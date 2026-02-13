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
import io.takamaka.messages.chat.core.SignedMessageBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Request bean for registering an FCM token for push notifications.
 * Extends SignedMessageBean to include Ed25519 signature for authentication.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FcmTokenRegistrationRequestBean extends SignedMessageBean {

    /**
     * Constructor with all fields including parent class fields.
     *
     * @param fcmTokenRegistrationSignedContentBean the signed content
     * @param from sender's public key (Base64URL)
     * @param signature Ed25519 signature (Base64URL)
     * @param messageType message type identifier
     * @param signatureType signature type (e.g., "Ed25519BC")
     */
    public FcmTokenRegistrationRequestBean(
            FcmTokenRegistrationSignedContentBean fcmTokenRegistrationSignedContentBean,
            String from,
            String signature,
            String messageType,
            String signatureType) {
        super(from, signature, messageType, signatureType);
        this.fcmTokenRegistrationSignedContentBean = fcmTokenRegistrationSignedContentBean;
    }

    /**
     * The signed content containing FCM token and device information.
     */
    @JsonProperty("fcm_token_registration_signed_content")
    private FcmTokenRegistrationSignedContentBean fcmTokenRegistrationSignedContentBean;
}
