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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.chat.attachment.ChatMediaPlaceholderBean;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BasicMessageEncryptedContentBean {

    @JsonProperty("text_message")
    private String textMessage;
    @JsonProperty("attached_media")
    private List<ChatMediaPlaceholderBean> attachedMedia;
    @JsonProperty("action")
    private String action;
    @JsonProperty("targets")
    private List<String> targets;
    /**
     * Cross-conversation forward — recursive structured-claim payload.
     * Populated by the forwarder with the source message's decrypted bean
     * (attachment refs rewritten for the destination conversation). The
     * source signature is NOT preserved; this carries plaintext content
     * the forwarder is structurally vouching for. Recursive: a forwarded
     * forward carries its own fw_content. Hard depth cap = 10.
     * <p>See spec §12.10 for the full design and §12.10.5 for the
     * deliberately-minimal stance.
     */
    @JsonProperty("fw_content")
    private BasicMessageEncryptedContentBean fwContent;
    /**
     * In-conversation history share (action = {@code "share_history"})
     * — the encrypted original message envelope, preserved intact for
     * late receivers who lost local history past the server's retention
     * window. Carries the original signed {@link BasicMessageRequestBean}
     * with the same conversation key, the same encrypted content blob,
     * and the original sender's Ed25519 signature still attached and
     * verifiable.
     * <p>Unlike {@link #fwContent}, this is NOT a claim — it is a
     * cryptographically verifiable original. {@code share_history} is
     * a different feature from {@code forward}: forward delivers an
     * unverifiable claim across conversations; share_history delivers
     * a verifiable original within the same conversation. No nested
     * share_history (an inner share_history is rejected at validation).
     * <p>See spec §12.11 for the full design.
     */
    @JsonProperty("original_message")
    private BasicMessageRequestBean originalMessage;
    /**
     * Optional trace-of-resend flag for {@code action="share_history"}.
     * Set to {@code true} by a relayer who received the embedded
     * {@link #originalMessage} via a prior {@code share_history} (i.e.
     * not directly from the original sender). Set to {@code false} or
     * left {@code null} when the relayer is the original direct
     * receiver doing a first share. Unverifiable — UI treatment is
     * informational only ("originally received elsewhere"), never
     * skeptical. Deliberately a single boolean rather than a counter
     * or chain, to avoid introducing forensic signal about
     * intermediate relayers.
     * <p>See spec §12.11.10 for the canonical unwrap-and-rewrap
     * re-share operation that drives this flag.
     */
    @JsonProperty("re_shared")
    private Boolean reShared;
    /**
     * Client-side message-management protocol version, declared by the
     * sender, in strict MAJOR.MINOR format (regex {@code ^\d+\.\d+$}).
     * Distinct from the cryptographic protocol version — this field
     * governs the message-action layer (action registry, target
     * semantics, embedded-payload rules), not the E2E cryptographic
     * stack. Lives inside the encrypted body — server-blind.
     *
     * <p>Three input states are distinguished:
     * <ul>
     *   <li>{@code null} / empty / whitespace-only after trim →
     *       <strong>conditional grandfather to v1.0 (legacy)</strong>:
     *       valid iff none of {@link #action}, {@link #targets},
     *       {@link #fwContent}, {@link #originalMessage},
     *       {@link #reShared} is populated. If any v1.1+ field is
     *       populated without an explicit version, the message is
     *       rejected (incoherent / spoofed legacy claim).</li>
     *   <li>Recognized value matching the strict regex with MAJOR
     *       equal to the receiver's MAJOR → validated per the
     *       version-specific rules. Same-MAJOR / higher-MINOR is
     *       processed best-effort.</li>
     *   <li>Malformed, unrecognized MAJOR, or other parse failure →
     *       <strong>message rejected</strong> (drop, ERROR severity).</li>
     * </ul>
     *
     * <p>Honest senders:
     * <ul>
     *   <li>Legacy v1.0 (pre-revision): emit no version, no v1.1+
     *       fields. Pass as legacy.</li>
     *   <li>v1.1+: emit version, may emit v1.1+ fields. Pass as v1.x.</li>
     *   <li>Mixed (no version + v1.1+ fields populated) → rejected.</li>
     * </ul>
     *
     * <p>See spec §3.4 for the full version field definition, §4.2
     * step 0 for the validation gate, and §12.7 for the strict-drop
     * rule on invalid versions and unknown actions.
     */
    @JsonProperty("client_protocol_version")
    private String clientProtocolVersion;
}
