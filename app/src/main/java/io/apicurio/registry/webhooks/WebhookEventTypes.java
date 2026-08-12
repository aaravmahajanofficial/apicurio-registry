package io.apicurio.registry.webhooks;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Canonical CloudEvents type identifiers supported by webhook subscriptions.
 * <p>
 * Values mirror the {@code WebhookEventType} OpenAPI enum and are used for server-side validation
 * before persisting subscriptions.
 */
public final class WebhookEventTypes {

    /** Emitted when a new artifact is created. */
    public static final String ARTIFACT_CREATED = "io.apicurio.registry.artifact.created.v1";

    /** Emitted when artifact metadata is updated. */
    public static final String ARTIFACT_UPDATED = "io.apicurio.registry.artifact.updated.v1";

    /** Emitted when an artifact is deleted. */
    public static final String ARTIFACT_DELETED = "io.apicurio.registry.artifact.deleted.v1";

    /** Emitted when an artifact version is published (enabled). */
    public static final String VERSION_PUBLISHED = "io.apicurio.registry.artifact.version.published.v1";

    /** Emitted when an artifact version is deprecated. */
    public static final String VERSION_DEPRECATED = "io.apicurio.registry.artifact.version.deprecated.v1";

    /** Emitted when an artifact version lifecycle state changes. */
    public static final String VERSION_STATE_CHANGED = "io.apicurio.registry.artifact.version.state_changed.v1";

    /** Emitted when an artifact write is rejected by a registry rule. */
    public static final String RULE_VIOLATED = "io.apicurio.registry.rule.violated.v1";

    private static final Set<String> ALL = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            ARTIFACT_CREATED,
            ARTIFACT_UPDATED,
            ARTIFACT_DELETED,
            VERSION_PUBLISHED,
            VERSION_DEPRECATED,
            VERSION_STATE_CHANGED,
            RULE_VIOLATED)));

    private WebhookEventTypes() {
    }

    /**
     * @return an unmodifiable set of all supported webhook event type strings
     */
    public static Set<String> all() {
        return ALL;
    }

    /**
     * Validates that at least one event type is present and every type is supported.
     *
     * @param eventTypes the event types from a create or update request
     * @throws IllegalArgumentException if the list is null, empty, or contains an unknown type
     */
    public static void validate(List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IllegalArgumentException("At least one event type is required.");
        }
        for (String eventType : eventTypes) {
            if (!ALL.contains(eventType)) {
                throw new IllegalArgumentException("Unknown webhook event type: " + eventType);
            }
        }
    }
}
