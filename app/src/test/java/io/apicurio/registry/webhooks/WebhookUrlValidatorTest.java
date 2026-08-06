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
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebhookUrlValidatorTest {

    @Test
    void acceptsHttpsPublicHostname() {
        WebhookUrlValidator.validate("https://example.com/hooks/apicurio", false, true,
                hostname -> new InetAddress[] { InetAddress.getByName("8.8.8.8") });
    }

    @Test
    void rejectsHttpWhenInsecureNotAllowed() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> WebhookUrlValidator.validate("http://example.com/hook", false, true,
                        hostname -> new InetAddress[] { InetAddress.getByName("8.8.8.8") }));
        assertEquals(WebhookUrlValidator.INVALID_URL_MESSAGE, ex.getMessage());
    }

    @Test
    void acceptsHttpWhenInsecureAllowed() {
        WebhookUrlValidator.validate("http://example.com/hook", true, true,
                hostname -> new InetAddress[] { InetAddress.getByName("8.8.8.8") });
    }

    @Test
    void rejectsLiteralPrivateIpv4() {
        assertThrows(BadRequestException.class,
                () -> WebhookUrlValidator.validate("https://127.0.0.1/hook", false, true,
                        hostname -> new InetAddress[] { InetAddress.getByName("127.0.0.1") }));
        assertThrows(BadRequestException.class,
                () -> WebhookUrlValidator.validate("https://10.0.0.1/hook", false, true,
                        hostname -> new InetAddress[] { InetAddress.getByName("10.0.0.1") }));
        assertThrows(BadRequestException.class,
                () -> WebhookUrlValidator.validate("https://169.254.169.254/metadata", false, true,
                        hostname -> new InetAddress[] { InetAddress.getByName("169.254.169.254") }));
    }

    @Test
    void rejectsHostnameResolvingToPrivateIp() {
        assertThrows(BadRequestException.class,
                () -> WebhookUrlValidator.validate("https://metadata.example/hook", false, true,
                        hostname -> new InetAddress[] { InetAddress.getByName("10.0.0.5") }));
    }

    @Test
    void rejectsDecimalEncodedHostname() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> WebhookUrlValidator.validate("https://2130706433/hook", false, true,
                        hostname -> new InetAddress[] { InetAddress.getByName("127.0.0.1") }));
        assertEquals(WebhookUrlValidator.INVALID_URL_MESSAGE, ex.getMessage());
    }

    @Test
    void rejectsHexEncodedHostname() {
        assertThrows(BadRequestException.class,
                () -> WebhookUrlValidator.validate("https://0x7f000001/hook", false, true,
                        hostname -> new InetAddress[] { InetAddress.getByName("127.0.0.1") }));
    }

    @Test
    void rejectsUserinfoInUrl() {
        assertThrows(BadRequestException.class,
                () -> WebhookUrlValidator.validate("https://user@example.com/hook", false, true,
                        hostname -> new InetAddress[] { InetAddress.getByName("8.8.8.8") }));
    }

    @Test
    void skipsDnsDenylistWhenBlockPrivateIpsDisabled() throws UnknownHostException {
        WebhookUrlValidator.validate("https://internal.example/hook", false, false,
                hostname -> new InetAddress[] { InetAddress.getByName("10.0.0.1") });
    }
}
