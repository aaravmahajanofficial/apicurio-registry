package io.apicurio.registry.rest.v3.impl;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.logging.audit.Audited;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.rest.ConflictException;
import io.apicurio.registry.rest.MethodMetadata;
import io.apicurio.registry.rest.ParameterValidationUtils;
import io.apicurio.registry.rest.v3.beans.CreateWebhookSubscription;
import io.apicurio.registry.rest.v3.beans.UpdateWebhookSubscription;
import io.apicurio.registry.rest.v3.beans.WebhookDeliverySearchResults;
import io.apicurio.registry.rest.v3.beans.WebhookSubscription;
import io.apicurio.registry.rest.v3.beans.WebhookSubscriptionSearchResults;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import io.apicurio.registry.webhooks.WebhookEventTypes;
import io.apicurio.registry.webhooks.WebhookSecretCipher;
import io.apicurio.registry.webhooks.WebhookSecretUtil;
import io.apicurio.registry.webhooks.WebhookUrlValidator;
import io.apicurio.registry.webhooks.WebhooksConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.ws.rs.BadRequestException;
import io.quarkus.security.identity.SecurityIdentity;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Business logic for CloudEvents webhook subscription management.
 * <p>
 * Exposed via {@link AdminResourceImpl} at {@code /admin/webhooks/subscriptions}. Requires
 * {@link WebhooksConfig#isOperational()} ({@code apicurio.webhooks.enabled=true} and PostgreSQL
 * storage).
 */
@ApplicationScoped
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class WebhooksResourceImpl {

    private static final String WEBHOOKS_NOT_OPERATIONAL = "Webhook notifications are not enabled on this "
            + "registry instance. Set apicurio.webhooks.enabled=true and use PostgreSQL storage.";

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    WebhooksConfig webhooksConfig;

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    WebhookSecretCipher secretCipher;

    /**
     * Creates a webhook subscription and returns the signing secret exactly once.
     *
     * @param data the create request (URL, event types, optional filters)
     * @return the created subscription including the plaintext {@code secret}
     * @throws ConflictException if the registry-wide subscription limit is reached or webhooks are
     *         not operational
     * @throws BadRequestException if URL or event type validation fails
     */
    @Audited
    @MethodMetadata(extractParameters = {"0"})
    public WebhookSubscription createWebhookSubscription(CreateWebhookSubscription data) {
        requireOperational();
        ParameterValidationUtils.requireParameter("data", data);
        ParameterValidationUtils.requireParameter("url", data.getUrl());
        ParameterValidationUtils.requireParameter("eventTypes", data.getEventTypes());

        WebhookUrlValidator.validate(data.getUrl(), webhooksConfig.isAllowInsecureUrls(),
                webhooksConfig.isBlockPrivateIps());
        validateEventTypes(WebhooksApiUtil.fromEventTypes(data.getEventTypes()));

        if (storage.countWebhookSubscriptions() >= webhooksConfig.getSubscriptionsMaxCount()) {
            throw new ConflictException("Maximum number of webhook subscriptions ("
                    + webhooksConfig.getSubscriptionsMaxCount() + ") has been reached.");
        }

        String secret = WebhookSecretUtil.generateSecret();
        String subscriptionId = UUID.randomUUID().toString();
        WebhookSubscriptionDto dto = WebhooksApiUtil.createToDto(data, subscriptionId,
                WebhookSecretUtil.hashSecret(secret), secretCipher.encrypt(secret), resolveCreatedBy());

        storage.createWebhookSubscription(dto);
        return WebhooksApiUtil.dtoToWebhookSubscription(
                storage.getWebhookSubscription(subscriptionId), secret);
    }

    /**
     * Lists webhook subscriptions with offset/limit pagination.
     *
     * @param limit maximum results to return (defaults to 20)
     * @param offset number of results to skip (defaults to 0)
     * @return a page of subscriptions and the total count
     */
    public WebhookSubscriptionSearchResults listWebhookSubscriptions(BigInteger limit, BigInteger offset) {
        requireOperational();
        int resolvedOffset = offset != null ? offset.intValue() : 0;
        int resolvedLimit = limit != null ? limit.intValue() : 20;
        return WebhooksApiUtil.toSearchResults(
                storage.listWebhookSubscriptions(resolvedOffset, resolvedLimit),
                storage.countWebhookSubscriptions());
    }

    /**
     * Returns a single webhook subscription by ID. The signing secret is never included.
     *
     * @param subscriptionId the subscription identifier
     * @return the subscription
     */
    public WebhookSubscription getWebhookSubscription(String subscriptionId) {
        requireOperational();
        ParameterValidationUtils.requireParameter("subscriptionId", subscriptionId);
        return WebhooksApiUtil.dtoToWebhookSubscription(storage.getWebhookSubscription(subscriptionId));
    }

    /**
     * Updates an existing webhook subscription. The signing secret cannot be rotated via this API
     * in Phase 2.
     *
     * @param subscriptionId the subscription identifier
     * @param data the fields to update (non-null fields only)
     * @return the updated subscription
     * @throws BadRequestException if URL or event type validation fails
     */
    @Audited
    @MethodMetadata(extractParameters = {"0", "1"})
    public WebhookSubscription updateWebhookSubscription(String subscriptionId,
            UpdateWebhookSubscription data) {
        requireOperational();
        ParameterValidationUtils.requireParameter("subscriptionId", subscriptionId);
        ParameterValidationUtils.requireParameter("data", data);

        WebhookSubscriptionDto existing = storage.getWebhookSubscription(subscriptionId);
        if (data.getUrl() != null) {
            WebhookUrlValidator.validate(data.getUrl(), webhooksConfig.isAllowInsecureUrls(),
                    webhooksConfig.isBlockPrivateIps());
        }
        if (data.getEventTypes() != null) {
            validateEventTypes(WebhooksApiUtil.fromEventTypes(data.getEventTypes()));
        }

        WebhooksApiUtil.applyUpdate(existing, data);
        storage.updateWebhookSubscription(existing);
        return WebhooksApiUtil.dtoToWebhookSubscription(storage.getWebhookSubscription(subscriptionId));
    }

    /**
     * Deletes a webhook subscription and its pending deliveries (via storage cascade).
     *
     * @param subscriptionId the subscription identifier
     */
    @Audited
    @MethodMetadata(extractParameters = {"0"})
    public void deleteWebhookSubscription(String subscriptionId) {
        requireOperational();
        ParameterValidationUtils.requireParameter("subscriptionId", subscriptionId);
        storage.deleteWebhookSubscription(subscriptionId);
    }

    /**
     * Lists delivery records for a subscription with offset/limit pagination.
     *
     * @param subscriptionId the subscription identifier
     * @param limit maximum results to return (defaults to 20)
     * @param offset number of results to skip (defaults to 0)
     * @return a page of deliveries and the total count for the subscription
     */
    public WebhookDeliverySearchResults listWebhookDeliveries(String subscriptionId, BigInteger limit,
            BigInteger offset) {
        requireOperational();
        ParameterValidationUtils.requireParameter("subscriptionId", subscriptionId);
        storage.getWebhookSubscription(subscriptionId);

        int resolvedOffset = offset != null ? offset.intValue() : 0;
        int resolvedLimit = limit != null ? limit.intValue() : 20;
        return WebhooksApiUtil.toDeliverySearchResults(
                storage.getWebhookDeliveries(subscriptionId, resolvedOffset, resolvedLimit),
                storage.countWebhookDeliveries(subscriptionId));
    }

    /**
     * Ensures webhooks are enabled and backed by a supported storage variant.
     *
     * @throws ConflictException if {@link WebhooksConfig#isOperational()} is {@code false}
     */
    private void requireOperational() {
        if (!webhooksConfig.isOperational()) {
            throw new ConflictException(WEBHOOKS_NOT_OPERATIONAL);
        }
    }

    /**
     * Validates event types and maps validation failures to HTTP 400.
     *
     * @param eventTypes the event type strings to validate
     * @throws BadRequestException if validation fails
     */
    private void validateEventTypes(java.util.List<String> eventTypes) {
        try {
            WebhookEventTypes.validate(eventTypes);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }
    }

    /**
     * @return the authenticated principal name, or {@code null} when the caller is anonymous
     */
    private String resolveCreatedBy() {
        if (securityIdentity != null && !securityIdentity.isAnonymous()) {
            return securityIdentity.getPrincipal().getName();
        }
        return null;
    }
}
