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
 * A CloudEvents envelope ready for webhook delivery enqueue.
 *
 * @param cloudEventId stable deduplication key ({@code webhook_deliveries.cloudEventId})
 * @param eventType CloudEvents {@code type} attribute (subscription filter key)
 * @param subject CloudEvents {@code subject} attribute
 * @param payload serialized structured-mode JSON ({@code application/cloudevents+json} body)
 */
public record MappedCloudEvent(String cloudEventId, String eventType, String subject, String payload) {
}
