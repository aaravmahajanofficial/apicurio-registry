package io.apicurio.registry.webhooks;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * REST integration tests for webhook subscription CRUD, pagination, and validation.
 */
@QuarkusTest
@TestProfile(WebhooksPostgreSqlTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class WebhookSubscriptionResourceTest extends AbstractResourceTestBase {

    private static final String BASE = "/registry/v3/admin/webhooks/subscriptions";

    /**
     * Exercises create, get, update, list, list deliveries, and delete.
     */
    @Test
    public void testWebhookSubscriptionCrud() {
        Map<String, Object> createBody = Map.of(
                "url", "https://example.com/hooks/apicurio",
                "eventTypes", List.of("io.apicurio.registry.artifact.created.v1"),
                "groupId", "prod",
                "description", "test subscription",
                "enabled", true);

        String subscriptionId = given()
                .contentType(CT_JSON)
                .body(createBody)
                .when()
                .post(BASE)
                .then()
                .statusCode(200)
                .body("subscriptionId", notNullValue())
                .body("url", equalTo("https://example.com/hooks/apicurio"))
                .body("eventTypes[0]", equalTo("io.apicurio.registry.artifact.created.v1"))
                .body("groupId", equalTo("prod"))
                .body("enabled", equalTo(true))
                .body("secret", startsWith("whsec_"))
                .body("createdOn", notNullValue())
                .extract()
                .path("subscriptionId");

        given()
                .when()
                .get(BASE + "/" + subscriptionId)
                .then()
                .statusCode(200)
                .body("subscriptionId", equalTo(subscriptionId))
                .body("secret", nullValue());

        given()
                .contentType(CT_JSON)
                .body(Map.of("description", "updated", "enabled", false))
                .when()
                .put(BASE + "/" + subscriptionId)
                .then()
                .statusCode(200)
                .body("description", equalTo("updated"))
                .body("enabled", equalTo(false));

        given()
                .when()
                .get(BASE)
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("subscriptions[0].subscriptionId", notNullValue());

        given()
                .when()
                .get(BASE + "/" + subscriptionId + "/deliveries")
                .then()
                .statusCode(200)
                .body("count", equalTo(0))
                .body("deliveries", notNullValue());

        given()
                .when()
                .delete(BASE + "/" + subscriptionId)
                .then()
                .statusCode(204);

        given()
                .when()
                .get(BASE + "/" + subscriptionId)
                .then()
                .statusCode(404);
    }

    /**
     * Verifies HTTP 400 for invalid URL scheme, unknown event type, and missing event types.
     */
    @Test
    public void testValidationErrors() {
        given()
                .contentType(CT_JSON)
                .body(Map.of("url", "ftp://example.com/hook", "eventTypes", List.of(
                        "io.apicurio.registry.artifact.created.v1")))
                .when()
                .post(BASE)
                .then()
                .statusCode(400);

        given()
                .contentType(CT_JSON)
                .body(Map.of("url", "https://example.com/hook", "eventTypes", List.of("unknown.event")))
                .when()
                .post(BASE)
                .then()
                .statusCode(400)
                .body("detail", containsString("Unknown webhook event type"));

        given()
                .contentType(CT_JSON)
                .body(Map.of("url", "https://example.com/hook"))
                .when()
                .post(BASE)
                .then()
                .statusCode(400);
    }
}
