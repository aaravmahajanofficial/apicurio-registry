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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WebhookDeliveryBackoffTest {

  private WebhookDeliveryBackoff backoff;
  private WebhooksConfig webhooksConfig;

  @BeforeEach
  void setUp() {
    webhooksConfig = Mockito.mock(WebhooksConfig.class);
    when(webhooksConfig.getDeliveryInitialDelay()).thenReturn("1s");
    when(webhooksConfig.getDeliveryMaxDelay()).thenReturn("5m");
    when(webhooksConfig.getDeliveryBackoffMultiplier()).thenReturn(2.0);

    backoff = new WebhookDeliveryBackoff();
    backoff.webhooksConfig = webhooksConfig;
  }

  @Test
  void baseDelayForFirstFailureIsOneSecond() {
    assertEquals(1000L, backoff.computeBaseDelayMs(0));
  }

  @Test
  void baseDelayDoublesEachAttempt() {
    assertEquals(2000L, backoff.computeBaseDelayMs(1));
    assertEquals(4000L, backoff.computeBaseDelayMs(2));
  }

  @Test
  void baseDelayCapsAtMaxDelay() {
    assertEquals(300_000L, backoff.computeBaseDelayMs(20));
  }

  @Test
  void jitterIsWithinTwentyFivePercentOfBase() {
    long base = backoff.computeBaseDelayMs(3);
    for (int i = 0; i < 50; i++) {
      long jitter = backoff.computeJitterMs(base);
      assertTrue(jitter >= 0);
      assertTrue(jitter <= base * 0.25 + 1);
    }
  }

  @Test
  void parseDurationMsSupportsSuffixes() {
    assertEquals(1500L, WebhookDeliveryBackoff.parseDurationMs("1500ms", 0));
    assertEquals(5000L, WebhookDeliveryBackoff.parseDurationMs("5s", 0));
    assertEquals(120_000L, WebhookDeliveryBackoff.parseDurationMs("2m", 0));
  }

  @Test
  void nextAttemptOnIsInFuture() {
    long before = System.currentTimeMillis();
    long next = backoff.computeNextAttemptOn(0).getTime();
    assertTrue(next >= before + 1000L);
  }
}
