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
