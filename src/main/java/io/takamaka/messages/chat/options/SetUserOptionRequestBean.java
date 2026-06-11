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
package io.takamaka.messages.chat.options;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.chat.core.SignedMessageBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Signed envelope for a {@code setuseroption} write. Extends
 * {@link SignedMessageBean}; the signature covers {@code canonical(pl)}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SetUserOptionRequestBean extends SignedMessageBean {

    /**
     * Full-args constructor including the inherited envelope fields.
     *
     * @param pl the signed content
     * @param from sender's identity public key (Base64URL)
     * @param signature Ed25519 signature over {@code canonical(pl)} (Base64URL)
     * @param messageType message type identifier ({@code SET_USER_OPTION})
     * @param signatureType signature type (e.g., "Ed25519BC")
     */
    public SetUserOptionRequestBean(
            SetUserOptionSignedContentBean pl,
            String from,
            String signature,
            String messageType,
            String signatureType) {
        super(from, signature, messageType, signatureType);
        this.pl = pl;
    }

    /**
     * The signed content (sign unit).
     */
    @JsonProperty("pl")
    private SetUserOptionSignedContentBean pl;
}
