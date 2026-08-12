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

import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RegistryException;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;

/**
 * Async HTTP client for webhook CloudEvent delivery.
 * <p>
 * Uses the shared Vert.x {@link WebClient} pool with redirects disabled, configurable timeout, SSRF
 * guard before connect, and HMAC payload signing.
 */
@ApplicationScoped
public class WebhookHttpClient {

    @Inject
    WebClient webClient;

    @Inject
    WebhookSsrfGuard ssrfGuard;

    @Inject
    WebhookSignatureService signatureService;

    @Inject
    WebhooksConfig webhooksConfig;

    /**
     * POSTs a signed JSON payload to a webhook endpoint.
     *
     * @param url the subscriber callback URL
     * @param secret the plaintext signing secret
     * @param body the JSON CloudEvents payload
     * @return a future completing with the HTTP response summary, or failing on SSRF/transport errors
     */
    public Future<WebhookHttpResponse> post(String url, String secret, String body) {
        try {
            ssrfGuard.validateBeforeConnect(url);
        } catch (WebhookSsrfException ex) {
            return Future.failedFuture(ex);
        }

        long timeoutMs = parseTimeoutMs(webhooksConfig.getDeliveryHttpTimeout());
        String signatureHeader = signatureService.sign(secret, body);

        return webClient.postAbs(url)
                .putHeader("Content-Type", ContentTypes.APPLICATION_JSON)
                .putHeader(WebhookSignatureService.SIGNATURE_HEADER, signatureHeader)
                .followRedirects(false)
                .timeout(timeoutMs)
                .sendBuffer(Buffer.buffer(body))
                .map(this::toResponse);
    }

    /**
     * Maps a Vert.x HTTP response to a delivery result or throws on non-2xx status.
     *
     * @param response the Vert.x HTTP response from the subscriber endpoint
     * @return a summary when the status code is 2xx
     * @throws WebhookDeliveryException when the subscriber returns a non-2xx status
     */
    private WebhookHttpResponse toResponse(HttpResponse<Buffer> response) {
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return new WebhookHttpResponse(statusCode, response.statusMessage());
        }
        throw new WebhookDeliveryException(
                "Webhook delivery failed with HTTP " + statusCode + ": " + response.statusMessage());
    }

    /**
     * Parses a webhook HTTP timeout configuration value into milliseconds.
     * <p>
     * Supports {@code ms}, {@code s}, and {@code m} suffixes; defaults to 15 seconds on parse errors.
     *
     * @param value the configured timeout string (e.g. {@code 15s})
     * @return timeout in milliseconds
     */
    private static long parseTimeoutMs(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ofSeconds(15).toMillis();
        }
        String trimmed = value.trim();
        try {
            if (trimmed.endsWith("ms")) {
                return Long.parseLong(trimmed.substring(0, trimmed.length() - 2));
            }
            if (trimmed.endsWith("s")) {
                return Long.parseLong(trimmed.substring(0, trimmed.length() - 1)) * 1000L;
            }
            if (trimmed.endsWith("m")) {
                return Long.parseLong(trimmed.substring(0, trimmed.length() - 1)) * 60_000L;
            }
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            return Duration.ofSeconds(15).toMillis();
        }
    }

    /**
     * Summary of a successful webhook HTTP response.
     *
     * @param statusCode HTTP status code
     * @param statusMessage HTTP status message
     */
    public record WebhookHttpResponse(int statusCode, String statusMessage) {
    }

    /**
     * Thrown when the subscriber returns a non-2xx HTTP status.
     */
    public static class WebhookDeliveryException extends RegistryException {

        private static final long serialVersionUID = 1L;

        /**
         * Creates an exception for a failed webhook delivery HTTP response.
         *
         * @param message a safe error summary including the HTTP status code
         */
        public WebhookDeliveryException(String message) {
            super(message);
        }
    }
}
