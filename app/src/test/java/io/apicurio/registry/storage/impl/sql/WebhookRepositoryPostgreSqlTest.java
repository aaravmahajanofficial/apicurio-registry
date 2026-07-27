package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.WebhookDeliveryDto;
import io.apicurio.registry.storage.dto.WebhookDeliveryLogDto;
import io.apicurio.registry.storage.dto.WebhookFanoutDto;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import io.apicurio.registry.storage.error.WebhookSubscriptionNotFoundException;
import io.apicurio.registry.storage.util.PostgresqlTestProfile;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
@TestProfile(PostgresqlTestProfile.class)
public class WebhookRepositoryPostgreSqlTest {

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    public void testWebhookSubscriptionCrud() {
        assertTrue(storage.supportsWebhooks());

        String subscriptionId = UUID.randomUUID().toString();
        WebhookSubscriptionDto created = WebhookSubscriptionDto.builder()
                .subscriptionId(subscriptionId)
                .url("https://example.com/hook")
                .eventTypes(List.of("io.apicurio.registry.artifact.created.v1"))
                .groupIdFilter("prod")
                .enabled(true)
                .description("test subscription")
                .createdBy("tester")
                .build();

        storage.createWebhookSubscription(created);

        assertEquals(1, storage.countWebhookSubscriptions());

        WebhookSubscriptionDto fetched = storage.getWebhookSubscription(subscriptionId);
        assertEquals("https://example.com/hook", fetched.getUrl());
        assertEquals(List.of("io.apicurio.registry.artifact.created.v1"), fetched.getEventTypes());
        assertEquals("prod", fetched.getGroupIdFilter());
        assertTrue(fetched.isEnabled());

        fetched.setDescription("updated");
        fetched.setEnabled(false);
        storage.updateWebhookSubscription(fetched);

        WebhookSubscriptionDto updated = storage.getWebhookSubscription(subscriptionId);
        assertEquals("updated", updated.getDescription());
        assertFalse(updated.isEnabled());

        assertTrue(storage.getEnabledWebhookSubscriptions().isEmpty());
        assertEquals(1, storage.listWebhookSubscriptions(0, 10).size());

        storage.deleteWebhookSubscription(subscriptionId);
        assertEquals(0, storage.countWebhookSubscriptions());
        assertThrows(WebhookSubscriptionNotFoundException.class,
                () -> storage.getWebhookSubscription(subscriptionId));
    }

    @Test
    public void testWebhookFanoutDeliveryAndLog() {
        String subscriptionId = UUID.randomUUID().toString();
        storage.createWebhookSubscription(WebhookSubscriptionDto.builder()
                .subscriptionId(subscriptionId)
                .url("https://example.com/hook")
                .eventTypes(List.of("io.apicurio.registry.artifact.created.v1"))
                .enabled(true)
                .build());

        String outboxEventId = UUID.randomUUID().toString();
        storage.insertWebhookFanout(WebhookFanoutDto.builder()
                .outboxEventId(outboxEventId)
                .sourcePayload("{\"type\":\"ARTIFACT_CREATED\"}")
                .storageEventType("ARTIFACT_CREATED")
                .fanoutStatus("PENDING")
                .fanoutAttempts(0)
                .createdOn(new Date())
                .build());

        List<WebhookFanoutDto> pending = storage.getPendingWebhookFanouts(5, 10);
        assertEquals(1, pending.size());
        assertEquals(outboxEventId, pending.get(0).getOutboxEventId());

        storage.updateWebhookFanoutStatus(WebhookFanoutDto.builder()
                .outboxEventId(outboxEventId)
                .fanoutStatus("DONE")
                .fanoutAttempts(1)
                .fanoutOn(new Date())
                .build());

        assertTrue(storage.getPendingWebhookFanouts(5, 10).isEmpty());

        String cloudEventId = UUID.randomUUID().toString();
        long deliveryId = storage.insertWebhookDelivery(WebhookDeliveryDto.builder()
                .subscriptionId(subscriptionId)
                .cloudEventId(cloudEventId)
                .eventType("io.apicurio.registry.artifact.created.v1")
                .payload("{\"id\":\"" + cloudEventId + "\"}")
                .status("PENDING")
                .attemptCount(0)
                .nextAttemptOn(new Date())
                .build());

        assertTrue(deliveryId > 0);
        assertEquals(1, storage.countWebhookDeliveries(subscriptionId));
        assertEquals(1, storage.getWebhookDeliveries(subscriptionId, 0, 10).size());

        storage.insertWebhookDeliveryLog(WebhookDeliveryLogDto.builder()
                .deliveryId(deliveryId)
                .subscriptionId(subscriptionId)
                .cloudEventId(cloudEventId)
                .attemptNumber(1)
                .httpStatus(200)
                .durationMs(42)
                .attemptedOn(new Date())
                .build());

        assertEquals(1, storage.getWebhookDeliveryLog(subscriptionId, 0, 10).size());

        storage.deleteWebhookSubscription(subscriptionId);
    }
}
