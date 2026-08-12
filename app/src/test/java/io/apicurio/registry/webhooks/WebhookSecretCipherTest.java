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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

class WebhookSecretCipherTest {

    private WebhookSecretCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new WebhookSecretCipher();
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }
        cipher.encryptionKey = Base64.getEncoder().encodeToString(key);
    }

    @Test
    void encryptDecryptRoundTrip() {
        String secret = "whsec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        String encrypted = cipher.encrypt(secret);
        assertNotEquals(secret, encrypted);
        assertEquals(secret, cipher.decrypt(encrypted));
    }

    @Test
    void rejectsMissingKey() {
        cipher.encryptionKey = "";
        assertThrows(WebhookSecretCipher.WebhookSecretCipherException.class,
                () -> cipher.encrypt("whsec_test"));
    }

    @Test
    void rejectsInvalidKeyLength() {
        cipher.encryptionKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(WebhookSecretCipher.WebhookSecretCipherException.class,
                () -> cipher.encrypt("whsec_test"));
    }

    @Test
    void isConfiguredWhenKeyValid() {
        assertTrue(cipher.isConfigured());
    }
}
