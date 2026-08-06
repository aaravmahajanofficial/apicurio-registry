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

import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.types.VersionState;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CloudEventsMapper} storage-event routing and CloudEvents envelope fields.
 */
class CloudEventsMapperTest {

    private final CloudEventsMapper mapper = new CloudEventsMapper();

    @BeforeEach
    void setUp() throws Exception {
        WebhooksConfig config = mock(WebhooksConfig.class);
        when(config.getPayloadMaxBytes()).thenReturn(262144);
        Field field = CloudEventsMapper.class.getDeclaredField("webhooksConfig");
        field.setAccessible(true);
        field.set(mapper, config);
    }

    @Test
    void mapsArtifactCreated() {
        String payload = new JSONObject()
                .put("id", "evt-artifact-created")
                .put("groupId", "prod")
                .put("artifactId", "orders")
                .put("name", "Orders")
                .put("description", "desc")
                .put("eventType", StorageEventType.ARTIFACT_CREATED.name())
                .toString();

        List<MappedCloudEvent> mapped = mapper.mapFromStorageSnapshot(
                StorageEventType.ARTIFACT_CREATED.name(), payload);
        assertEquals(1, mapped.size());
        MappedCloudEvent event = mapped.get(0);
        assertEquals(WebhookEventTypes.ARTIFACT_CREATED, event.eventType());
        assertEquals("prod/orders", event.subject());
        assertTrue(event.payload().contains("\"specversion\":\"1.0\""));
        assertTrue(event.payload().contains("\"type\":\"io.apicurio.registry.artifact.created.v1\""));
    }

    @Test
    void mapsVersionStateChangedToPublished() {
        String payload = new JSONObject()
                .put("id", "evt-state")
                .put("groupId", "g")
                .put("artifactId", "a")
                .put("version", "1.0")
                .put("oldState", VersionState.DRAFT.name())
                .put("newState", VersionState.ENABLED.name())
                .toString();

        MappedCloudEvent event = mapper.mapFromStorageSnapshot(
                StorageEventType.ARTIFACT_VERSION_STATE_CHANGED.name(), payload).get(0);
        assertEquals(WebhookEventTypes.VERSION_PUBLISHED, event.eventType());
        assertEquals("g/a/1.0", event.subject());
    }

    @Test
    void mapsVersionStateChangedToDeprecated() {
        String payload = new JSONObject()
                .put("id", "evt-dep")
                .put("groupId", "g")
                .put("artifactId", "a")
                .put("version", "1.0")
                .put("oldState", VersionState.ENABLED.name())
                .put("newState", VersionState.DEPRECATED.name())
                .toString();

        MappedCloudEvent event = mapper.mapFromStorageSnapshot(
                StorageEventType.ARTIFACT_VERSION_STATE_CHANGED.name(), payload).get(0);
        assertEquals(WebhookEventTypes.VERSION_DEPRECATED, event.eventType());
    }

    @Test
    void mapsVersionStateChangedToStateChangedForDisabled() {
        String payload = new JSONObject()
                .put("id", "evt-dis")
                .put("groupId", "g")
                .put("artifactId", "a")
                .put("version", "1.0")
                .put("oldState", VersionState.ENABLED.name())
                .put("newState", VersionState.DISABLED.name())
                .toString();

        MappedCloudEvent event = mapper.mapFromStorageSnapshot(
                StorageEventType.ARTIFACT_VERSION_STATE_CHANGED.name(), payload).get(0);
        assertEquals(WebhookEventTypes.VERSION_STATE_CHANGED, event.eventType());
    }

    @Test
    void deterministicCloudEventIdIsStable() {
        String id1 = CloudEventsMapper.deterministicCloudEventId("evt-1",
                WebhookEventTypes.ARTIFACT_CREATED);
        String id2 = CloudEventsMapper.deterministicCloudEventId("evt-1",
                WebhookEventTypes.ARTIFACT_CREATED);
        assertEquals(id1, id2);
        assertFalse(id1.equals(CloudEventsMapper.deterministicCloudEventId("evt-2",
                WebhookEventTypes.ARTIFACT_CREATED)));
    }

    @Test
    void mapsRuleViolation() {
        JSONObject data = new JSONObject().put("rule", "COMPATIBILITY");
        MappedCloudEvent event = mapper.mapRuleViolation(UUID.randomUUID().toString(), "g/a", data);
        assertEquals(WebhookEventTypes.RULE_VIOLATED, event.eventType());
        assertTrue(event.payload().contains("rule.violated.v1"));
    }

    @Test
    void ignoresUnsupportedStorageEvents() {
        List<MappedCloudEvent> mapped = mapper.mapFromStorageSnapshot(
                StorageEventType.GROUP_CREATED.name(),
                new JSONObject().put("id", "x").put("groupId", "g").toString());
        assertTrue(mapped.isEmpty());
    }
}
