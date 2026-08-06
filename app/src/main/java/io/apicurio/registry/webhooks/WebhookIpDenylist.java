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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Shared private/link-local IP denylist for webhook endpoint URLs.
 * <p>
 * Used at subscription registration ({@link WebhookUrlValidator}) and before each outbound delivery
 * ({@link WebhookSsrfGuard}) to block SSRF to internal networks and cloud metadata endpoints.
 */
public final class WebhookIpDenylist {

    private WebhookIpDenylist() {
    }

    /**
     * Determines whether a resolved IP address is blocked by the webhook SSRF denylist.
     *
     * @param address a resolved IP address for the webhook hostname
     * @return {@code true} when the address is in a blocked range (RFC 1918, loopback, link-local,
     *         metadata, IPv6 ULA/link-local)
     */
    public static boolean isBlocked(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
            return true;
        }
        if (address instanceof Inet4Address) {
            return isBlockedIPv4(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            return isBlockedIPv6(address.getAddress());
        }
        return false;
    }

    /**
     * Checks whether an IPv4 address falls into a blocked private, loopback, or link-local range.
     *
     * @param bytes the four-byte IPv4 address
     * @return {@code true} when the address is blocked
     */
    private static boolean isBlockedIPv4(byte[] bytes) {
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        if (b0 == 10) {
            return true;
        }
        if (b0 == 172 && b1 >= 16 && b1 <= 31) {
            return true;
        }
        if (b0 == 192 && b1 == 168) {
            return true;
        }
        if (b0 == 127) {
            return true;
        }
        if (b0 == 169 && b1 == 254) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether an IPv6 address falls into a blocked ULA or link-local range.
     *
     * @param bytes the sixteen-byte IPv6 address
     * @return {@code true} when the address is blocked
     */
    private static boolean isBlockedIPv6(byte[] bytes) {
        if ((bytes[0] & 0xFF) == 0xfe && (bytes[1] & 0xC0) == 0x80) {
            return true;
        }
        if ((bytes[0] & 0xFE) == 0xFC) {
            return true;
        }
        return false;
    }
}
