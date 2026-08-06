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

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Resolves webhook hostnames to IP addresses for SSRF validation.
 * <p>
 * Production uses the JVM DNS resolver. Tests may substitute a custom implementation to simulate
 * DNS rebinding without changing production code paths.
 */
public interface WebhookHostnameResolver {

    /**
     * Resolves a webhook hostname to all A/AAAA records for denylist validation.
     *
     * @param hostname the hostname from a webhook URL (not a full URL)
     * @return all A/AAAA records for the hostname
     * @throws UnknownHostException when DNS resolution fails
     */
    InetAddress[] resolveAll(String hostname) throws UnknownHostException;

    /**
     * Default resolver backed by {@link InetAddress#getAllByName(String)}.
     */
    @ApplicationScoped
    class Default implements WebhookHostnameResolver {

        /**
         * Resolves the hostname using the JVM system DNS resolver.
         *
         * @param hostname the hostname from a webhook URL
         * @return all A/AAAA records returned by {@link InetAddress#getAllByName(String)}
         * @throws UnknownHostException when DNS resolution fails
         */
        @Override
        public InetAddress[] resolveAll(String hostname) throws UnknownHostException {
            return InetAddress.getAllByName(hostname);
        }
    }
}
