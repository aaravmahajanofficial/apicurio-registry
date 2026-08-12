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
import static org.hamcrest.Matchers.equalTo;

/**
 * RBAC integration tests for webhook subscription admin endpoints.
 */
@QuarkusTest
@TestProfile(WebhooksRbacTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class WebhookSubscriptionRbacTest extends AbstractResourceTestBase {

    private static final String BASE = "/registry/v3/admin/webhooks/subscriptions";

    private static final Map<String, Object> CREATE_BODY = Map.of(
            "url", "https://example.com/hooks/rbac",
            "eventTypes", List.of("io.apicurio.registry.artifact.created.v1"));

    /**
     * Unauthenticated requests are rejected with HTTP 401.
     */
    @Test
    public void testAnonymousDenied() {
        given()
                .when()
                .get(BASE)
                .then()
                .statusCode(401);
    }

    /**
     * Read-only users may list subscriptions but cannot create them (HTTP 403).
     */
    @Test
    public void testReadOnlyCanListButNotCreate() {
        given()
                .auth().preemptive().basic("duncan", "duncan")
                .when()
                .get(BASE)
                .then()
                .statusCode(200)
                .body("count", equalTo(0));

        given()
                .auth().preemptive().basic("duncan", "duncan")
                .contentType(CT_JSON)
                .body(CREATE_BODY)
                .when()
                .post(BASE)
                .then()
                .statusCode(403);
    }

    /**
     * Admin users may create webhook subscriptions.
     */
    @Test
    public void testAdminCanCreate() {
        given()
                .auth().preemptive().basic("alice", "alice")
                .contentType(CT_JSON)
                .body(CREATE_BODY)
                .when()
                .post(BASE)
                .then()
                .statusCode(200);
    }
}
