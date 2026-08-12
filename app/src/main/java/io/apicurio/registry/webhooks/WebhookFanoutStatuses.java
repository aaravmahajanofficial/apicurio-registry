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

/**
 * Fanout status values for the {@code webhook_fanout} table.
 */
public final class WebhookFanoutStatuses {

    /** Fanout snapshot persisted; matching and enqueue not yet complete. */
    public static final String PENDING = "PENDING";

    /** Fanout completed; matching subscriptions were enqueued to {@code webhook_deliveries}. */
    public static final String DONE = "DONE";

    /** Fanout failed; eligible for {@link WebhookFanoutReconciler} retry. */
    public static final String FAILED = "FAILED";

    private WebhookFanoutStatuses() {
    }
}
