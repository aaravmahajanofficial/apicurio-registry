/*
 * Copyright 2026 Red Hat Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.registry.webhooks;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.WebhookDeliveryDto;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * Polls {@code webhook_deliveries} for {@code PENDING} rows, claims batches with
 * {@code FOR UPDATE SKIP LOCKED}, and dispatches HTTP deliveries concurrently.
 */
@ApplicationScoped
public class WebhookDeliveryWorker {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    WebhooksConfig webhooksConfig;

    @Inject
    WebhookDeliveryService deliveryService;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final AtomicInteger globalInFlight = new AtomicInteger(0);
    private final Map<String, AtomicInteger> inflightBySubscription = new ConcurrentHashMap<>();
    private final Map<Long, WebhookDeliveryDto> activeDeliveries = new ConcurrentHashMap<>();

    /**
     * Claims and dispatches pending webhook deliveries on the configured poll interval.
     */
    @Scheduled(delay = 5, concurrentExecution = SKIP, every = "{apicurio.webhooks.delivery.poll-every:2s}")
    void poll() {
        if (!webhooksConfig.isOperational() || shuttingDown.get()) {
            return;
        }
        try {
            if (!storage.isReady() || storage.isReadOnly()) {
                return;
            }
            int batchSize = webhooksConfig.getDeliveryBatchSize();
            int maxConcurrency = webhooksConfig.getDeliveryConcurrency();
            int maxPerSubscription = webhooksConfig.getDeliveryMaxInflightPerSubscription();

            List<WebhookDeliveryDto> claimed = storage.claimWebhookDeliveries(batchSize);
            if (claimed.isEmpty()) {
                return;
            }
            log.debug("Claimed {} webhook delivery row(s) at {}", claimed.size(), Instant.now());

            List<WebhookDeliveryDto> toDispatch = new ArrayList<>();
            for (WebhookDeliveryDto delivery : claimed) {
                if (globalInFlight.get() >= maxConcurrency) {
                    deliveryService.releaseToPending(delivery);
                    continue;
                }
                AtomicInteger subscriptionInflight = inflightBySubscription.computeIfAbsent(
                        delivery.getSubscriptionId(), id -> new AtomicInteger(0));
                if (subscriptionInflight.get() >= maxPerSubscription) {
                    deliveryService.releaseToPending(delivery);
                    continue;
                }
                subscriptionInflight.incrementAndGet();
                globalInFlight.incrementAndGet();
                activeDeliveries.put(delivery.getDeliveryId(), delivery);
                toDispatch.add(delivery);
            }

            for (WebhookDeliveryDto delivery : toDispatch) {
                if (shuttingDown.get()) {
                    releaseActiveDelivery(delivery);
                    deliveryService.releaseToPending(delivery);
                    continue;
                }
                deliveryService.deliver(delivery, () -> releaseActiveDelivery(delivery));
            }
        } catch (Exception ex) {
            log.error("Webhook delivery worker poll failed", ex);
        }
    }

    /**
     * Stops claiming new deliveries and drains in-flight HTTP requests during shutdown.
     *
     * @param event Quarkus shutdown event
     */
    void onShutdown(@Observes ShutdownEvent event) {
        shuttingDown.set(true);
        long deadlineMs = System.currentTimeMillis()
                + WebhookDeliveryBackoff.parseDuration(webhooksConfig.getDeliveryShutdownTimeout()).toMillis();
        while (globalInFlight.get() > 0 && System.currentTimeMillis() < deadlineMs) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        List<WebhookDeliveryDto> remaining = new ArrayList<>(activeDeliveries.values());
        for (WebhookDeliveryDto delivery : remaining) {
            try {
                deliveryService.releaseToPending(delivery);
            } catch (Exception ex) {
                log.warn("Failed to release webhook delivery {} on shutdown", delivery.getDeliveryId(), ex);
            }
        }
        activeDeliveries.clear();
        inflightBySubscription.clear();
        globalInFlight.set(0);
        if (!remaining.isEmpty()) {
            log.info("Webhook delivery worker shutdown released {} in-flight delivery row(s) to PENDING",
                    remaining.size());
        }
    }

    private void releaseActiveDelivery(WebhookDeliveryDto delivery) {
        activeDeliveries.remove(delivery.getDeliveryId());
        globalInFlight.decrementAndGet();
        inflightBySubscription.computeIfPresent(delivery.getSubscriptionId(), (id, count) -> {
            count.decrementAndGet();
            return count;
        });
    }
}
