/*
 * Copyright 2026 Red Hat Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.registry.webhooks;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.WebhookDeliveryDto;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Enqueues {@link WebhookEventTypes#RULE_VIOLATED} CloudEvents when registry rules reject a write.
 * <p>
 * Rule violations do not produce an outbox CDI event (the write is rejected), so this component enqueues
 * deliveries directly to {@code webhook_deliveries} in a separate storage transaction. Failures are logged
 * but never prevent {@link RuleViolationException} from reaching the REST caller.
 */
@ApplicationScoped
public class RuleViolationEmitter {

    @Inject
    Logger log;

    @Inject
    WebhooksConfig webhooksConfig;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    CloudEventsMapper cloudEventsMapper;

    @Inject
    WebhookSubscriptionMatcher subscriptionMatcher;

    /**
     * Attempts to enqueue webhook deliveries for a rejected rule evaluation.
     * <p>
     * No-op when webhooks are disabled, rule-violation events are disabled, or storage does not support
     * webhooks. Enqueue failures are logged and not propagated.
     *
     * @param groupId         the artifact group id from the rejected write
     * @param artifactId      the artifact id from the rejected write
     * @param artifactType    the artifact type being validated
     * @param applicationType whether rules ran for a create or update context
     * @param violation       the rule violation thrown by the rule executor
     */
    public void emit(String groupId, String artifactId, String artifactType,
                     RuleApplicationType applicationType, RuleViolationException violation) {
        if (!webhooksConfig.isOperational() || !webhooksConfig.isRuleViolationsEnabled()) {
            return;
        }
        try {
            JSONObject violationData = buildViolationData(groupId, artifactId, artifactType,
                    applicationType, violation);
            String cloudEventId = UUID.randomUUID().toString();
            String subject = buildSubject(groupId, artifactId);
            MappedCloudEvent mappedEvent = cloudEventsMapper.mapRuleViolation(cloudEventId, subject,
                    violationData);
            enqueueMatchingDeliveries(violationData.toString(), mappedEvent);
        } catch (Exception ex) {
            log.error("Failed to enqueue rule violation webhook for {}-{}", groupId, artifactId, ex);
        }
    }

    /**
     * Builds the CloudEvents {@code data} object for a rule violation, truncating violations when needed.
     *
     * @param groupId         artifact group id
     * @param artifactId      artifact id
     * @param artifactType    artifact type
     * @param applicationType create vs update rule application context
     * @param violation       the thrown rule violation
     * @return JSON payload used both as CloudEvents data and subscription filter input
     */
    private JSONObject buildViolationData(String groupId, String artifactId, String artifactType,
                                          RuleApplicationType applicationType, RuleViolationException violation) {
        JSONObject data = new JSONObject();
        data.put("groupId", groupId);
        data.put("artifactId", artifactId);
        data.put("artifactType", artifactType);
        data.put("ruleType", violation.getRuleType().name());
        violation.getRuleConfiguration().ifPresent(config -> data.put("ruleConfiguration", config));
        if (applicationType != null) {
            data.put("applicationType", applicationType.name());
        }

        Set<RuleViolation> causes = violation.getCauses();
        int maxViolations = webhooksConfig.getViolationsMaxCount();
        JSONArray violations = new JSONArray();
        int included = 0;
        for (RuleViolation cause : causes) {
            if (included >= maxViolations) {
                break;
            }
            JSONObject entry = new JSONObject();
            entry.put("description", cause.getDescription());
            if (cause.getContext() != null && !cause.getContext().isBlank()) {
                entry.put("context", cause.getContext());
            }
            violations.put(entry);
            included++;
        }
        data.put("violations", violations);
        if (causes.size() > maxViolations) {
            data.put("truncated", true);
            data.put("totalViolations", causes.size());
        }
        return data;
    }

    /**
     * @param groupId    artifact group id
     * @param artifactId artifact id
     * @return CloudEvents subject in the form {@code groupId/artifactId}
     */
    private static String buildSubject(String groupId, String artifactId) {
        if (groupId == null || artifactId == null) {
            return null;
        }
        return groupId + "/" + artifactId;
    }

    /**
     * Enqueues {@code PENDING} deliveries for subscriptions matching the rule-violation CloudEvent.
     *
     * @param filterPayload JSON used for group and artifact-type subscription filters
     * @param mappedEvent   mapped CloudEvents envelope
     */
    private void enqueueMatchingDeliveries(String filterPayload, MappedCloudEvent mappedEvent) {
        List<WebhookSubscriptionDto> subscriptions = storage.getEnabledWebhookSubscriptions();
        for (WebhookSubscriptionDto subscription : subscriptions) {
            if (!subscriptionMatcher.matches(subscription, mappedEvent, filterPayload)) {
                continue;
            }
            WebhookDeliveryDto delivery = WebhookDeliveryDto.builder()
                    .subscriptionId(subscription.getSubscriptionId())
                    .cloudEventId(mappedEvent.cloudEventId())
                    .eventType(mappedEvent.eventType())
                    .payload(mappedEvent.payload())
                    .status(WebhookDeliveryStatuses.PENDING)
                    .attemptCount(0)
                    .build();
            storage.insertWebhookDelivery(delivery);
        }
    }
}
