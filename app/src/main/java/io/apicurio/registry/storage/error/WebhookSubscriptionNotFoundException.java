package io.apicurio.registry.storage.error;

import lombok.Getter;

/**
 * Thrown when a webhook subscription ID does not exist in storage.
 */
public class WebhookSubscriptionNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    @Getter
    private final String subscriptionId;

    /**
     * @param subscriptionId the subscription ID that was not found
     */
    public WebhookSubscriptionNotFoundException(String subscriptionId) {
        super("No webhook subscription with id '" + subscriptionId + "' was found.");
        this.subscriptionId = subscriptionId;
    }
}
