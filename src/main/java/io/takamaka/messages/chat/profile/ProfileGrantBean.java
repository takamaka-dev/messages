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
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.chat.conversation.TopicKeyDistributionItemBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One grant: the profile key wrapped to one peer's registered RSA-4096
 * encryption key — registry §4.2, design D3.
 *
 * <p>Structurally this is {@link TopicKeyDistributionItemBean} plus a grantee
 * and an epoch. That is not a coincidence and not a resemblance to be tidied
 * away: a profile-key grant is byte-identical in shape to a conversation-key
 * invite, and it is produced by the SAME code —
 * {@code ChatCryptoUtils.getProfileGrantForUser} delegates to
 * {@code getInviteForUser}.</p>
 *
 * <p><b>Never reimplement the wrap.</b> The base64 re-encode inside
 * {@code getInviteForUser} is the F11 fix in situ; a second implementation
 * inherits the bug rather than the fix.</p>
 *
 * <p><b>The grant is the security boundary.</b> The server's co-membership
 * check on a peer read is anti-enumeration only (design D9). Holding this row
 * — not passing that check — is what lets a peer read the card.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileGrantBean {

    /**
     * The grantee's IDENTITY public key (Base64URL) — who may read. Distinct
     * from the ENCRYPTION key the wrap targets; {@link #encKeyHash} identifies
     * that one.
     */
    @JsonProperty("grantee")
    private String grantee;

    /**
     * The profile-key generation this grant unwraps. A grant whose epoch does
     * not match the stored blob's is stale: rotation deletes superseded epochs
     * rather than keeping them (design D12).
     */
    @JsonProperty("key_epoch")
    private long keyEpoch;

    /**
     * {@code TkmSignUtils.Hash256B64URL(grantee encryption_public_key)}.
     *
     * <p>A PRECONDITION CHECK, not a checksum. A grantee that re-registered
     * with a fresh encryption key holds a grant wrapped to a key it no longer
     * has; comparing this hash to its own lets it report {@code no_grant}
     * instead of attempting a decrypt that would fail confusingly. Only the
     * owner can heal that — v1 does not auto-heal it (design D12, the
     * re-registration hole).</p>
     */
    @JsonProperty("enc_key_hash")
    private String encKeyHash;

    /**
     * The profile key, RSA-4096-OAEP-SHA256-encrypted to the grantee's
     * encryption public key, Base64URL (F11 — see
     * {@link EncryptedProfileBean#getBlob()}).
     */
    @JsonProperty("enc_key")
    private String encKey;
}
