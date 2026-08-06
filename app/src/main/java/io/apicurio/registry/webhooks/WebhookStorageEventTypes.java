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

import io.apicurio.registry.storage.StorageEventType;

import java.util.Set;

/**
 * Registry {@link StorageEventType} values that may produce CloudEvents webhook notifications.
 */
public final class WebhookStorageEventTypes {

    private static final Set<StorageEventType> SUPPORTED = Set.of(
            StorageEventType.ARTIFACT_CREATED,
            StorageEventType.ARTIFACT_METADATA_UPDATED,
            StorageEventType.ARTIFACT_DELETED,
            StorageEventType.ARTIFACT_VERSION_CREATED,
            StorageEventType.ARTIFACT_VERSION_STATE_CHANGED);

    private WebhookStorageEventTypes() {
    }

    /**
     * @param storageEventType the {@link StorageEventType#name()} from an outbox event
     * @return {@code true} when fanout should process this storage event
     */
    public static boolean isSupported(String storageEventType) {
        if (storageEventType == null) {
            return false;
        }
        try {
            return SUPPORTED.contains(StorageEventType.valueOf(storageEventType));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
