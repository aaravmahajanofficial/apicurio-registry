package io.apicurio.registry.webhooks;

import io.apicurio.registry.utils.tests.PostgreSqlEmbeddedTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quarkus test profile for webhook REST tests against PostgreSQL with webhooks enabled.
 */
public class WebhooksPostgreSqlTestProfile implements QuarkusTestProfile {

    /**
     * Enables experimental features, PostgreSQL storage, and webhook notifications.
     */
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("apicurio.storage.sql.kind", "postgresql");
        props.put("apicurio.features.experimental.enabled", "true");
        props.put("apicurio.webhooks.enabled", "true");
        props.put("apicurio.webhooks.allow-insecure-urls", "true");
        props.put("apicurio.webhooks.subscriptions.max-count", "100");
        props.put("apicurio.webhooks.secrets.encryption-key", WebhooksTestSecrets.ENCRYPTION_KEY);
        return props;
    }

    /**
     * Starts an embedded PostgreSQL instance unless {@code cluster.tests} is set.
     */
    @Override
    public List<TestResourceEntry> testResources() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            return List.of(new TestResourceEntry(PostgreSqlEmbeddedTestResource.class));
        }
        return List.of();
    }
}
