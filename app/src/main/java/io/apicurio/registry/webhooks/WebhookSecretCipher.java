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

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.types.RegistryException;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_REST;

/**
 * AES-256-GCM encryption for webhook signing secrets at rest.
 * <p>
 * Plaintext secrets are returned only on subscription create; {@link #encrypt} stores an opaque
 * blob in {@code webhook_subscriptions.secretEncrypted} for delivery-time HMAC signing.
 */
@ApplicationScoped
public class WebhookSecretCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_LENGTH = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    @ConfigProperty(name = "apicurio.webhooks.secrets.encryption-key", defaultValue = "")
    @Info(category = CATEGORY_REST, description = "Base64-encoded 256-bit AES key for encrypting webhook signing secrets at rest", availableSince = "3.3.0", experimental = true)
    String encryptionKey;

    /**
     * Encrypts a plaintext signing secret for database storage.
     *
     * @param plaintext the subscription signing secret
     * @return base64-encoded {@code IV || ciphertext}
     * @throws WebhookSecretCipherException when the encryption key is missing or invalid
     */
    public String encrypt(String plaintext) {
        requireKey();
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, decodeKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception ex) {
            throw new WebhookSecretCipherException("Failed to encrypt webhook signing secret", ex);
        }
    }

    /**
     * Decrypts a stored signing secret for outbound delivery signing.
     *
     * @param encrypted base64-encoded {@code IV || ciphertext} from {@link #encrypt}
     * @return the plaintext signing secret
     * @throws WebhookSecretCipherException when decryption fails or the key is missing
     */
    public String decrypt(String encrypted) {
        requireKey();
        if (encrypted == null || encrypted.isBlank()) {
            throw new WebhookSecretCipherException("Webhook signing secret is not available for delivery");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            if (decoded.length <= GCM_IV_LENGTH) {
                throw new WebhookSecretCipherException("Encrypted webhook secret payload is too short");
            }
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, decodeKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (WebhookSecretCipherException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new WebhookSecretCipherException("Failed to decrypt webhook signing secret", ex);
        }
    }

    /**
     * @return {@code true} when a valid encryption key is configured
     */
    public boolean isConfigured() {
        return encryptionKey != null && !encryptionKey.isBlank() && decodeKeySilently() != null;
    }

    private void requireKey() {
        if (!isConfigured()) {
            throw new WebhookSecretCipherException(
                    "apicurio.webhooks.secrets.encryption-key must be set to a base64-encoded 256-bit key");
        }
    }

    private SecretKey decodeKey() {
        SecretKey key = decodeKeySilently();
        if (key == null) {
            throw new WebhookSecretCipherException(
                    "apicurio.webhooks.secrets.encryption-key must decode to 32 bytes");
        }
        return key;
    }

    private SecretKey decodeKeySilently() {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            return null;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKey.trim());
            if (keyBytes.length != KEY_LENGTH) {
                return null;
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Thrown when webhook secret encryption or decryption fails.
     */
    public static class WebhookSecretCipherException extends RegistryException {

        private static final long serialVersionUID = 1L;

        /**
         * @param message safe error message
         */
        public WebhookSecretCipherException(String message) {
            super(message);
        }

        /**
         * @param message safe error message
         * @param cause underlying failure
         */
        public WebhookSecretCipherException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
