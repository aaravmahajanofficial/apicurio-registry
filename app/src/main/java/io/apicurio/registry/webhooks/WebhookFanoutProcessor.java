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

import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;

import io.apicurio.registry.storage.dto.OutboxEvent;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlOutboxEvent;
import io.apicurio.registry.storage.impl.sql.SqlOutboxEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Observes registry storage outbox CDI events and hands off fanout after the storage transaction
 * commits.
 * <p>
 * Registry SQL storage uses JDBI {@code HandleFactory} transactions rather than JTA, so this
 * component achieves post-commit semantics by observing synchronously and delegating fanout work to
 * a {@link ManagedExecutor} thread after the handle callback returns and commits.
 * <p>
 * This is an independent observer of the same CDI events as
 * {@link io.apicurio.registry.storage.impl.sql.SqlEventsProcessor} (Debezium path). It does not read
 * the ephemeral {@code outbox} table.
 */
@ApplicationScoped
public class WebhookFanoutProcessor {

    @Inject
    Logger log;

    @Inject
    WebhooksConfig webhooksConfig;

    @Inject
    WebhookFanoutService fanoutService;

    @Inject
    ManagedExecutor managedExecutor;

    /**
     * Handles SQL storage outbox events fired from PostgreSQL-backed repositories.
     *
     * @param event the CDI wrapper around the in-memory {@link OutboxEvent}
     */
    public void onSqlOutboxEvent(@Observes SqlOutboxEvent event) {
        handoff(event.getOutboxEvent());
    }

    /**
     * Handles KafkaSQL storage outbox events when the SQL snapshot supports webhooks.
     *
     * @param event the CDI wrapper around the in-memory {@link OutboxEvent}
     */
    public void onKafkaSqlOutboxEvent(@Observes KafkaSqlOutboxEvent event) {
        handoff(event.getOutboxEvent());
    }

    /**
     * Schedules fanout on a worker thread when webhooks are operational and the event is supported.
     *
     * @param outboxEvent the in-memory outbox payload from the storage layer
     */
    private void handoff(OutboxEvent outboxEvent) {
        if (!webhooksConfig.isOperational()) {
            return;
        }
        if (!WebhookStorageEventTypes.isSupported(outboxEvent.getType())) {
            return;
        }
        String outboxEventId = outboxEvent.getId();
        String storageEventType = outboxEvent.getType();
        String sourcePayload = outboxEvent.getPayload().toString();
        managedExecutor.runAsync(() -> {
            try {
                //  Offloads execution to a background worker thread via ManagedExecutor.runAsync().
                //  This ensures the database handle from the artifact write has committed before fanout starts.
                fanoutService.fanoutFromOutboxEvent(outboxEventId, storageEventType, sourcePayload);
            } catch (Exception ex) {
                log.error("Webhook fanout handoff failed for outboxEventId={}", outboxEventId, ex);
            }
        });
    }
}
