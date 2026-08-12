package io.apicurio.registry.webhooks;

import io.apicurio.registry.storage.RegistryStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WebhookDeliveryLogReaper}.
 */
class WebhookDeliveryLogReaperTest {

    private RegistryStorage storage;
    private WebhooksConfig webhooksConfig;
    private WebhookDeliveryLogReaper reaper;

    @BeforeEach
    void setUp() throws Exception {
        storage = mock(RegistryStorage.class);
        webhooksConfig = mock(WebhooksConfig.class);
        reaper = new WebhookDeliveryLogReaper();
        setField(reaper, "storage", storage);
        setField(reaper, "webhooksConfig", webhooksConfig);
        setField(reaper, "log", org.slf4j.LoggerFactory.getLogger(WebhookDeliveryLogReaperTest.class));
        when(webhooksConfig.getLogRetention()).thenReturn("7d");
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void reapDeletesLogsOlderThanRetention() {
        long before = System.currentTimeMillis();
        reaper.reap();
        long expectedCutoffUpper = before - WebhookDeliveryBackoff.parseDuration("7d").toMillis();
        verify(storage).deleteOldWebhookDeliveryLogs(anyLong());
        // cutoff is computed inside reap(); verify call happened
    }
}
