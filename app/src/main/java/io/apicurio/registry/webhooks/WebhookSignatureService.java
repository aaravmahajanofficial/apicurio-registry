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

import jakarta.enterprise.context.ApplicationScoped;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HMAC-SHA256 signing for outbound webhook CloudEvents payloads.
 * <p>
 * Signature header format: {@code X-Apicurio-Webhook-Signature: t=<unix>,v1=<hex>} where {@code v1}
 * is HMAC-SHA256 over {@code timestamp + "." + body}. Subscribers should reject timestamps outside a
 * short replay window (default 5 minutes).
 */
@ApplicationScoped
public class WebhookSignatureService {

    /** HTTP header name for the HMAC webhook signature ({@code t=<unix>,v1=<hex>}). */
    public static final String SIGNATURE_HEADER = "X-Apicurio-Webhook-Signature";

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Duration DEFAULT_REPLAY_TOLERANCE = Duration.ofMinutes(5);
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile(
            "t=(\\d+),v1=([0-9a-fA-F]+)");

    /**
     * Signs a webhook payload and returns the {@link #SIGNATURE_HEADER} value.
     *
     * @param secret the plaintext signing secret (never logged)
     * @param body the JSON payload body
     * @return header value in the form {@code t=<unix>,v1=<hex>}
     */
    public String sign(String secret, String body) {
        long timestamp = Instant.now().getEpochSecond();
        String signature = computeSignature(secret, timestamp, body);
        return "t=" + timestamp + ",v1=" + signature;
    }

    /**
     * Verifies a webhook signature header against the payload body.
     *
     * @param secret the plaintext signing secret
     * @param body the JSON payload body
     * @param signatureHeader the {@link #SIGNATURE_HEADER} value
     * @return {@code true} when the signature is valid and the timestamp is within tolerance
     */
    public boolean verify(String secret, String body, String signatureHeader) {
        return verify(secret, body, signatureHeader, DEFAULT_REPLAY_TOLERANCE);
    }

    /**
     * Verifies a webhook signature header with a custom replay tolerance.
     *
     * @param secret the plaintext signing secret
     * @param body the JSON payload body
     * @param signatureHeader the {@link #SIGNATURE_HEADER} value
     * @param replayTolerance maximum age of the signature timestamp
     * @return {@code true} when the signature is valid and the timestamp is within tolerance
     */
    public boolean verify(String secret, String body, String signatureHeader, Duration replayTolerance) {
        if (secret == null || body == null || signatureHeader == null) {
            return false;
        }
        Matcher matcher = SIGNATURE_PATTERN.matcher(signatureHeader.trim());
        if (!matcher.matches()) {
            return false;
        }
        long timestamp = Long.parseLong(matcher.group(1));
        String providedSignature = matcher.group(2).toLowerCase(Locale.ROOT);
        long now = Instant.now().getEpochSecond();
        if (timestamp > now + replayTolerance.getSeconds()) {
            return false;
        }
        if (now - timestamp > replayTolerance.getSeconds()) {
            return false;
        }
        String expected = computeSignature(secret, timestamp, body);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes the HMAC-SHA256 hex digest for a signed payload.
     *
     * @param secret the plaintext signing secret
     * @param timestamp Unix epoch seconds included in the signed payload prefix
     * @param body the JSON payload body
     * @return lowercase hex-encoded HMAC-SHA256 digest
     */
    private static String computeSignature(String secret, long timestamp, String body) {
        String payload = timestamp + "." + body;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("Failed to compute webhook HMAC signature", ex);
        }
    }

    /**
     * Encodes a byte array as lowercase hexadecimal.
     *
     * @param bytes the digest bytes to encode
     * @return lowercase hex string with no separators
     */
    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
