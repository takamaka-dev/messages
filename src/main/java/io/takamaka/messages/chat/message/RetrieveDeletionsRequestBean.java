/*
 * Copyright 2026 AiliA SA.
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
package io.takamaka.messages.chat.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.chat.core.SignedMessageBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Envelope for a signed {@code retrievedeletions} request (DR-025 catch-up). The Ed25519 {@code signature}
 * covers {@code canonical(pl)}; the server verifies it, checks the caller is a member of the named
 * conversation, and streams back the owner-signed delete envelopes recorded since the cursor.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RetrieveDeletionsRequestBean extends SignedMessageBean {

    /**
     * Full-args constructor including the inherited envelope fields.
     *
     * @param pl the signed content (sign unit)
     * @param from the caller's identity public key (= verify key)
     * @param signature Ed25519 over {@code canonical(pl)}
     * @param messageType {@code "RETRIEVE_DELETIONS"}
     * @param signatureType {@code "Ed25519BC"}
     */
    public RetrieveDeletionsRequestBean(
            RetrieveDeletionsSignedContentBean pl,
            String from,
            String signature,
            String messageType,
            String signatureType) {
        super(from, signature, messageType, signatureType);
        this.pl = pl;
    }

    /** The signed content (sign unit). */
    @JsonProperty("pl")
    private RetrieveDeletionsSignedContentBean pl;
}
