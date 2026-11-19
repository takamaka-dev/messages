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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.chat.core.NonceResponseBean;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contains the signed content for FCM token registration.
 * This content is signed with Ed25519 to authenticate the request.
 *
 * <p><b>Null handling is per field and load-bearing.</b> The server verifies by re-serialising the PARSED bean
 * through a mapper that includes nulls, so every member's null form is part of the signed bytes:
 * {@code device_id} is signed as {@code "device_id":null} when absent (pinned by {@code FcmCanonicalVectorTest}),
 * while {@code cap} is OMITTED when null or empty ({@code NON_EMPTY} on that field only). Never put
 * {@code @JsonInclude} on the class: it would drop the {@code "device_id":null} today's clients sign.</p>
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

    /**
     * DR-074 amendment 1: the capabilities this install declares for this token ({@link FcmCapabilities}).
     * Absent = none. Canonical form: 1 to 8 values, each {@code [a-z0-9-]{1,32}}, strictly ascending, so the
     * server refuses ({@code INVALID_CAP}) rather than normalising. Only {@code registerfcmtoken} reads it.
     */
    @JsonProperty("cap")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)   // on THIS FIELD ONLY, never on the class (see the class doc)
    private List<String> cap;

    /**
     * The registration without {@code cap}: its canonical form is byte-identical to the content signed before
     * DR-074 amendment 1.
     */
    public FcmTokenRegistrationSignedContentBean(NonceResponseBean nonce, String fcmToken, String platform, String deviceId) {
        this(nonce, fcmToken, platform, deviceId, null);
    }
}
