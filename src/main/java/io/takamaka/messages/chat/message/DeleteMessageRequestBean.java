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
 * Envelope for a signed {@code DELETE_MESSAGE} ("delete for everyone") command
 * (DR-025). The Ed25519 {@code signature} covers {@code canonical(pl)}. The
 * server verifies the signature, checks {@code owner == from} + membership +
 * the 2-day window (server clock vs the server-assigned target timestamp), then
 * tombstones the {@code messages} row, stores THIS signed envelope verbatim in
 * the deletion log (so any client can independently re-verify the deletion),
 * purges the owner's {@code target_efh} blobs, and fans out the delete.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeleteMessageRequestBean extends SignedMessageBean {

    /**
     * Full-args constructor including the inherited envelope fields.
     *
     * @param pl the signed content (sign unit)
     * @param from owner's identity public key (= verify key)
     * @param signature Ed25519 over {@code canonical(pl)}
     * @param messageType {@code "DELETE_MESSAGE"}
     * @param signatureType {@code "Ed25519BC"}
     */
    public DeleteMessageRequestBean(
            DeleteMessageSignedContentBean pl,
            String from,
            String signature,
            String messageType,
            String signatureType) {
        super(from, signature, messageType, signatureType);
        this.pl = pl;
    }

    /** The signed content (sign unit). */
    @JsonProperty("pl")
    private DeleteMessageSignedContentBean pl;
}
