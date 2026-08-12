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
import jakarta.enterprise.context.ApplicationScoped;
import org.json.JSONObject;

/**
 * Filters webhook subscriptions against a mapped CloudEvent and optional source payload fields.
 */
@ApplicationScoped
public class WebhookSubscriptionMatcher {

    /**
     * Determines whether a subscription should receive a mapped CloudEvent.
     *
     * @param subscription the enabled subscription candidate
     * @param mappedCloudEvent the mapped envelope produced by {@link CloudEventsMapper}
     * @param sourcePayloadJson original outbox JSON (for group and artifact-type filters)
     * @return {@code true} when all subscription filters match
     */
    public boolean matches(WebhookSubscriptionDto subscription, MappedCloudEvent mappedCloudEvent,
            String sourcePayloadJson) {
        if (subscription == null || !subscription.isEnabled()) {
            return false;
        }
        if (subscription.getEventTypes() == null
                || !subscription.getEventTypes().contains(mappedCloudEvent.eventType())) {
            return false;
        }
        JSONObject source = new JSONObject(sourcePayloadJson);
        if (!matchesGroupFilter(subscription.getGroupIdFilter(), source)) {
            return false;
        }
        return matchesArtifactTypeFilter(subscription.getArtifactTypeFilter(), source);
    }

    /**
     * @param groupIdFilter optional subscription group filter (exact match)
     * @param source outbox payload containing {@code groupId}
     * @return {@code true} when the filter is unset or matches the payload group id
     */
    private boolean matchesGroupFilter(String groupIdFilter, JSONObject source) {
        if (groupIdFilter == null || groupIdFilter.isBlank()) {
            return true;
        }
        String groupId = source.optString("groupId", null);
        return groupId != null && groupIdFilter.equals(groupId);
    }

    /**
     * @param artifactTypeFilter optional subscription artifact-type filter (exact match)
     * @param source outbox payload that may contain {@code artifactType}
     * @return {@code true} when the filter is unset, or the payload type matches, or type is absent
     */
    private boolean matchesArtifactTypeFilter(String artifactTypeFilter, JSONObject source) {
        if (artifactTypeFilter == null || artifactTypeFilter.isBlank()) {
            return true;
        }
        if (!source.has("artifactType") || source.isNull("artifactType")) {
            return true;
        }
        return artifactTypeFilter.equals(source.optString("artifactType", null));
    }
}
