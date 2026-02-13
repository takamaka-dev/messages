/*
 * Copyright 2025 AiliA SA.
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
package io.takamaka.messages.chat.fcm;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.chat.core.NonceResponseBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contains the signed content for FCM token registration.
 * This content is signed with Ed25519 to authenticate the request.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FcmTokenRegistrationSignedContentBean {

    /**
     * Server nonce for replay protection.
     */
    private NonceResponseBean nonce;

    /**
     * The Firebase Cloud Messaging token from the device.
     */
    @JsonProperty("fcm_token")
    private String fcmToken;

    /**
     * Platform identifier (e.g., "android", "ios", "web").
     */
    private String platform;

    /**
     * Optional unique device identifier for multi-device support.
     */
    @JsonProperty("device_id")
    private String deviceId;
}
