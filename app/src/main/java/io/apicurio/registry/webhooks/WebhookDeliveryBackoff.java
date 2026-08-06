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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Computes exponential backoff delays for failed webhook deliveries.
 * <p>
 * Formula: {@code baseDelay = min(initial × multiplier^attempt, max)} plus additive jitter in
 * {@code [0, baseDelay × 0.25]}.
 */
@ApplicationScoped
public class WebhookDeliveryBackoff {

    @Inject
    WebhooksConfig webhooksConfig;

    /**
     * Computes the next attempt timestamp after a failed delivery.
     *
     * @param failedAttemptIndex 0-based index of the failed attempt (0 = first failure)
     * @return absolute time for the next delivery attempt
     */
    public Date computeNextAttemptOn(int failedAttemptIndex) {
        long baseDelayMs = computeBaseDelayMs(failedAttemptIndex);
        long jitterMs = computeJitterMs(baseDelayMs);
        return new Date(System.currentTimeMillis() + baseDelayMs + jitterMs);
    }

    /**
     * @param failedAttemptIndex 0-based failed attempt index
     * @return base delay in milliseconds before jitter
     */
    public long computeBaseDelayMs(int failedAttemptIndex) {
        long initialMs = parseDurationMs(webhooksConfig.getDeliveryInitialDelay(), 1000L);
        long maxMs = parseDurationMs(webhooksConfig.getDeliveryMaxDelay(), 300_000L);
        double multiplier = webhooksConfig.getDeliveryBackoffMultiplier();
        double scaled = initialMs * Math.pow(multiplier, failedAttemptIndex);
        long baseDelay = (long) Math.min(scaled, maxMs);
        return Math.max(baseDelay, 0L);
    }

    /**
     * @param baseDelayMs the base delay before jitter
     * @return additive jitter in {@code [0, baseDelayMs × 0.25]}
     */
    public long computeJitterMs(long baseDelayMs) {
        if (baseDelayMs <= 0) {
            return 0L;
        }
        long jitterCap = (long) (baseDelayMs * 0.25);
        if (jitterCap <= 0) {
            return 0L;
        }
        return ThreadLocalRandom.current().nextLong(0, jitterCap + 1);
    }

    /**
     * Parses a duration configuration string into milliseconds.
     *
     * @param value configured duration (e.g. {@code 1s}, {@code 5m})
     * @param defaultMs fallback when parsing fails
     * @return duration in milliseconds
     */
    static long parseDurationMs(String value, long defaultMs) {
        if (value == null || value.isBlank()) {
            return defaultMs;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.endsWith("ms")) {
                return Long.parseLong(trimmed.substring(0, trimmed.length() - 2));
            }
            if (trimmed.endsWith("s")) {
                return Long.parseLong(trimmed.substring(0, trimmed.length() - 1)) * 1000L;
            }
            if (trimmed.endsWith("m")) {
                return Long.parseLong(trimmed.substring(0, trimmed.length() - 1)) * 60_000L;
            }
            if (trimmed.endsWith("h")) {
                return Long.parseLong(trimmed.substring(0, trimmed.length() - 1)) * 3_600_000L;
            }
            if (trimmed.endsWith("d")) {
                return Long.parseLong(trimmed.substring(0, trimmed.length() - 1)) * 86_400_000L;
            }
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            return defaultMs;
        }
    }

    /**
     * @param value configured shutdown or timeout duration
     * @return parsed duration, defaulting to 30 seconds
     */
    public static Duration parseDuration(String value) {
        return Duration.ofMillis(parseDurationMs(value, Duration.ofSeconds(30).toMillis()));
    }
}
