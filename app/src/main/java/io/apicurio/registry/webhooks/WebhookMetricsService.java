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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.atomic.AtomicLong;

import static io.apicurio.registry.metrics.MetricsConstants.WEBHOOK_DELIVERY_DURATION;
import static io.apicurio.registry.metrics.MetricsConstants.WEBHOOK_DELIVERY_DURATION_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.WEBHOOK_DELIVERY_TOTAL;
import static io.apicurio.registry.metrics.MetricsConstants.WEBHOOK_DELIVERY_TOTAL_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.WEBHOOK_QUEUE_DEPTH;
import static io.apicurio.registry.metrics.MetricsConstants.WEBHOOK_QUEUE_DEPTH_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.WEBHOOK_RETRY_TOTAL;
import static io.apicurio.registry.metrics.MetricsConstants.WEBHOOK_RETRY_TOTAL_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.WEBHOOK_SUBSCRIPTION_AUTO_DISABLED;
import static io.apicurio.registry.metrics.MetricsConstants.WEBHOOK_SUBSCRIPTION_AUTO_DISABLED_DESCRIPTION;
import static io.apicurio.registry.metrics.MetricsConstants.WEBHOOK_TAG_STATUS;

/**
 * Micrometer metrics for webhook delivery operations.
 */
@ApplicationScoped
public class WebhookMetricsService {

    @Inject
    MeterRegistry registry;

    private final AtomicLong queueDepth = new AtomicLong(0);

    private Timer deliveryDurationTimer;
    private Counter retryCounter;
    private Counter autoDisabledCounter;

    @PostConstruct
    void init() {
        if (registry == null) {
            return;
        }
        deliveryDurationTimer = Timer.builder(WEBHOOK_DELIVERY_DURATION)
                .description(WEBHOOK_DELIVERY_DURATION_DESCRIPTION)
                .register(registry);
        retryCounter = Counter.builder(WEBHOOK_RETRY_TOTAL)
                .description(WEBHOOK_RETRY_TOTAL_DESCRIPTION)
                .register(registry);
        autoDisabledCounter = Counter.builder(WEBHOOK_SUBSCRIPTION_AUTO_DISABLED)
                .description(WEBHOOK_SUBSCRIPTION_AUTO_DISABLED_DESCRIPTION)
                .register(registry);
        Gauge.builder(WEBHOOK_QUEUE_DEPTH, queueDepth, AtomicLong::get)
                .description(WEBHOOK_QUEUE_DEPTH_DESCRIPTION)
                .register(registry);
    }

    /**
     * Records a completed delivery attempt with its terminal or retry status.
     *
     * @param status delivery status after the attempt (e.g. {@code DELIVERED}, {@code DEAD_LETTER})
     * @param durationMs HTTP attempt duration in milliseconds
     * @param attemptNumber 1-based attempt number for this delivery
     */
    public void recordDeliveryAttempt(String status, long durationMs, int attemptNumber) {
        if (registry == null) {
            return;
        }
        Counter.builder(WEBHOOK_DELIVERY_TOTAL)
                .description(WEBHOOK_DELIVERY_TOTAL_DESCRIPTION)
                .tag(WEBHOOK_TAG_STATUS, status)
                .register(registry)
                .increment();
        if (deliveryDurationTimer != null) {
            deliveryDurationTimer.record(java.time.Duration.ofMillis(durationMs));
        }
        if (attemptNumber > 1 && retryCounter != null) {
            retryCounter.increment();
        }
    }

    /**
     * Updates the queue depth gauge from storage.
     *
     * @param depth number of {@code PENDING} or {@code IN_PROGRESS} deliveries
     */
    public void updateQueueDepth(long depth) {
        queueDepth.set(depth);
    }

    /**
     * Records that a subscription was auto-disabled after consecutive failures.
     */
    public void recordSubscriptionAutoDisabled() {
        if (autoDisabledCounter != null) {
            autoDisabledCounter.increment();
        }
    }
}
