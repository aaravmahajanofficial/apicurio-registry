package io.apicurio.registry.webhooks;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebhookSubscriptionHealthService}.
 */
class WebhookSubscriptionHealthServiceTest {

    private RegistryStorage storage;
    private WebhooksConfig webhooksConfig;
    private WebhookMetricsService metrics;
    private WebhookSubscriptionHealthService healthService;

    @BeforeEach
    void setUp() throws Exception {
        storage = mock(RegistryStorage.class);
        webhooksConfig = mock(WebhooksConfig.class);
        metrics = mock(WebhookMetricsService.class);
        healthService = new WebhookSubscriptionHealthService();
        setField(healthService, "storage", storage);
        setField(healthService, "webhooksConfig", webhooksConfig);
        setField(healthService, "metrics", metrics);
        setField(healthService, "log",
                org.slf4j.LoggerFactory.getLogger(WebhookSubscriptionHealthServiceTest.class));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void onDeliverySucceededResetsConsecutiveFailures() {
        String subscriptionId = "sub-1";
        WebhookSubscriptionDto subscription = WebhookSubscriptionDto.builder()
                .subscriptionId(subscriptionId)
                .consecutiveDeliveryFailures(3)
                .enabled(true)
                .build();
        when(storage.getWebhookSubscription(subscriptionId)).thenReturn(subscription);

        healthService.onDeliverySucceeded(subscriptionId);

        ArgumentCaptor<WebhookSubscriptionDto> captor = ArgumentCaptor.forClass(WebhookSubscriptionDto.class);
        verify(storage).updateWebhookSubscription(captor.capture());
        assertEquals(0, captor.getValue().getConsecutiveDeliveryFailures());
    }

    @Test
    void onDeliverySucceededSkipsUpdateWhenAlreadyZero() {
        String subscriptionId = "sub-1";
        when(storage.getWebhookSubscription(subscriptionId)).thenReturn(
                WebhookSubscriptionDto.builder()
                        .subscriptionId(subscriptionId)
                        .consecutiveDeliveryFailures(0)
                        .enabled(true)
                        .build());

        healthService.onDeliverySucceeded(subscriptionId);

        verify(storage, never()).updateWebhookSubscription(any());
    }

    @Test
    void onDeliveryDeadLetterAutoDisablesAtThreshold() {
        String subscriptionId = "sub-1";
        when(webhooksConfig.getDeliveryAutoDisableThreshold()).thenReturn(2);
        WebhookSubscriptionDto subscription = WebhookSubscriptionDto.builder()
                .subscriptionId(subscriptionId)
                .consecutiveDeliveryFailures(1)
                .enabled(true)
                .build();
        when(storage.getWebhookSubscription(subscriptionId)).thenReturn(subscription);

        healthService.onDeliveryDeadLetter(subscriptionId);

        ArgumentCaptor<WebhookSubscriptionDto> captor = ArgumentCaptor.forClass(WebhookSubscriptionDto.class);
        verify(storage).updateWebhookSubscription(captor.capture());
        assertEquals(2, captor.getValue().getConsecutiveDeliveryFailures());
        assertFalse(captor.getValue().isEnabled());
        verify(metrics).recordSubscriptionAutoDisabled();
    }
}
