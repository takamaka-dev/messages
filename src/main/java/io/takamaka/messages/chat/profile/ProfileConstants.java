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

import io.takamaka.messages.chat.attachment.InlineContentLimits;

/**
 * Shared literals and caps for the user-profile channel, mirroring the
 * authoritative registry
 * ({@code rschat-docs/api-references/user-profile-registry.md}). Mechanism:
 * {@code rschat-docs/roadmap/USER_PROFILE_DESIGN.md}, register entry DR-032.
 *
 * <p>The registry remains the source of truth; this class only avoids string
 * and number drift across modules.</p>
 *
 * <p><b>Every cap below except the blob length is enforced by the PRODUCING
 * CLIENT and by nothing else.</b> The card is AES-256-GCM ciphertext to the
 * server (design D2), so {@code rschat} cannot see a display name, a status
 * message, or an image — it validates the envelope's size and structure only
 * (design D8). This is the same posture {@link InlineContentLimits} already
 * documents for inline media, and it is a consequence of the end-to-end
 * ruling, not a gap to be closed later.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class ProfileConstants {

    private ProfileConstants() {
    }

    // ---- versions (registry §3) ----
    /**
     * Schema version of the PLAINTEXT card, carried inside the ciphertext and
     * read only by a decrypting peer.
     */
    public static final String PAYLOAD_VERSION_1_0 = "1.0";
    /**
     * Schema version of the CLEARTEXT envelope, carried on the wire and
     * validated server-side against {@code rschat.profile.allowed-blob-versions}.
     * Moves independently of {@link #PAYLOAD_VERSION_1_0}.
     */
    public static final String BLOB_VERSION_1_0 = "1.0";

    // ---- cipher (registry §4.1) ----
    /**
     * The only cipher this channel defines. The blob is
     * {@code base64url(IV || ciphertext || GCM tag)} under the profile key.
     */
    public static final String CIPHER_AES_256_GCM = "AES_256_GCM";

    /**
     * Raw profile-key length. The key is 32 CSPRNG bytes carried as Base64URL
     * text, because that text is what gets RSA-wrapped per grantee by the
     * existing topic-key primitive (design D3).
     */
    public static final int PROFILE_KEY_BYTES = 32;
    /**
     * GCM nonce length in bytes. 12 is the NIST-recommended size and the one
     * {@code EncryptionContext.v0_2_a_stream_gcm} already uses in this estate.
     */
    public static final int GCM_IV_BYTES = 12;
    /**
     * GCM authentication tag length in bits.
     */
    public static final int GCM_TAG_BITS = 128;

    // ---- card caps: CLIENT-ENFORCED (registry §4) ----
    /**
     * {@code display_name} cap, in Unicode CODE POINTS after NFC normalisation
     * — not UTF-16 units. Counting units truncates emoji and non-BMP scripts
     * one glyph early, and two clients that disagree on the counting rule
     * disagree on whether the same card is conformant.
     */
    public static final int MAX_DISPLAY_NAME_CHARS = 64;
    /**
     * {@code status_message} cap, in NFC code points (see
     * {@link #MAX_DISPLAY_NAME_CHARS}).
     */
    public static final int MAX_STATUS_MESSAGE_CHARS = 128;
    /**
     * Avatar cap in DECODED bytes: 128 KiB (registry §4, §4.3).
     *
     * <p><b>This is deliberately NOT
     * {@link InlineContentLimits#MAX_INLINE_BYTES}, and must never be re-aliased
     * to it.</b> That constant is the per-message INLINE-MEDIA rule, enforced in
     * {@code MessageActionValidator} and {@code ChatCryptoUtils} for every
     * message in the estate. The two caps once shared a value (50 KiB); they
     * never shared a meaning, and on 2026-08-20 they stopped sharing the value
     * too. Re-linking them would raise the inline-media limit for every message
     * as a side effect of a profile change — a test asserts they differ, so that
     * "tidy-up" fails the build (registry §4.3.1, §6).</p>
     *
     * <p><b>Why 128 KiB and not 512 KiB or 1 MiB.</b> A 1 MiB avatar's
     * {@code setuserprofile} frame lands over the 2 MB
     * {@code max-frame-payload-length} enforced on the server AND in
     * {@code ClientRequestor}, so it is structurally blocked. 512 KiB fits, but
     * takes D11's cold 200-peer read from 18 MB to 186 MB and invalidates the
     * {@code read-profile} rate bucket. Full-resolution photos want a real blob
     * store (design §9 REJECTED-B), not a bigger inline cap. Registry §4.3.2
     * carries the arithmetic.</p>
     *
     * <p><b>Bytes, never pixels.</b> {@code MAX_THUMBNAIL_DIMENSION_PX} was
     * REMOVED on 2026-08-12 because validating a pixel limit means decoding
     * untrusted image data — a decompression-bomb surface accepted for a
     * cosmetic constraint. That ruling applies here verbatim and is not
     * reopened (registry §4.5.4). It governs what a client checks on RECEIVE;
     * it does not forbid a producer re-encoding a file its own user picked
     * (registry §4.6.3).</p>
     */
    public static final int MAX_AVATAR_BYTES = 131072;
    /**
     * {@code avatar_media_type} cap. The field is ADVISORY: consumers decode by
     * magic bytes and never by this value (registry §4.5.3).
     */
    public static final int MAX_AVATAR_MEDIA_TYPE_CHARS = 64;

    // ---- envelope caps: server-enforced, but clamp to the manifest (DR-023) ----
    /**
     * Default cap on the base64 blob, matching {@code rschat.profile.max-blob-b64-chars}.
     * 240 KiB — the derived budget in registry §4.3; change it and re-derive
     * that table.
     *
     * <p>Derivation, so a future change re-derives rather than guesses: the blob
     * is base64 ONCE inside the card and base64 AGAIN over the GCM output, so it
     * grows at ≈ 1.78 × the avatar plus ~1.4 KB of JSON and GCM overhead. At
     * {@link #MAX_AVATAR_BYTES} that is ≈ 234 424 characters, and this cap
     * leaves ~11 KB of headroom above it.</p>
     *
     * <p>A client SHOULD clamp to the value advertised by {@code serverinfo}
     * rather than to this compiled-in constant (DR-022/DR-023). This is the
     * fallback when no manifest has been fetched.</p>
     */
    public static final int MAX_BLOB_B64_CHARS = 245760;
    /**
     * Default cap on grants in one write, matching
     * {@code rschat.profile.max-grants-per-write}.
     */
    public static final int MAX_GRANTS_PER_WRITE = 256;
    /**
     * Default cap on targets in one {@code getprofiledigests} batch, matching
     * {@code rschat.profile.max-digest-batch} and the existing
     * {@code max-user-key-request-batch}.
     */
    public static final int MAX_DIGEST_BATCH = 50;

    // ---- peer-read status literals (registry §4.4) ----
    /**
     * Blob and the caller's grant are returned.
     */
    public static final String STATUS_VISIBLE = "visible";
    /**
     * The caller's {@code known_blob_hash} matched; no body (design D11).
     */
    public static final String STATUS_UNCHANGED = "unchanged";
    /**
     * The profile exists and is visible, but no grant matches the caller's
     * CURRENT encryption key — typically the re-registration hole (D12). Only
     * the owner can heal it, by re-granting.
     */
    public static final String STATUS_NO_GRANT = "no_grant";
    /**
     * The owner tombstoned the profile (design D6).
     */
    public static final String STATUS_CLEARED = "cleared";
    /**
     * Uniform answer for "no co-membership" AND "no such identity" —
     * deliberately indistinguishable, so a registered identity cannot walk the
     * user table (design D9).
     */
    public static final String STATUS_NOT_VISIBLE = "not_visible";
}
