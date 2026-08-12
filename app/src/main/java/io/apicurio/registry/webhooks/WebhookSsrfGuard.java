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
import jakarta.inject.Inject;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Delivery-time SSRF guard that re-resolves webhook hostnames before each HTTP connect.
 * <p>
 * Protects against DNS rebinding (TOCTOU) between registration and delivery by applying the same
 * IP denylist as {@link WebhookUrlValidator} on freshly resolved A/AAAA records.
 */
@ApplicationScoped
public class WebhookSsrfGuard {

    @Inject
    WebhooksConfig webhooksConfig;

    @Inject
    WebhookHostnameResolver hostnameResolver;

    /**
     * Re-resolves the webhook URL hostname and rejects the connect when any resolved IP is blocked.
     *
     * @param url the full webhook endpoint URL
     * @throws WebhookSsrfException when the URL is malformed or resolves to a blocked address
     */
    public void validateBeforeConnect(String url) {
        if (!webhooksConfig.isBlockPrivateIps()) {
            return;
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException ex) {
            throw new WebhookSsrfException();
        }
        String host = uri.getHost();
        if (host == null || host.isBlank() || uri.getUserInfo() != null) {
            throw new WebhookSsrfException();
        }
        try {
            InetAddress[] addresses = hostnameResolver.resolveAll(host);
            for (InetAddress address : addresses) {
                if (WebhookIpDenylist.isBlocked(address)) {
                    throw new WebhookSsrfException();
                }
            }
        } catch (UnknownHostException ex) {
            throw new WebhookSsrfException();
        }
    }
}
