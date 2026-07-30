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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.violation.RuleViolation;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.WebhookDeliveryDto;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import io.apicurio.registry.types.RuleType;

/**
 * Unit tests for {@link RuleViolationEmitter}.
 */
class RuleViolationEmitterTest {

    private RuleViolationEmitter emitter;
    private WebhooksConfig webhooksConfig;
    private RegistryStorage storage;
    private CloudEventsMapper cloudEventsMapper;

    @BeforeEach
    void setUp() throws Exception {
        emitter = new RuleViolationEmitter();
        webhooksConfig = mock(WebhooksConfig.class);
        storage = mock(RegistryStorage.class);
        cloudEventsMapper = new CloudEventsMapper();

        Field mapperConfig = CloudEventsMapper.class.getDeclaredField("webhooksConfig");
        mapperConfig.setAccessible(true);
        mapperConfig.set(cloudEventsMapper, webhooksConfig);
        when(webhooksConfig.getPayloadMaxBytes()).thenReturn(262144);
        when(webhooksConfig.getViolationsMaxCount()).thenReturn(20);

        setField(emitter, "log", mock(Logger.class));
        setField(emitter, "webhooksConfig", webhooksConfig);
        setField(emitter, "storage", storage);
        setField(emitter, "cloudEventsMapper", cloudEventsMapper);
        setField(emitter, "subscriptionMatcher", new WebhookSubscriptionMatcher());
    }

    @Test
    void enqueuesDeliveryWhenEnabled() {
        when(webhooksConfig.isOperational()).thenReturn(true);
        when(webhooksConfig.isRuleViolationsEnabled()).thenReturn(true);
        WebhookSubscriptionDto subscription = WebhookSubscriptionDto.builder()
                .subscriptionId("sub-1")
                .enabled(true)
                .eventTypes(List.of(WebhookEventTypes.RULE_VIOLATED))
                .build();
        when(storage.getEnabledWebhookSubscriptions()).thenReturn(List.of(subscription));

        RuleViolationException violation = violationWithCauses(1);
        emitter.emit("prod", "orders", "AVRO", RuleApplicationType.UPDATE, violation);

        verify(storage).insertWebhookDelivery(any(WebhookDeliveryDto.class));
    }

    @Test
    void skipsWhenRuleViolationsDisabled() {
        when(webhooksConfig.isOperational()).thenReturn(true);
        when(webhooksConfig.isRuleViolationsEnabled()).thenReturn(false);

        emitter.emit("prod", "orders", "AVRO", RuleApplicationType.UPDATE, violationWithCauses(1));

        verify(storage, never()).insertWebhookDelivery(any());
    }

    @Test
    void truncatesViolationsInPayload() throws Exception {
        when(webhooksConfig.isOperational()).thenReturn(true);
        when(webhooksConfig.isRuleViolationsEnabled()).thenReturn(true);
        when(webhooksConfig.getViolationsMaxCount()).thenReturn(2);
        when(storage.getEnabledWebhookSubscriptions()).thenReturn(List.of());

        RuleViolationException violation = violationWithCauses(5);
        emitter.emit("prod", "orders", "AVRO", RuleApplicationType.CREATE, violation);

        verify(storage, never()).insertWebhookDelivery(any());
    }

    private static RuleViolationException violationWithCauses(int count) {
        Set<RuleViolation> causes = new HashSet<>();
        for (int i = 0; i < count; i++) {
            causes.add(new RuleViolation("violation-" + i, "/path/" + i));
        }
        return new RuleViolationException("failed", RuleType.COMPATIBILITY, "BACKWARD", causes);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
