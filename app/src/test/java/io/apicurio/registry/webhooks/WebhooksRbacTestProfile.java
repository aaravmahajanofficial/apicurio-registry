package io.apicurio.registry.webhooks;

import io.apicurio.registry.utils.tests.PostgreSqlEmbeddedTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quarkus test profile for webhook RBAC tests: PostgreSQL, webhooks enabled, and basic auth with
 * embedded admin ({@code alice}) and read-only ({@code duncan}) users.
 */
public class WebhooksRbacTestProfile implements QuarkusTestProfile {

    /**
     * Configures PostgreSQL, webhooks, role-based authorization, and embedded basic-auth users.
     */
    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("apicurio.storage.sql.kind", "postgresql");
        props.put("apicurio.features.experimental.enabled", "true");
        props.put("apicurio.webhooks.enabled", "true");
        props.put("apicurio.webhooks.allow-insecure-urls", "true");
        props.put("quarkus.oidc.tenant-enabled", "false");
        props.put("quarkus.http.auth.basic", "true");
        props.put("apicurio.auth.role-based-authorization", "true");
        props.put("apicurio.auth.role-source", "token");
        props.put("quarkus.security.users.embedded.enabled", "true");
        props.put("quarkus.security.users.embedded.plain-text", "true");
        props.put("quarkus.security.users.embedded.users.alice", "alice");
        props.put("quarkus.security.users.embedded.users.duncan", "duncan");
        props.put("quarkus.security.users.embedded.roles.alice", "sr-admin");
        props.put("quarkus.security.users.embedded.roles.duncan", "sr-readonly");
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
