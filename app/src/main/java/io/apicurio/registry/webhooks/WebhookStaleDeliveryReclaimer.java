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
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.Date;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * Reclaims stale {@code IN_PROGRESS} webhook deliveries using {@code FOR UPDATE SKIP LOCKED}.
 * <p>
 * Reclaim resets rows to {@code PENDING} without incrementing {@code attemptCount}.
 */
@ApplicationScoped
public class WebhookStaleDeliveryReclaimer {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    WebhooksConfig webhooksConfig;

    /**
     * Reclaims deliveries stuck in {@code IN_PROGRESS} longer than {@code in-progress-timeout}.
     */
    @Scheduled(delay = 10, concurrentExecution = SKIP, every = "{apicurio.webhooks.delivery.poll-every:2s}")
    void reclaim() {
        if (!webhooksConfig.isOperational()) {
            return;
        }
        try {
            if (!storage.isReady() || storage.isReadOnly()) {
                return;
            }
            long timeoutMs = WebhookDeliveryBackoff.parseDurationMs(
                    webhooksConfig.getDeliveryInProgressTimeout(), 300_000L);
            Date staleBefore = new Date(System.currentTimeMillis() - timeoutMs);
            int reclaimed = storage.reclaimStaleWebhookDeliveries(staleBefore,
                    webhooksConfig.getDeliveryBatchSize());
            if (reclaimed > 0) {
                log.debug("Reclaimed {} stale webhook delivery row(s) at {}", reclaimed, Instant.now());
            }
        } catch (Exception ex) {
            log.error("Webhook stale delivery reclaimer failed", ex);
        }
    }
}
