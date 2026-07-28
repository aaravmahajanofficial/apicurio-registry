package io.apicurio.registry.webhooks;

import jakarta.ws.rs.BadRequestException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Registration-time validation for webhook endpoint URLs.
 * <p>
 * Phase 2 enforces scheme allowlisting only ({@code https://}, or {@code http://} when insecure
 * URLs are explicitly permitted). SSRF protections (DNS resolution, private IP blocking) are
 * added in Phase 2b.
 */
public final class WebhookUrlValidator {

    private WebhookUrlValidator() {
    }

    /**
     * Validates a webhook subscription URL.
     *
     * @param url the endpoint URL from a create or update request
     * @param allowInsecureUrls when {@code true}, {@code http://} URLs are accepted in addition to
     *        {@code https://}
     * @throws BadRequestException if the URL is missing, malformed, or uses a disallowed scheme
     */
    public static void validate(String url, boolean allowInsecureUrls) {
        if (url == null || url.isBlank()) {
            throw new BadRequestException("Webhook URL is required.");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException ex) {
            throw new BadRequestException("Webhook URL is not valid.");
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new BadRequestException("Webhook URL must include a scheme (https://).");
        }
        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if ("https".equals(normalizedScheme)) {
            return;
        }
        if (allowInsecureUrls && "http".equals(normalizedScheme)) {
            return;
        }
        throw new BadRequestException("Webhook URL must use https://.");
    }
}
