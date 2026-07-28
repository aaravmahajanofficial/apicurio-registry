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
 * Persistence model for a webhook fanout record ({@code webhook_fanout} table).
 * <p>
 * Captures a durable snapshot of a registry storage event ({@link #sourcePayload}) so fanout and
 * reconciliation can proceed independently of the ephemeral Debezium outbox table.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class WebhookFanoutDto {

    private String outboxEventId;
    private String sourcePayload;
    private String storageEventType;
    private String fanoutStatus;
    private int fanoutAttempts;
    private String lastError;
    private Date createdOn;
    private Date fanoutOn;
}
