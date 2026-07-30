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

import org.slf4j.Logger;

import java.util.Date;
import java.util.List;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.WebhookDeliveryDto;
import io.apicurio.registry.storage.dto.WebhookFanoutDto;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Executes webhook fanout: maps storage snapshots to CloudEvents and enqueues matching deliveries.
 * <p>
 * Invoked by {@link WebhookFanoutProcessor} after artifact commits and by
 * {@link WebhookFanoutReconciler} for {@code PENDING}/{@code FAILED} rows in {@code webhook_fanout}.
 */
@ApplicationScoped
public class WebhookFanoutService {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    CloudEventsMapper cloudEventsMapper;

    @Inject
    WebhookSubscriptionMatcher subscriptionMatcher;

    /**
     * Persists a fanout snapshot for a freshly observed outbox event and executes fanout.
     *
     * @param outboxEventId     the outbox event id from the CDI {@code SqlOutboxEvent}
     * @param storageEventType  the {@link io.apicurio.registry.storage.StorageEventType} name
     * @param sourcePayloadJson JSON payload snapshot from the outbox event
     */
    public void fanoutFromOutboxEvent(String outboxEventId, String storageEventType,
                                      String sourcePayloadJson) {
        // Persists a snapshot of the raw event into the webhook_fanout database table with status PENDING.
        // If the server crashes during fanout, WebhookFanoutReconciler can replay this row later.
        WebhookFanoutDto fanout = WebhookFanoutDto.builder()
                .outboxEventId(outboxEventId)
                .sourcePayload(sourcePayloadJson)
                .storageEventType(storageEventType)
                .fanoutStatus(WebhookFanoutStatuses.PENDING)
                .fanoutAttempts(0)
                .build();
        storage.insertWebhookFanout(fanout);
        executeFanout(fanout);
    }

    /**
     * Replays fanout from an existing {@code webhook_fanout} row (reconciler path).
     *
     * @param fanout the persisted fanout row with {@code sourcePayload}
     */
    public void replayFanout(WebhookFanoutDto fanout) {
        executeFanout(fanout);
    }

    /**
     * Maps the fanout snapshot, matches subscriptions, enqueues deliveries, and updates fanout status.
     *
     * @param fanout the fanout row to process
     */
    private void executeFanout(WebhookFanoutDto fanout) {
        try {
            List<MappedCloudEvent> mappedEvents = cloudEventsMapper.mapFromStorageSnapshot(
                    fanout.getStorageEventType(), fanout.getSourcePayload());
            if (mappedEvents.isEmpty()) {
                markDone(fanout, null);
                return;
            }

            List<WebhookSubscriptionDto> subscriptions = storage.getEnabledWebhookSubscriptions();
            for (MappedCloudEvent mappedEvent : mappedEvents) {
                enqueueMatchingDeliveries(fanout.getSourcePayload(), mappedEvent, subscriptions);
            }
            markDone(fanout, null);
        } catch (Exception ex) {
            log.error("Webhook fanout failed for outboxEventId={}", fanout.getOutboxEventId(), ex);
            markFailed(fanout, safeErrorMessage(ex));
        }
    }

    /**
     * Enqueues {@code PENDING} delivery rows for subscriptions matching a mapped CloudEvent.
     *
     * @param sourcePayloadJson original outbox JSON for filter evaluation
     * @param mappedEvent       mapped CloudEvents envelope
     * @param subscriptions     enabled subscriptions to evaluate
     */
    private void enqueueMatchingDeliveries(String sourcePayloadJson, MappedCloudEvent mappedEvent,
                                           List<WebhookSubscriptionDto> subscriptions) {
        for (WebhookSubscriptionDto subscription : subscriptions) {
            if (!subscriptionMatcher.matches(subscription, mappedEvent, sourcePayloadJson)) {
                continue;
            }
            // Inserts a row into the webhook_deliveries PostgreSQL table with status PENDING.
            WebhookDeliveryDto delivery = WebhookDeliveryDto.builder()
                    .subscriptionId(subscription.getSubscriptionId())
                    .cloudEventId(mappedEvent.cloudEventId())
                    .eventType(mappedEvent.eventType())
                    .payload(mappedEvent.payload())
                    .status(WebhookDeliveryStatuses.PENDING)
                    .attemptCount(0)
                    .build();
            storage.insertWebhookDelivery(delivery);
        }
    }

    /**
     * Marks a fanout row as successfully completed.
     *
     * @param fanout    the fanout row to update
     * @param lastError optional error text (unused on success; {@code null} clears errors)
     */
    private void markDone(WebhookFanoutDto fanout, String lastError) {
        fanout.setFanoutStatus(WebhookFanoutStatuses.DONE);
        fanout.setLastError(lastError);
        fanout.setFanoutOn(new Date());
        storage.updateWebhookFanoutStatus(fanout);
    }

    /**
     * Marks a fanout row as failed and increments the attempt counter.
     *
     * @param fanout    the fanout row to update
     * @param lastError safe error summary for operators
     */
    private void markFailed(WebhookFanoutDto fanout, String lastError) {
        fanout.setFanoutStatus(WebhookFanoutStatuses.FAILED);
        fanout.setFanoutAttempts(fanout.getFanoutAttempts() + 1);
        fanout.setLastError(lastError);
        fanout.setFanoutOn(new Date());
        storage.updateWebhookFanoutStatus(fanout);
    }

    /**
     * @param ex the fanout failure
     * @return a short error message without stack traces for {@code webhook_fanout.lastError}
     */
    private static String safeErrorMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Webhook fanout failed";
        }
        return message.length() > 512 ? message.substring(0, 512) : message;
    }
}
