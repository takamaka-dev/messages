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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The signed content ({@code pl}) of a read receipt — {@code canonical(pl)} is
 * what the envelope {@code signature} covers (READ_RECEIPT_DESIGN.md §6, D4).
 *
 * <p>The watermark (the last-read {@code message_signature}) is AES-CBC encrypted
 * under the conversation key with scope {@code "READ_RECEIPT"} (domain-separated
 * from messages), so the server learns <em>that</em> X read in conversation C,
 * never <em>which</em> message. {@code @JsonIgnoreProperties} is REQUIRED for
 * additive forward-compat (D7/E2): older peers ignore unknown future fields and
 * fall back to the {@code pv} gate instead of hard-failing the parse.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReadReceiptSignedContentBean {

    /** Conversation hash — binds roster + routing. */
    @JsonProperty("conv")
    private String conversationHashName;

    /** b64 AES-CBC ciphertext of the last-read message signature ({@code em[1]}). */
    @JsonProperty("enc")
    private String encryptedWatermark;

    /** b64 random per-receipt IV ({@code em[0]}). */
    @JsonProperty("iv")
    private String iv;

    /** Protocol/format version, "major.minor" (e.g. "1.0") — forward-compat gate (D7). */
    @JsonProperty("pv")
    private String protocolVersion;

    /** Cipher version (e.g. "v0_1_a") — resolves crypto params (DR-006 §P9 model-b). */
    @JsonProperty("v")
    private String cipherVersion;
}
