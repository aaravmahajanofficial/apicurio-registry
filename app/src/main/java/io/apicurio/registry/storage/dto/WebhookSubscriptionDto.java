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
import java.util.List;

/**
 * Persistence model for a webhook subscription ({@code webhook_subscriptions} table).
 * <p>
 * Maps operator-configured endpoint URL, event-type filters, optional group/artifact-type filters,
 * and signing secret metadata. The plaintext secret is never stored; only {@link #secretHash} is
 * persisted.
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class WebhookSubscriptionDto {

    private String subscriptionId;
    private String url;
    private List<String> eventTypes;
    private String groupIdFilter;
    private String artifactTypeFilter;
    private String secretHash;
    private boolean enabled;
    private String description;
    private String createdBy;
    private Date createdOn;
    private Date modifiedOn;
}
