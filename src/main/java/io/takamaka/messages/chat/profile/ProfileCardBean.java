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
package io.takamaka.messages.chat.profile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The PLAINTEXT profile card — {@code payload_version 1.0}, defined field by
 * field in {@code rschat-docs/api-references/user-profile-registry.md} §4.
 *
 * <p><b>Client-side only. This bean never appears on the wire in this form.</b>
 * It is sealed with AES-256-GCM under the profile key into an
 * {@link EncryptedProfileBean} before it leaves the client, and the server
 * stores nothing but that opaque blob (design D2). A field of this class
 * reaching {@code rschat} unencrypted is a P0, not a bug.</p>
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} is load-bearing, not
 * boilerplate: registry §3 promises that a {@code 1.1} card stays readable by a
 * {@code 1.0} consumer, and that promise is kept here.</p>
 *
 * <p><b>Do not add a {@code toString()}-visible secret to this class, and do
 * not log an instance.</b> Lombok's {@code @Data} generates a
 * {@code toString()} over every field; the 2026-08-13 P0 was one
 * {@code log.info} over one {@code @Data} bean that carried conversation keys.
 * The card holds no key, but it does hold a name, a status and a face — the
 * personal data this whole channel exists to keep out of the clear.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileCardBean {

    /**
     * Card schema version, {@code "1.0"}. REQUIRED — a card without it is
     * rejected rather than guessed at. Distinct from
     * {@code EncryptedProfileBean.blobVersion}, which describes the envelope
     * and moves independently (registry §3).
     */
    @JsonProperty("payload_version")
    private String payloadVersion;

    /**
     * Display name, NFC-normalised, at most
     * {@link ProfileConstants#MAX_DISPLAY_NAME_CHARS} code points.
     *
     * <p><b>A name is not an identity</b> (registry §4.5.2). There is no
     * uniqueness, no reservation and no directory: two identities may both call
     * themselves "Alice", and one of them may be doing it on purpose. A
     * renderer that lets this string displace the public key has built an
     * impersonation vector.</p>
     */
    @JsonProperty("display_name")
    private String displayName;

    /**
     * Short status message, NFC-normalised, at most
     * {@link ProfileConstants#MAX_STATUS_MESSAGE_CHARS} code points. Sanitise
     * before rendering (registry §4.5.1) — it is an attacker-controlled string
     * from a peer.
     */
    @JsonProperty("status_message")
    private String statusMessage;

    /**
     * The avatar image, base64 of the raw bytes, at most
     * {@link ProfileConstants#MAX_AVATAR_BYTES} DECODED. Inline in the card by
     * design (D7): no blob store, no quota, no orphan sweep.
     */
    @JsonProperty("avatar")
    private String avatar;

    /**
     * ADVISORY media type. <b>Never decode by this value</b> — determine the
     * format from the leading bytes, same rule and same reason as
     * {@code ChatMediaPlaceholderBean.mediaType} (registry §4.5.3).
     */
    @JsonProperty("avatar_media_type")
    private String avatarMediaType;
}
