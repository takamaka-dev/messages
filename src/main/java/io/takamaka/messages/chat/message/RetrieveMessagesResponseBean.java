/*
 * Copyright 2024 AiliA SA.
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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One message delivered to a subscriber, either live or from history.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RetrieveMessagesResponseBean {

    /**
     * The Ed25519 signature of the MESSAGE this bean carries — the value the
     * server stored as the message's primary key, taken from the signed
     * envelope inside {@link #messageJson}.
     * <p>
     * This is the field a client should key local storage on. It is stable
     * across every delivery of the same message, on every path.
     * <p>
     * Added 2026-08-12 because {@link #requestSignature} could not serve that
     * purpose: it carried the message signature on the live fan-out and the
     * CALLER'S OWN request signature on history, so a client keying on it
     * minted a fresh identity per fetch (see
     * {@code testing/ATTACHMENT_MATRIX_2026-08-12_FINDINGS.md} A4).
     */
    @JsonProperty("message_signature")
    private String messageSignature;

    /**
     * @deprecated Ambiguous by construction — historically the message
     * signature on the live path and the request's own signature on history.
     * Both paths now emit the MESSAGE signature here so existing clients are
     * correct, but new code must read {@link #messageSignature}: this field
     * cannot be given an honest name and is kept only so deployed clients do
     * not break.
     */
    @Deprecated
    @JsonProperty("request_signature")
    private String requestSignature;

    @JsonProperty("reception_timestamp")
    private Long receptionTimestamp;

    @JsonProperty("message_json")
    private String messageJson;

    /**
     * Canonical constructor for a delivered message. Sets both signature fields
     * from the same value, which is the only combination that is not a lie.
     *
     * @param messageSignature the signature of the message being delivered
     * @param receptionTimestamp server-side timestamp
     * @param messageJson the signed envelope
     */
    public RetrieveMessagesResponseBean(String messageSignature, Long receptionTimestamp, String messageJson) {
        this.messageSignature = messageSignature;
        this.requestSignature = messageSignature;
        this.receptionTimestamp = receptionTimestamp;
        this.messageJson = messageJson;
    }
}
