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
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
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
}
