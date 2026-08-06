package io.apicurio.registry.storage.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

/**
 * Persistence model for a queued webhook delivery ({@code webhook_deliveries} table).
 * <p>
 * Represents one CloudEvent envelope to be POSTed to a subscriber. Status transitions
 * (e.g. {@code PENDING}, {@code IN_PROGRESS}, {@code DELIVERED}, {@code DEAD_LETTER}) are
 * managed by the delivery worker in later phases.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class WebhookDeliveryDto {

    private long deliveryId;
    private String subscriptionId;
    private String cloudEventId;
    private String eventType;
    private String payload;
    private String status;
    private int attemptCount;
    private Date nextAttemptOn;
    private String lastError;
    private Date createdOn;
    private Date modifiedOn;
}
