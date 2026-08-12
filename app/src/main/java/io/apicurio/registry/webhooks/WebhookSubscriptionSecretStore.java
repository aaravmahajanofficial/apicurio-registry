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

import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.WebhookSubscriptionDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Resolves plaintext signing secrets for webhook delivery by decrypting {@code secretEncrypted}.
 */
@ApplicationScoped
public class WebhookSubscriptionSecretStore {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    WebhookSecretCipher secretCipher;

    /**
     * Loads and decrypts the signing secret for a subscription.
     *
     * @param subscriptionId the subscription identifier
     * @return the plaintext signing secret for HMAC signing
     * @throws WebhookSecretCipher.WebhookSecretCipherException when the secret cannot be resolved
     */
    public String getSigningSecret(String subscriptionId) {
        WebhookSubscriptionDto subscription = storage.getWebhookSubscription(subscriptionId);
        return secretCipher.decrypt(subscription.getSecretEncrypted());
    }
}
