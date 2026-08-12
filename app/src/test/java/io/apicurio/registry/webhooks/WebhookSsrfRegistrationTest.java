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

import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * REST integration tests rejecting SSRF-prone webhook URLs at registration time.
 */
@QuarkusTest
@TestProfile(WebhooksSsrfTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class WebhookSsrfRegistrationTest {

    private static final String BASE = "/registry/v3/admin/webhooks/subscriptions";
    private static final List<String> EVENT_TYPES = List.of("io.apicurio.registry.artifact.created.v1");

    @Test
    public void rejectsHttpWhenInsecureDisabled() {
        given()
                .contentType("application/json")
                .body(Map.of("url", "http://example.com/hook", "eventTypes", EVENT_TYPES))
                .when()
                .post(BASE)
                .then()
                .statusCode(400)
                .body("detail", equalTo(WebhookUrlValidator.INVALID_URL_MESSAGE));
    }

    @Test
    public void rejectsLiteralPrivateIpv4Urls() {
        for (String url : List.of(
                "https://127.0.0.1/hook",
                "https://10.0.0.1/hook",
                "https://169.254.169.254/metadata")) {
            given()
                    .contentType("application/json")
                    .body(Map.of("url", url, "eventTypes", EVENT_TYPES))
                    .when()
                    .post(BASE)
                    .then()
                    .statusCode(400)
                    .body("detail", equalTo(WebhookUrlValidator.INVALID_URL_MESSAGE));
        }
    }

    @Test
    public void rejectsLocalhostHostname() {
        given()
                .contentType("application/json")
                .body(Map.of("url", "https://localhost/hook", "eventTypes", EVENT_TYPES))
                .when()
                .post(BASE)
                .then()
                .statusCode(400)
                .body("detail", equalTo(WebhookUrlValidator.INVALID_URL_MESSAGE));
    }
}
