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
 * Persistence model for a single webhook delivery attempt ({@code webhook_delivery_log} table).
 * <p>
 * Append-only audit record capturing HTTP status, duration, and error details for each delivery
 * attempt. Used by the admin delivery-log API and retention purge job.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class WebhookDeliveryLogDto {

    private long logId;
    private long deliveryId;
    private String subscriptionId;
    private String cloudEventId;
    private int attemptNumber;
    private Integer httpStatus;
    private Integer durationMs;
    private String error;
    private Date attemptedOn;
}
