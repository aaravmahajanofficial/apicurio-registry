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
 * Delivery queue status values for the {@code webhook_deliveries} table.
 */
public final class WebhookDeliveryStatuses {

    /** Awaiting pickup by the webhook delivery worker. */
    public static final String PENDING = "PENDING";

    /** Claimed by a worker and currently being delivered. */
    public static final String IN_PROGRESS = "IN_PROGRESS";

    /** Successfully delivered to the subscriber endpoint. */
    public static final String DELIVERED = "DELIVERED";

    /** Exhausted retry attempts; no further automatic delivery. */
    public static final String DEAD_LETTER = "DEAD_LETTER";

    private WebhookDeliveryStatuses() {
    }
}
