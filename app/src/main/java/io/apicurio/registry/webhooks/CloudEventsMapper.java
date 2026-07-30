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

import org.json.JSONObject;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.types.VersionState;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Translates registry storage outbox payloads into CloudEvents v1.0 structured JSON envelopes.
 * <p>
 * Outbox-originated events use a deterministic CloudEvents {@code id} derived from the outbox event
 * id so fanout retries and reconciler replays remain idempotent on
 * {@code (subscriptionId, cloudEventId)}.
 */
@ApplicationScoped
public class CloudEventsMapper {

    static final String CLOUD_EVENT_SOURCE = "/apis/registry/v3";
    static final String RULE_VERSION_EXTENSION = "apicurioruleversion";
    static final String RULE_VERSION_VALUE = "1";
    static final String CLOUD_EVENT_ID_PREFIX = "io.apicurio.registry.webhook:";

    @Inject
    WebhooksConfig webhooksConfig;

    /**
     * Maps a persisted storage outbox snapshot to zero or more webhook CloudEvents.
     *
     * @param storageEventType  the {@link StorageEventType#name()} stored in {@code webhook_fanout}
     * @param sourcePayloadJson the JSON payload snapshot from the outbox CDI event
     * @return mapped envelopes; empty when the storage event is not webhook-relevant
     */
    public List<MappedCloudEvent> mapFromStorageSnapshot(String storageEventType, String sourcePayloadJson) {
        if (!WebhookStorageEventTypes.isSupported(storageEventType)) {
            return List.of();
        }
        JSONObject source = new JSONObject(sourcePayloadJson);
        StorageEventType type = StorageEventType.valueOf(storageEventType);
        String outboxEventId = source.optString("id", null);
        if (outboxEventId == null || outboxEventId.isBlank()) {
            return List.of();
        }

        List<WebhookEventMapping> mappings = resolveMappings(type, source);
        List<MappedCloudEvent> results = new ArrayList<>(mappings.size());
        for (WebhookEventMapping mapping : mappings) {
            JSONObject data = buildDataObject(source, mapping.dataFields());
            String subject = buildSubject(source, mapping.includeVersionInSubject());
            String cloudEventId = deterministicCloudEventId(outboxEventId, mapping.eventType());
            String payload = serialize(buildCloudEvent(cloudEventId, mapping.eventType(), subject, data));
            results.add(new MappedCloudEvent(cloudEventId, mapping.eventType(), subject, payload));
        }
        return results;
    }

    /**
     * Builds a {@link WebhookEventTypes#RULE_VIOLATED} envelope for a rejected write (no outbox row).
     * <p>
     * Uses a caller-supplied UUID v4 {@code cloudEventId} because rule violations are not derived from
     * outbox events.
     *
     * @param cloudEventId unique event id (UUID v4)
     * @param subject      optional {@code groupId/artifactId} subject
     * @param data         violation payload built by {@link RuleViolationEmitter}
     * @return mapped envelope ready for delivery enqueue
     */
    public MappedCloudEvent mapRuleViolation(String cloudEventId, String subject, JSONObject data) {
        String payload = serialize(
                buildCloudEvent(cloudEventId, WebhookEventTypes.RULE_VIOLATED, subject, data));
        return new MappedCloudEvent(cloudEventId, WebhookEventTypes.RULE_VIOLATED, subject, payload);
    }

    private List<WebhookEventMapping> resolveMappings(StorageEventType type, JSONObject source) {
        switch (type) {
            case ARTIFACT_CREATED:
                return List.of(mapping(WebhookEventTypes.ARTIFACT_CREATED,
                        "groupId", "artifactId", "name", "description"));
            case ARTIFACT_METADATA_UPDATED:
                return List.of(mapping(WebhookEventTypes.ARTIFACT_UPDATED,
                        "groupId", "artifactId", "name", "description", "owner"));
            case ARTIFACT_DELETED:
                return List.of(mapping(WebhookEventTypes.ARTIFACT_DELETED,
                        "groupId", "artifactId"));
            case ARTIFACT_VERSION_CREATED:
                return List.of(mapping(WebhookEventTypes.VERSION_PUBLISHED, true,
                        "groupId", "artifactId", "version", "name", "description"));
            case ARTIFACT_VERSION_STATE_CHANGED:
                return mapVersionStateChanged(source);
            default:
                return List.of();
        }
    }

    private List<WebhookEventMapping> mapVersionStateChanged(JSONObject source) {
        String newState = source.optString("newState", null);
        if (newState == null) {
            return List.of();
        }
        String normalized = newState.toUpperCase(Locale.ROOT);
        if (VersionState.ENABLED.name().equals(normalized)) {
            return List.of(mapping(WebhookEventTypes.VERSION_PUBLISHED, true,
                    "groupId", "artifactId", "version", "oldState", "newState"));
        }
        if (VersionState.DEPRECATED.name().equals(normalized)) {
            return List.of(mapping(WebhookEventTypes.VERSION_DEPRECATED, true,
                    "groupId", "artifactId", "version", "oldState", "newState"));
        }
        if (VersionState.DISABLED.name().equals(normalized) || VersionState.SUNSET.name().equals(normalized)) {
            return List.of(mapping(WebhookEventTypes.VERSION_STATE_CHANGED, true,
                    "groupId", "artifactId", "version", "oldState", "newState"));
        }
        return List.of();
    }

    private static WebhookEventMapping mapping(String eventType, String... dataFields) {
        return new WebhookEventMapping(eventType, false, dataFields);
    }

    private static WebhookEventMapping mapping(String eventType, boolean includeVersionInSubject,
                                               String... dataFields) {
        return new WebhookEventMapping(eventType, includeVersionInSubject, dataFields);
    }

    private JSONObject buildDataObject(JSONObject source, String[] fields) {
        JSONObject data = new JSONObject();
        for (String field : fields) {
            if (source.has(field) && !source.isNull(field)) {
                data.put(field, source.get(field));
            }
        }
        return data;
    }

    private String buildSubject(JSONObject source, boolean includeVersion) {
        String groupId = source.optString("groupId", null);
        String artifactId = source.optString("artifactId", null);
        if (groupId == null || artifactId == null) {
            return null;
        }
        if (!includeVersion) {
            return groupId + "/" + artifactId;
        }
        String version = source.optString("version", null);
        if (version == null) {
            return groupId + "/" + artifactId;
        }
        return groupId + "/" + artifactId + "/" + version;
    }

    /**
     * Derives a stable CloudEvents {@code id} from the outbox event id and webhook event type.
     *
     * @param outboxEventId the outbox event id from the storage snapshot
     * @param eventType     the target CloudEvents type string
     * @return deterministic UUID string for deduplication
     */
    public static String deterministicCloudEventId(String outboxEventId, String eventType) {
        String material = CLOUD_EVENT_ID_PREFIX + outboxEventId + ":" + eventType;
        return UUID.nameUUIDFromBytes(material.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private CloudEvent buildCloudEvent(String cloudEventId, String eventType, String subject,
                                       JSONObject data) {
        byte[] dataBytes = truncateIfNeeded(data.toString());
        CloudEventBuilder builder = CloudEventBuilder.v1()
                .withId(cloudEventId)
                .withSource(URI.create(CLOUD_EVENT_SOURCE))
                .withType(eventType)
                .withTime(OffsetDateTime.now())
                .withDataContentType("application/json")
                .withExtension(RULE_VERSION_EXTENSION, RULE_VERSION_VALUE)
                .withData(dataBytes);
        if (subject != null) {
            builder.withSubject(subject);
        }
        return builder.build();
    }

    private byte[] truncateIfNeeded(String json) {
        int maxBytes = webhooksConfig.getPayloadMaxBytes();
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return bytes;
        }
        return new String(bytes, 0, maxBytes, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
    }

    private String serialize(CloudEvent cloudEvent) {
        EventFormat format = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);
        if (format == null) {
            throw new IllegalStateException("CloudEvents JSON format not available");
        }
        return new String(format.serialize(cloudEvent), StandardCharsets.UTF_8);
    }

    private record WebhookEventMapping(String eventType, boolean includeVersionInSubject,
                                       String[] dataFields) {
    }
}
