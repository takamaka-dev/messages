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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.extra.beans.EncMessageBean;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The signed content ({@code pl}) of a server-side {@code DELETE_MESSAGE}
 * ("delete for everyone") command — {@code canonical(pl)} is what the envelope
 * signature covers (DR-025). All fields are <b>cleartext</b>: the server needs
 * them to authorize and execute the purge without reading message content.
 *
 * <p><b>Ownership + window are enforced against server-held state, not this
 * bean.</b> The server checks {@code owner == verified from} against
 * {@code messages.user_public_key} (and, per {@code target_efh},
 * {@code verified_attachment.sender_pk}), and enforces the 2-day window against
 * the server-assigned {@code messages.message_timestamp} — NOT against
 * {@code client_ts}, which is advisory freshness / anti-replay symmetry only.
 * The idempotency / anti-replay key is {@code target_message_signature} (the
 * globally-unique {@code messages} PK).</p>
 *
 * <p>{@code target_efh} is the OWNER-supplied list of attachment content
 * addresses ({@code encrypted_file_hash}) to purge alongside the message — the
 * server cannot derive it (the efh lives only inside the E2E-encrypted body).
 * Empty/absent when the message carries no attachments. {@code @JsonInclude
 * (NON_EMPTY)} keeps null/empty {@code target_efh}/{@code reason} out of the
 * signed canonical form symmetrically on sign and verify;
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} is additive forward-compat.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeleteMessageSignedContentBean {

    /** Conversation hash — binds roster + routing + membership check. */
    @JsonProperty("conversation_hash_name")
    private String conversationHashName;

    /** The {@code messages.message_signature} of the message to delete (the target). */
    @JsonProperty("target_message_signature")
    private String targetMessageSignature;

    /**
     * Owner-supplied cleartext list of attachment {@code encrypted_file_hash}
     * (efh) to purge with the message. Empty/absent for a text-only message.
     */
    @JsonProperty("target_efh")
    private List<String> targetEncryptedFileHashes;

    /**
     * Client-authored timestamp — advisory freshness / anti-replay symmetry
     * ONLY. NEVER the window input (the server uses its own clock vs the
     * server-assigned {@code messages.message_timestamp}).
     */
    @JsonProperty("client_ts")
    private Long clientTimestamp;

    /**
     * Optional reason, ENCRYPTED under the conversation symmetric key.
     *
     * <p><b>Why this is not a String.</b> Every other field in this bean is a protocol identifier the server
     * MUST read to authorize and execute the purge, which is why they are cleartext. The reason never was:
     * {@code MessageDeleteService} does not read it, it exists purely so members can see why something was
     * removed. It was a plaintext String until 2026-07-29, which put user-authored text on a
     * zero-knowledge relay — a direct breach of "E2E encryption is always on: no plaintext data transmitted
     * to server", and a field's home (a cleartext payload) had silently decided its confidentiality.</p>
     *
     * <p>The leak also inverted the feature it belonged to: a deletion reason tends to DESCRIBE the message
     * being deleted ("wrong chat, that had the address"), so delete purged the ciphertext while leaving a
     * plaintext description of what was purged, outliving the content it described.</p>
     *
     * <p>This follows the precedent the protocol already set for the same field elsewhere:
     * {@code getRedactMessageBean} carries its reason inside the encrypted body, and even
     * {@code SignedContentTopicBean.topicDescription} is an {@link EncMessageBean} rather than a String.</p>
     *
     * <p>Encrypted with scope {@code DELETE_MESSAGE} (domain separation — a delete-reason ciphertext must not
     * be interchangeable with a message body) and {@code EncryptionContext.v0_1_a}. Members decrypt with the
     * conversation key; the relay sees an opaque blob it has no use for.</p>
     */
    @JsonProperty("reason")
    private EncMessageBean reason;
}
