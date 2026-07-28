package io.apicurio.registry.webhooks;

import org.apache.commons.codec.digest.DigestUtils;

import java.security.SecureRandom;

/**
 * Utilities for generating and hashing webhook signing secrets.
 * <p>
 * Secrets use the {@code whsec_} prefix (Stripe/GitHub pattern) and are returned in plaintext only
 * on subscription create. Only a SHA-256 hash is persisted in storage.
 */
public final class WebhookSecretUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private WebhookSecretUtil() {
    }

    /**
     * Generates a new webhook signing secret with 256 bits of entropy.
     *
     * @return a secret in the form {@code whsec_<64-hex-chars>}
     */
    public static String generateSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder("whsec_");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Computes the SHA-256 hex digest of a webhook secret for at-rest storage.
     *
     * @param secret the plaintext signing secret
     * @return the SHA-256 hex hash of the secret
     */
    public static String hashSecret(String secret) {
        return DigestUtils.sha256Hex(secret);
    }
}
