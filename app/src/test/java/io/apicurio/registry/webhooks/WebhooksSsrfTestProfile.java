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

import io.apicurio.registry.utils.tests.PostgreSqlEmbeddedTestResource;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quarkus test profile for SSRF registration tests with strict URL policies.
 */
public class WebhooksSsrfTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("apicurio.storage.sql.kind", "postgresql");
        props.put("apicurio.features.experimental.enabled", "true");
        props.put("apicurio.webhooks.enabled", "true");
        props.put("apicurio.webhooks.allow-insecure-urls", "false");
        props.put("apicurio.webhooks.security.block-private-ips", "true");
        props.put("apicurio.webhooks.secrets.encryption-key", WebhooksTestSecrets.ENCRYPTION_KEY);
        return props;
    }

    @Override
    public List<TestResourceEntry> testResources() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            return List.of(new TestResourceEntry(PostgreSqlEmbeddedTestResource.class));
        }
        return List.of();
    }
}
