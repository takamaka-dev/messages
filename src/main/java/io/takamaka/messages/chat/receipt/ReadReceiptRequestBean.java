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
package io.takamaka.messages.chat.receipt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.chat.core.SignedMessageBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Envelope-conformant read receipt (READ_RECEIPT_DESIGN.md §6, D2/D10). The
 * Ed25519 {@code signature} covers {@code canonical(pl)}; {@code ts} is set by
 * the server at fan-out and is EXCLUDED from the signature (it is outside
 * {@code pl}).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReadReceiptRequestBean extends SignedMessageBean {

    /**
     * Full-args constructor including the inherited envelope fields (no
     * server timestamp — that is decorated later).
     *
     * @param pl the signed content
     * @param from reader's identity public key (= verify key)
     * @param signature Ed25519 over {@code canonical(pl)}
     * @param messageType {@code "READ_RECEIPT"}
     * @param signatureType {@code "Ed25519BC"}
     */
    public ReadReceiptRequestBean(
            ReadReceiptSignedContentBean pl,
            String from,
            String signature,
            String messageType,
            String signatureType) {
        super(from, signature, messageType, signatureType);
        this.pl = pl;
    }

    /** The signed content (sign unit). */
    @JsonProperty("pl")
    private ReadReceiptSignedContentBean pl;

    /** Server reception time, set at fan-out. EXCLUDED from the signature. */
    @JsonProperty("ts")
    private Long serverTimestamp;
}
