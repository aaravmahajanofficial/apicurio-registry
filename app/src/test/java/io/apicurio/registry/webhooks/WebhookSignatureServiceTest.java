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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureServiceTest {

    private final WebhookSignatureService service = new WebhookSignatureService();

    @Test
    void signAndVerifyValidPayload() {
        String secret = "whsec_test_secret_value";
        String body = "{\"specversion\":\"1.0\",\"id\":\"evt-1\"}";
        String header = service.sign(secret, body);
        assertTrue(service.verify(secret, body, header));
    }

    @Test
    void rejectsTamperedBody() {
        String secret = "whsec_test_secret_value";
        String body = "{\"specversion\":\"1.0\"}";
        String header = service.sign(secret, body);
        assertFalse(service.verify(secret, "{\"tampered\":true}", header));
    }

    @Test
    void rejectsExpiredTimestamp() {
        String secret = "whsec_test_secret_value";
        String body = "{\"specversion\":\"1.0\"}";
        long expiredTimestamp = Instant.now().getEpochSecond() - 600;
        String header = "t=" + expiredTimestamp + ",v1=deadbeef";
        assertFalse(service.verify(secret, body, header, Duration.ofMinutes(5)));
    }

    @Test
    void rejectsMalformedHeader() {
        assertFalse(service.verify("whsec_x", "{}", "invalid-header"));
    }
}
