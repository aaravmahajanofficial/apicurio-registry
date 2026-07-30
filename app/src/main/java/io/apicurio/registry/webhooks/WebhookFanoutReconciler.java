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
import io.apicurio.registry.storage.dto.WebhookFanoutDto;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.List;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * Replays webhook fanout from durable {@code webhook_fanout} rows in {@code PENDING} or
 * {@code FAILED} status.
 * <p>
 * The reconciler never reads the ephemeral Debezium {@code outbox} table; it replays from
 * {@code webhook_fanout.sourcePayload} snapshots written by {@link WebhookFanoutProcessor}.
 */
@ApplicationScoped
public class WebhookFanoutReconciler {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    WebhooksConfig webhooksConfig;

    @Inject
    WebhookFanoutService fanoutService;

    /**
     * Polls for pending fanout rows and retries fanout from persisted payload snapshots.
     */
    @Scheduled(delay = 5, concurrentExecution = SKIP, every = "{apicurio.webhooks.fanout.reconcile-every}")
    void reconcile() {
        if (!webhooksConfig.isOperational()) {
            return;
        }
        try {
            if (!storage.isReady() || storage.isReadOnly()) {
                return;
            }
            List<WebhookFanoutDto> pending = storage.getPendingWebhookFanouts(
                    webhooksConfig.getFanoutMaxAttempts(),
                    webhooksConfig.getFanoutReconcileBatchSize());
            if (pending.isEmpty()) {
                return;
            }
            log.debug("Reconciling {} webhook fanout row(s) at {}", pending.size(), Instant.now());
            for (WebhookFanoutDto fanout : pending) {
                try {
                    fanoutService.replayFanout(fanout);
                } catch (Exception ex) {
                    log.error("Webhook fanout reconciliation failed for outboxEventId={}",
                            fanout.getOutboxEventId(), ex);
                }
            }
        } catch (Exception ex) {
            log.error("Webhook fanout reconciler failed", ex);
        }
    }
}
