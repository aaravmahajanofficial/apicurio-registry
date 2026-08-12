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

import jakarta.ws.rs.BadRequestException;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Registration-time validation for webhook endpoint URLs.
 * <p>
 * Enforces scheme allowlisting, encoded-IP obfuscation checks, DNS resolution, and private IP
 * denylisting (Phase 2b). Validation failures return a generic message to avoid leaking internal
 * network details.
 */
public final class WebhookUrlValidator {

    /**
     * Generic client-facing error message for SSRF and scheme validation failures.
     * <p>
     * Intentionally vague to avoid leaking resolved IPs or DNS details to API callers.
     */
    static final String INVALID_URL_MESSAGE = "Invalid webhook URL";

    private static final Pattern DECIMAL_HOST = Pattern.compile("^\\d+$");
    private static final Pattern HEX_HOST = Pattern.compile("^0x[0-9a-fA-F]+$");
    private static final Pattern IPV4_HOST = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final Pattern OCTAL_IPV4_SEGMENT = Pattern.compile("^0\\d+$");

    private WebhookUrlValidator() {
    }

    /**
     * Validates a webhook subscription URL using the default JVM DNS resolver.
     *
     * @param url the endpoint URL from a create or update request
     * @param allowInsecureUrls when {@code true}, {@code http://} URLs are accepted in addition to
     *        {@code https://}
     * @param blockPrivateIps when {@code true}, reject URLs resolving to private or link-local IPs
     * @throws BadRequestException if the URL is missing, malformed, or fails SSRF checks
     */
    public static void validate(String url, boolean allowInsecureUrls, boolean blockPrivateIps) {
        validate(url, allowInsecureUrls, blockPrivateIps, new WebhookHostnameResolver.Default());
    }

    /**
     * Validates a webhook subscription URL with a pluggable hostname resolver (for tests).
     *
     * @param url the endpoint URL from a create or update request
     * @param allowInsecureUrls when {@code true}, {@code http://} URLs are accepted in addition to
     *        {@code https://}
     * @param blockPrivateIps when {@code true}, reject URLs resolving to private or link-local IPs
     * @param resolver resolves hostnames to IP addresses for denylist checks
     * @throws BadRequestException if the URL is missing, malformed, or fails SSRF checks
     */
    public static void validate(String url, boolean allowInsecureUrls, boolean blockPrivateIps,
            WebhookHostnameResolver resolver) {
        if (url == null || url.isBlank()) {
            throw new BadRequestException("Webhook URL is required.");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException ex) {
            throw invalidUrl();
        }
        validateScheme(uri, allowInsecureUrls);
        validateHostname(uri, blockPrivateIps, resolver);
    }

    /**
     * Rejects URLs whose scheme is not {@code https} (or {@code http} when insecure URLs are allowed).
     *
     * @param uri the parsed subscription URL
     * @param allowInsecureUrls whether {@code http://} is permitted
     * @throws BadRequestException when the scheme is missing or not allowed
     */
    private static void validateScheme(URI uri, boolean allowInsecureUrls) {
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw invalidUrl();
        }
        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if ("https".equals(normalizedScheme)) {
            return;
        }
        if (allowInsecureUrls && "http".equals(normalizedScheme)) {
            return;
        }
        throw invalidUrl();
    }

    /**
     * Validates the URL hostname for userinfo tricks, encoded IPs, and resolved private addresses.
     *
     * @param uri the parsed subscription URL
     * @param blockPrivateIps whether to reject hostnames resolving to private or link-local IPs
     * @param resolver resolves hostnames for DNS-based denylist checks
     * @throws BadRequestException when the hostname is invalid or blocked
     */
    private static void validateHostname(URI uri, boolean blockPrivateIps,
            WebhookHostnameResolver resolver) {
        if (uri.getUserInfo() != null) {
            throw invalidUrl();
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw invalidUrl();
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        rejectEncodedHostname(normalizedHost);
        if (blockPrivateIps) {
            rejectBlockedResolvedAddresses(normalizedHost, resolver);
        }
    }

    /**
     * Rejects hostnames that encode IP addresses as decimal, hex, or octal literals.
     *
     * @param host the normalized (lowercase) hostname from the URL
     * @throws BadRequestException when the hostname uses an encoded or literal blocked IP form
     */
    private static void rejectEncodedHostname(String host) {
        if (DECIMAL_HOST.matcher(host).matches()) {
            throw invalidUrl();
        }
        if (HEX_HOST.matcher(host).matches()) {
            throw invalidUrl();
        }
        if (IPV4_HOST.matcher(host).matches()) {
            rejectLiteralIpAddresses(host);
            if (hasOctalIpv4Segment(host)) {
                throw invalidUrl();
            }
        }
    }

    /**
     * Rejects literal IPv4 hostnames that parse to a blocked address (e.g. {@code 127.0.0.1}).
     *
     * @param host the IPv4 literal hostname from the URL
     * @throws BadRequestException when the literal IP is blocked or unparseable
     */
    private static void rejectLiteralIpAddresses(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (WebhookIpDenylist.isBlocked(address)) {
                throw invalidUrl();
            }
        } catch (UnknownHostException ex) {
            throw invalidUrl();
        }
    }

    /**
     * Detects octal-encoded IPv4 segments (e.g. {@code 0177.0.0.1}).
     *
     * @param host the IPv4 literal hostname from the URL
     * @return {@code true} when any dotted segment is octal-encoded
     */
    private static boolean hasOctalIpv4Segment(String host) {
        String[] segments = host.split("\\.");
        for (String segment : segments) {
            if (OCTAL_IPV4_SEGMENT.matcher(segment).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the hostname and rejects the URL when any A/AAAA record is on the denylist.
     *
     * @param host the normalized hostname from the URL
     * @param resolver resolves hostnames for denylist checks
     * @throws BadRequestException when resolution fails or any address is blocked
     */
    private static void rejectBlockedResolvedAddresses(String host, WebhookHostnameResolver resolver) {
        try {
            InetAddress[] addresses = resolver.resolveAll(host);
            for (InetAddress address : addresses) {
                if (WebhookIpDenylist.isBlocked(address)) {
                    throw invalidUrl();
                }
            }
        } catch (UnknownHostException ex) {
            throw invalidUrl();
        }
    }

    /**
     * Returns a generic {@link BadRequestException} for invalid webhook URLs.
     *
     * @return a {@link BadRequestException} with {@link #INVALID_URL_MESSAGE}
     */
    private static BadRequestException invalidUrl() {
        return new BadRequestException(INVALID_URL_MESSAGE);
    }
}
