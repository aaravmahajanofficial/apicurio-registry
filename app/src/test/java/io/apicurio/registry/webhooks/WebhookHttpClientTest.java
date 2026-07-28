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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.util.concurrent.ExecutionException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebhookHttpClientTest {

    private Vertx vertx;
    private WebClient webClient;
    private WireMockServer wireMockServer;
    private WebhookHttpClient httpClient;
    private WebhookSsrfGuard ssrfGuard;
    private WebhooksConfig webhooksConfig;
    private WebhookHostnameResolver hostnameResolver;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        webClient = WebClient.create(vertx);
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        webhooksConfig = mock(WebhooksConfig.class);
        when(webhooksConfig.isBlockPrivateIps()).thenReturn(true);
        when(webhooksConfig.getDeliveryHttpTimeout()).thenReturn("15s");

        hostnameResolver = mock(WebhookHostnameResolver.class);

        ssrfGuard = new WebhookSsrfGuard();
        setField(ssrfGuard, "webhooksConfig", webhooksConfig);
        setField(ssrfGuard, "hostnameResolver", hostnameResolver);

        httpClient = new WebhookHttpClient();
        setField(httpClient, "webClient", webClient);
        setField(httpClient, "ssrfGuard", ssrfGuard);
        setField(httpClient, "signatureService", new WebhookSignatureService());
        setField(httpClient, "webhooksConfig", webhooksConfig);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
        if (webClient != null) {
            webClient.close();
        }
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    void deliversSignedPayloadOnSuccess() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        String host = "localhost";
        when(hostnameResolver.resolveAll(host))
                .thenReturn(new InetAddress[] { InetAddress.getByName("8.8.8.8") });

        String url = "http://" + host + ":" + wireMockServer.port() + "/hook";
        String secret = "whsec_delivery_test_secret";
        String body = "{\"specversion\":\"1.0\",\"id\":\"evt-42\"}";

        WebhookHttpClient.WebhookHttpResponse response = httpClient.post(url, secret, body).toCompletionStage()
                .toCompletableFuture().get();
        assertEquals(200, response.statusCode());

        String signatureHeader = wireMockServer.getServeEvents().get(0).getRequest()
                .getHeader(WebhookSignatureService.SIGNATURE_HEADER);
        assertTrue(new WebhookSignatureService().verify(secret, body, signatureHeader));
    }

    @Test
    void failsWhenRedirectWouldReachPrivateHost() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/hook"))
                .willReturn(aResponse().withStatus(302).withHeader("Location", "http://127.0.0.1/evil")));

        String host = "localhost";
        when(hostnameResolver.resolveAll(host))
                .thenReturn(new InetAddress[] { InetAddress.getByName("8.8.8.8") });

        String url = "http://" + host + ":" + wireMockServer.port() + "/hook";
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> httpClient.post(url, "whsec_x", "{}").toCompletionStage().toCompletableFuture().get());
        assertInstanceOf(WebhookHttpClient.WebhookDeliveryException.class, ex.getCause());
    }

    @Test
    void failsWhenDnsRebindsToPrivateIpAtDelivery() throws Exception {
        when(hostnameResolver.resolveAll("public.example"))
                .thenReturn(new InetAddress[] { InetAddress.getByName("10.0.0.9") });

        wireMockServer.stubFor(post(anyUrl()).willReturn(aResponse().withStatus(200)));

        String url = "http://public.example/hook";
        ExecutionException ex = assertThrows(ExecutionException.class,
                () -> httpClient.post(url, "whsec_x", "{}").toCompletionStage().toCompletableFuture().get());
        assertInstanceOf(WebhookSsrfException.class, ex.getCause());
        assertEquals(0, wireMockServer.getServeEvents().size());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
