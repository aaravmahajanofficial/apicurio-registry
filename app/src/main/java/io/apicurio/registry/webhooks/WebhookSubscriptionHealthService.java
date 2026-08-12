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
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;

/**
 * Tracks consecutive webhook delivery failures and auto-disables unhealthy subscriptions.
 */
@ApplicationScoped
public class WebhookSubscriptionHealthService {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    WebhooksConfig webhooksConfig;

    @Inject
    WebhookMetricsService metrics;

    /**
     * Resets the consecutive failure counter after a successful delivery.
     *
     * @param subscriptionId the subscription that received a delivery
     */
    public void onDeliverySucceeded(String subscriptionId) {
        WebhookSubscriptionDto subscription = storage.getWebhookSubscription(subscriptionId);
        if (subscription.getConsecutiveDeliveryFailures() <= 0) {
            return;
        }
        subscription.setConsecutiveDeliveryFailures(0);
        storage.updateWebhookSubscription(subscription);
    }

    /**
     * Increments consecutive failures when a delivery exhausts retries and may auto-disable the
     * subscription when the configured threshold is reached.
     *
     * @param subscriptionId the subscription whose delivery reached dead-letter status
     */
    public void onDeliveryDeadLetter(String subscriptionId) {
        WebhookSubscriptionDto subscription = storage.getWebhookSubscription(subscriptionId);
        int failures = subscription.getConsecutiveDeliveryFailures() + 1;
        subscription.setConsecutiveDeliveryFailures(failures);
        if (failures >= webhooksConfig.getDeliveryAutoDisableThreshold()) {
            subscription.setEnabled(false);
            log.warn("Auto-disabled webhook subscription {} after {} consecutive delivery failures",
                    subscriptionId, failures);
            metrics.recordSubscriptionAutoDisabled();
        }
        storage.updateWebhookSubscription(subscription);
    }
}
