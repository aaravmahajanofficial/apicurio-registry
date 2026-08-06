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
import io.apicurio.registry.storage.dto.WebhookDeliveryLogDto;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

/**
 * Executes a single webhook delivery attempt: resolves the signing secret, POSTs the CloudEvent,
 * records {@code webhook_delivery_log}, and updates delivery status.
 */
@ApplicationScoped
public class WebhookDeliveryService {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    WebhooksConfig webhooksConfig;

    @Inject
    WebhookHttpClient httpClient;

    @Inject
    WebhookSubscriptionSecretStore secretStore;

    @Inject
    WebhookDeliveryBackoff backoff;

    /**
     * Delivers a claimed webhook row and updates persistence on completion.
     * <p>
     * The caller is responsible for tracking in-flight concurrency and decrementing counters when
     * the returned future completes.
     *
     * @param delivery a delivery row in {@code IN_PROGRESS} status
     * @param onComplete invoked after persistence updates complete (success or failure)
     */
    public void deliver(WebhookDeliveryDto delivery, Runnable onComplete) {
        int attemptNumber = delivery.getAttemptCount() + 1;
        long startMs = System.currentTimeMillis();
        try {
            WebhookSubscriptionDto subscription = storage.getWebhookSubscription(delivery.getSubscriptionId());
            String secret = secretStore.getSigningSecret(delivery.getSubscriptionId());
            httpClient.post(subscription.getUrl(), secret, delivery.getPayload())
                    .onComplete(ar -> {
                        long durationMs = System.currentTimeMillis() - startMs;
                        if (ar.succeeded()) {
                            handleSuccess(delivery, attemptNumber, ar.result(), durationMs, onComplete);
                        } else {
                            handleFailure(delivery, attemptNumber, ar.cause(), durationMs, null, onComplete);
                        }
                    });
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startMs;
            handleFailure(delivery, attemptNumber, ex, durationMs, null, onComplete);
        }
    }

    /**
     * Marks a claimed delivery as {@code PENDING} without incrementing {@code attemptCount}.
     * <p>
     * Used when per-subscription in-flight caps or shutdown prevent dispatch in the current batch.
     *
     * @param delivery the delivery to release back to the queue
     */
    public void releaseToPending(WebhookDeliveryDto delivery) {
        delivery.setStatus(WebhookDeliveryStatuses.PENDING);
        storage.updateWebhookDelivery(delivery);
    }

    private void handleSuccess(WebhookDeliveryDto delivery, int attemptNumber,
            WebhookHttpClient.WebhookHttpResponse response, long durationMs, Runnable onComplete) {
        delivery.setAttemptCount(attemptNumber);
        delivery.setStatus(WebhookDeliveryStatuses.DELIVERED);
        delivery.setLastError(null);
        delivery.setNextAttemptOn(new java.util.Date());
        try {
            storage.updateWebhookDelivery(delivery);
            storage.insertWebhookDeliveryLog(WebhookDeliveryLogDto.builder()
                    .deliveryId(delivery.getDeliveryId())
                    .subscriptionId(delivery.getSubscriptionId())
                    .cloudEventId(delivery.getCloudEventId())
                    .attemptNumber(attemptNumber)
                    .httpStatus(response.statusCode())
                    .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                    .attemptedOn(new java.util.Date())
                    .build());
        } catch (Exception ex) {
            log.error("Failed to persist successful webhook delivery deliveryId={}",
                    delivery.getDeliveryId(), ex);
        } finally {
            onComplete.run();
        }
    }

    private void handleFailure(WebhookDeliveryDto delivery, int attemptNumber, Throwable cause,
            long durationMs, Integer httpStatus, Runnable onComplete) {
        delivery.setAttemptCount(attemptNumber);
        String error = truncateError(safeErrorMessage(cause));
        delivery.setLastError(error);
        if (attemptNumber >= webhooksConfig.getDeliveryMaxAttempts()) {
            delivery.setStatus(WebhookDeliveryStatuses.DEAD_LETTER);
        } else {
            delivery.setStatus(WebhookDeliveryStatuses.PENDING);
            delivery.setNextAttemptOn(backoff.computeNextAttemptOn(attemptNumber - 1));
        }
        try {
            storage.updateWebhookDelivery(delivery);
            storage.insertWebhookDeliveryLog(WebhookDeliveryLogDto.builder()
                    .deliveryId(delivery.getDeliveryId())
                    .subscriptionId(delivery.getSubscriptionId())
                    .cloudEventId(delivery.getCloudEventId())
                    .attemptNumber(attemptNumber)
                    .httpStatus(httpStatus)
                    .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                    .error(error)
                    .attemptedOn(new java.util.Date())
                    .build());
        } catch (Exception ex) {
            log.error("Failed to persist failed webhook delivery deliveryId={}",
                    delivery.getDeliveryId(), ex);
        } finally {
            onComplete.run();
        }
    }

    private static String safeErrorMessage(Throwable cause) {
        if (cause == null) {
            return "Webhook delivery failed";
        }
        if (cause instanceof WebhookHttpClient.WebhookDeliveryException) {
            return cause.getMessage();
        }
        if (cause instanceof WebhookSsrfException) {
            return "Webhook URL blocked by SSRF policy";
        }
        if (cause instanceof WebhookSecretCipher.WebhookSecretCipherException) {
            return "Webhook signing secret unavailable";
        }
        String message = cause.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return "Webhook delivery failed";
    }

    private static String truncateError(String error) {
        if (error == null) {
            return null;
        }
        if (error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }
}
