package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.rest.v3.beans.CreateWebhookSubscription;
import io.apicurio.registry.rest.v3.beans.UpdateWebhookSubscription;
import io.apicurio.registry.rest.v3.beans.WebhookDelivery;
import io.apicurio.registry.rest.v3.beans.WebhookDeliverySearchResults;
import io.apicurio.registry.rest.v3.beans.WebhookEventType;
import io.apicurio.registry.rest.v3.beans.WebhookSubscription;
import io.apicurio.registry.rest.v3.beans.WebhookSubscriptionSearchResults;
import io.apicurio.registry.storage.dto.WebhookDeliveryDto;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Mapping utilities between webhook storage DTOs and REST v3 API beans.
 */
public final class WebhooksApiUtil {

    private WebhooksApiUtil() {
    }

    /**
     * Maps a storage DTO to an API subscription, including the one-time plaintext signing secret.
     *
     * @param dto the persisted subscription
     * @param secret the plaintext signing secret (returned only on create)
     * @return the REST representation with {@code secret} populated
     */
    public static WebhookSubscription dtoToWebhookSubscription(WebhookSubscriptionDto dto, String secret) {
        WebhookSubscription subscription = dtoToWebhookSubscription(dto);
        subscription.setSecret(secret);
        return subscription;
    }

    /**
     * Maps a storage DTO to an API subscription without a signing secret.
     *
     * @param dto the persisted subscription
     * @return the REST representation (secret is not set)
     */
    public static WebhookSubscription dtoToWebhookSubscription(WebhookSubscriptionDto dto) {
        WebhookSubscription subscription = new WebhookSubscription();
        subscription.setSubscriptionId(dto.getSubscriptionId());
        subscription.setUrl(dto.getUrl());
        subscription.setEventTypes(toEventTypes(dto.getEventTypes()));
        subscription.setGroupId(dto.getGroupIdFilter());
        subscription.setArtifactType(dto.getArtifactTypeFilter());
        subscription.setDescription(dto.getDescription());
        subscription.setEnabled(dto.isEnabled());
        subscription.setCreatedBy(dto.getCreatedBy());
        if (dto.getCreatedOn() != null) {
            subscription.setCreatedOn(new Date(dto.getCreatedOn().getTime()));
        }
        if (dto.getModifiedOn() != null) {
            subscription.setModifiedOn(new Date(dto.getModifiedOn().getTime()));
        }
        return subscription;
    }

    /**
     * Builds a paginated subscription search result from storage rows and a total count.
     *
     * @param dtos the page of subscription DTOs
     * @param count the total number of subscriptions in the registry
     * @return the REST search results envelope
     */
    public static WebhookSubscriptionSearchResults toSearchResults(List<WebhookSubscriptionDto> dtos,
            long count) {
        WebhookSubscriptionSearchResults results = new WebhookSubscriptionSearchResults();
        results.setCount((int) count);
        List<WebhookSubscription> subscriptions = new ArrayList<>();
        for (WebhookSubscriptionDto dto : dtos) {
            subscriptions.add(dtoToWebhookSubscription(dto));
        }
        results.setSubscriptions(subscriptions);
        return results;
    }

    /**
     * Builds a paginated delivery search result from storage rows and a total count.
     *
     * @param dtos the page of delivery DTOs
     * @param count the total number of deliveries for the subscription
     * @return the REST search results envelope
     */
    public static WebhookDeliverySearchResults toDeliverySearchResults(List<WebhookDeliveryDto> dtos,
            long count) {
        WebhookDeliverySearchResults results = new WebhookDeliverySearchResults();
        results.setCount((int) count);
        List<WebhookDelivery> deliveries = new ArrayList<>();
        for (WebhookDeliveryDto dto : dtos) {
            deliveries.add(dtoToWebhookDelivery(dto));
        }
        results.setDeliveries(deliveries);
        return results;
    }

    /**
     * Maps a storage delivery DTO to the REST representation.
     *
     * @param dto the persisted delivery record
     * @return the REST delivery bean
     */
    public static WebhookDelivery dtoToWebhookDelivery(WebhookDeliveryDto dto) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setDeliveryId(dto.getDeliveryId());
        delivery.setSubscriptionId(dto.getSubscriptionId());
        delivery.setCloudEventId(dto.getCloudEventId());
        delivery.setEventType(dto.getEventType());
        delivery.setStatus(dto.getStatus());
        delivery.setAttemptCount(dto.getAttemptCount());
        if (dto.getNextAttemptOn() != null) {
            delivery.setNextAttemptOn(new Date(dto.getNextAttemptOn().getTime()));
        }
        delivery.setLastError(dto.getLastError());
        if (dto.getCreatedOn() != null) {
            delivery.setCreatedOn(new Date(dto.getCreatedOn().getTime()));
        }
        if (dto.getModifiedOn() != null) {
            delivery.setModifiedOn(new Date(dto.getModifiedOn().getTime()));
        }
        return delivery;
    }

    /**
     * Builds a storage DTO from a create-subscription API request.
     *
     * @param data the create request body
     * @param subscriptionId the generated subscription identifier
     * @param secretHash the SHA-256 hash of the signing secret
     * @param secretEncrypted AES-GCM encrypted signing secret for delivery-time HMAC
     * @param createdBy the authenticated principal, or {@code null} if anonymous
     * @return a DTO ready for persistence
     */
    public static WebhookSubscriptionDto createToDto(CreateWebhookSubscription data, String subscriptionId,
            String secretHash, String secretEncrypted, String createdBy) {
        boolean enabled = data.getEnabled() == null || data.getEnabled();
        return WebhookSubscriptionDto.builder()
                .subscriptionId(subscriptionId)
                .url(data.getUrl())
                .eventTypes(fromEventTypes(data.getEventTypes()))
                .groupIdFilter(data.getGroupId())
                .artifactTypeFilter(data.getArtifactType())
                .secretHash(secretHash)
                .secretEncrypted(secretEncrypted)
                .enabled(enabled)
                .description(data.getDescription())
                .createdBy(createdBy)
                .build();
    }

    /**
     * Applies non-null fields from an update request onto an existing subscription DTO.
     * <p>
     * Omitted fields are left unchanged. {@code groupId} and {@code artifactType} are updated only
     * when a non-null value is supplied.
     *
     * @param existing the subscription loaded from storage
     * @param data the update request body
     */
    public static void applyUpdate(WebhookSubscriptionDto existing, UpdateWebhookSubscription data) {
        if (data.getUrl() != null) {
            existing.setUrl(data.getUrl());
        }
        if (data.getEventTypes() != null) {
            existing.setEventTypes(fromEventTypes(data.getEventTypes()));
        }
        if (data.getDescription() != null) {
            existing.setDescription(data.getDescription());
        }
        if (data.getEnabled() != null) {
            existing.setEnabled(data.getEnabled());
        }
        if (data.getGroupId() != null) {
            existing.setGroupIdFilter(data.getGroupId());
        }
        if (data.getArtifactType() != null) {
            existing.setArtifactTypeFilter(data.getArtifactType());
        }
    }

    /**
     * Converts OpenAPI event type enums to their string values for storage.
     *
     * @param eventTypes the API event types, or {@code null}
     * @return the corresponding string values (empty list if {@code eventTypes} is {@code null})
     */
    public static List<String> fromEventTypes(List<WebhookEventType> eventTypes) {
        if (eventTypes == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (WebhookEventType eventType : eventTypes) {
            result.add(eventType.value());
        }
        return result;
    }

    /**
     * Converts storage event type strings to OpenAPI event type enums.
     *
     * @param eventTypes the persisted event type strings, or {@code null}
     * @return the corresponding API enums (empty list if {@code eventTypes} is {@code null})
     */
    public static List<WebhookEventType> toEventTypes(List<String> eventTypes) {
        if (eventTypes == null) {
            return List.of();
        }
        List<WebhookEventType> result = new ArrayList<>();
        for (String eventType : eventTypes) {
            result.add(WebhookEventType.fromValue(eventType));
        }
        return result;
    }
}
