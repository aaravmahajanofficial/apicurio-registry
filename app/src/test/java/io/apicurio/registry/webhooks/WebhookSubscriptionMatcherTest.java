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

import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link WebhookSubscriptionMatcher}.
 */
class WebhookSubscriptionMatcherTest {

    private final WebhookSubscriptionMatcher matcher = new WebhookSubscriptionMatcher();

    @Test
    void matchesEventTypeAndGroupFilter() {
        WebhookSubscriptionDto subscription = WebhookSubscriptionDto.builder()
                .subscriptionId("sub-1")
                .enabled(true)
                .eventTypes(List.of(WebhookEventTypes.ARTIFACT_CREATED))
                .groupIdFilter("prod")
                .build();
        MappedCloudEvent event = new MappedCloudEvent("id", WebhookEventTypes.ARTIFACT_CREATED,
                "prod/orders", "{}");
        String source = new JSONObject().put("groupId", "prod").toString();
        assertTrue(matcher.matches(subscription, event, source));
    }

    @Test
    void rejectsWrongGroupFilter() {
        WebhookSubscriptionDto subscription = WebhookSubscriptionDto.builder()
                .subscriptionId("sub-1")
                .enabled(true)
                .eventTypes(List.of(WebhookEventTypes.ARTIFACT_CREATED))
                .groupIdFilter("prod")
                .build();
        MappedCloudEvent event = new MappedCloudEvent("id", WebhookEventTypes.ARTIFACT_CREATED,
                "dev/orders", "{}");
        String source = new JSONObject().put("groupId", "dev").toString();
        assertFalse(matcher.matches(subscription, event, source));
    }
}
