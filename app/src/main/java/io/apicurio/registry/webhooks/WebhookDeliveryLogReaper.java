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

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

/**
 * Periodically purges webhook delivery audit log rows older than the configured retention period.
 */
@ApplicationScoped
public class WebhookDeliveryLogReaper {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    WebhooksConfig webhooksConfig;

    /**
     * Deletes delivery log entries older than {@link WebhooksConfig#getLogRetention()}.
     */
    @Scheduled(delay = 5, concurrentExecution = SKIP, every = "{apicurio.webhooks.log.reaper.every:6h}")
    void run() {
        if (!webhooksConfig.isOperational()) {
            return;
        }
        try {
            if (storage.isReady() && !storage.isReadOnly()) {
                log.debug("Running webhook delivery log reaper at {}", Instant.now());
                reap();
            }
        } catch (Exception ex) {
            log.error("Exception thrown when running webhook delivery log reaper", ex);
        }
    }

    void reap() {
        long retentionMs = WebhookDeliveryBackoff.parseDuration(webhooksConfig.getLogRetention()).toMillis();
        long cutoff = System.currentTimeMillis() - retentionMs;
        storage.deleteOldWebhookDeliveryLogs(cutoff);
    }
}
