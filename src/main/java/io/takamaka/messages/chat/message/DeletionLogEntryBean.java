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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of the DR-025 deletion log, as streamed by {@code retrievedeletions}.
 *
 * <p>The payload that matters is {@link #signedDeleteEnvelope} — the owner-signed
 * {@link DeleteMessageRequestBean} exactly as it was received, stored verbatim so a client can re-verify the
 * Ed25519 signature over {@code canonical(pl)} itself. <b>Verify it; do not trust this row.</b> Everything
 * else here (target, conversation, deleter, purged attachments) is also inside that envelope or derivable
 * from it — the flat fields are a convenience for cursoring and logging, and carry no authority. A relay
 * that alters them changes nothing a verifying client will act on.</p>
 *
 * <p>{@link #serverDeleteTime} is the cursor: pass the highest value seen back as {@code since} on the next
 * catch-up. It is server-assigned, so it orders the log but proves nothing by itself.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeletionLogEntryBean {

    /** The deleted message's signature (the deletion log's key). Authoritative copy is inside the envelope. */
    @JsonProperty("target_message_signature")
    private String targetMessageSignature;

    /** The conversation the deletion belongs to. Authoritative copy is inside the envelope. */
    @JsonProperty("conversation_hash_name")
    private String conversationHashName;

    /** Server-assigned tombstone time — the replay cursor. */
    @JsonProperty("server_delete_time")
    private Long serverDeleteTime;

    /** The owner-signed {@code DELETE_MESSAGE} envelope, VERBATIM. This is the only trustworthy field. */
    @JsonProperty("signed_delete_envelope")
    private String signedDeleteEnvelope;
}
