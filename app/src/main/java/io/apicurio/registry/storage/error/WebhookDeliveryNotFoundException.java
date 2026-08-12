package io.apicurio.registry.storage.error;

import lombok.Getter;

/**
 * Thrown when a webhook delivery ID does not exist in storage.
 */
public class WebhookDeliveryNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    @Getter
    private final long deliveryId;

    /**
     * @param deliveryId the delivery ID that was not found
     */
    public WebhookDeliveryNotFoundException(long deliveryId) {
        super("No webhook delivery with id '" + deliveryId + "' was found.");
        this.deliveryId = deliveryId;
    }
}
