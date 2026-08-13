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
package io.takamaka.messages.chat.attachment;

import java.util.Set;

/**
 * Protocol-level limits for inline content delivered through
 * {@link ChatMediaPlaceholderBean} with {@code isTheObject == true}.
 *
 * <p>These constants are <strong>normative</strong> for every client
 * implementation of the Takamaka chat protocol (Java reference, Dart port,
 * any future clients). They govern the boundary between two distinct
 * attachment paths:</p>
 *
 * <ul>
 *   <li><strong>Inline path</strong> ({@code isTheObject == true}): the
 *       full media payload travels inside the AES-256-GCM-encrypted
 *       {@code BasicMessageEncryptedContentBean} as base64-encoded bytes
 *       in the {@code preview} field. No server upload. No separate
 *       stream-encryption descriptor. Used for stickers, emojis,
 *       reactions, and any small image (≤ 50 KiB, ≤ 256×256 px) where
 *       the round-trip cost of a separate upload/download is not
 *       worth it.</li>
 *   <li><strong>Regular attachment path</strong> ({@code isTheObject == false}
 *       or {@code null}): the media is encrypted with a fresh per-upload
 *       stream descriptor ({@code sed}), uploaded to the server, and
 *       referenced by its {@code encrypted_file_hash}. Used for any
 *       content that does not satisfy the inline limits, or for which
 *       a thumbnail-plus-on-demand-download UX is preferred.</li>
 * </ul>
 *
 * <h2>Receiver enforcement</h2>
 *
 * <p>Receivers MUST reject any inline placeholder that violates these
 * limits. Rejection is at the inline-content level only: the parent
 * message's {@code text_message} (if any) MUST still be rendered. The
 * rejection MUST be surfaced through an implementation-defined
 * decoration (see the message-actions spec, §11.6, for the named
 * decoration codes).</p>
 *
 * <h2>Server visibility</h2>
 *
 * <p>The server cannot see or enforce these limits: the entire
 * placeholder lives inside the encrypted body. These constants exist
 * solely to keep cooperating clients consistent and to give honest
 * implementations a single source of truth.</p>
 *
 * <h2>Cross-platform parity</h2>
 *
 * <p>The Dart port at
 * {@code rsclient-flutter/lib/src/beans/attachment/inline_content_limits.dart}
 * mirrors these values one-for-one. Any change here MUST be matched
 * there in the same release, otherwise clients will disagree on what
 * "inline" means and produce mutually-rejected messages.</p>
 *
 * <h2>Provenance</h2>
 *
 * <p>The numeric values originated in {@code shell/.../utils/ThumbnailService}
 * (constants {@code MAX_THUMBNAIL_SIZE = 256} and
 * {@code MAX_INLINE_SIZE = 50 * 1024}). They are promoted here from
 * shell-local concerns to protocol-level invariants as part of the
 * reply/reaction message-actions specification. {@code ThumbnailService}
 * is updated in the same release to reference these constants instead
 * of declaring its own copies.</p>
 *
 * @see ChatMediaPlaceholderBean
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public final class InlineContentLimits {

    /**
     * Maximum byte length of the decoded inline payload.
     *
     * <p>This is the byte length of {@code base64StandardDecode(preview)},
     * <em>not</em> the length of the Base64 string itself. A Base64
     * payload of about 68&nbsp;266 characters decodes to 51&nbsp;200 bytes —
     * use the decoded length for the check.</p>
     *
     * <p>Value: {@value} bytes (50 KiB, i.e. {@code 50 * 1024}).</p>
     *
     * <p>Rationale: above this size, the marginal benefit of inlining
     * (one round-trip saved) is dwarfed by the cost of bloating every
     * encrypted message body, every notification fan-out, and every
     * conversation-history fetch. The regular attachment path is
     * preferable.</p>
     */
    public static final int MAX_INLINE_BYTES = 50 * 1024; // 51_200

    /*
     * MAX_THUMBNAIL_DIMENSION_PX (256) was REMOVED on 2026-08-12.
     *
     * It required decoding untrusted image data in every client just to
     * validate a placeholder — a decompression-bomb surface accepted in
     * exchange for a cosmetic constraint. It also did not generalise (inline
     * content need not be an image) and did not serve this class's own stated
     * rationale, which is about BYTES bloating message bodies, notification
     * fan-out and history fetches. Pixels bloat nothing; MAX_INLINE_BYTES
     * already bounds the harm.
     *
     * Nothing enforced it: an audit on 2026-08-12 found zero receiver-side
     * checks of either limit, and the one producer that read this class
     * (chat-web-gui) checked only the byte limit — and consequently inlined an
     * 800x450 image, which the pixel rule forbade.
     *
     * ⚠️ Renderers must therefore NOT assume an inline image is small in
     * PIXELS. A 50 KiB image can be 4000x3000. Scale defensively.
     */

    /**
     * Maximum number of placeholders in one message's {@code attached_media}.
     *
     * <p>A per-placeholder byte limit bounds nothing while the LIST is unbounded:
     * {@link #MAX_INLINE_BYTES} times infinity is infinity. This is the cheap
     * structural cap — it rejects an absurd list before anything is decoded.
     * {@link #MAX_TOTAL_PREVIEW_BYTES} is the one that actually binds.</p>
     *
     * <p>Value: {@value}. Above any real composer's multi-select, far below the
     * ~20 max-size previews that would breach the server's content cap.</p>
     *
     * @since 1.9.0
     */
    public static final int MAX_ATTACHED_MEDIA = 16;

    /**
     * Maximum SUM of decoded {@code preview} bytes across one message's
     * {@code attached_media}, counting inline payloads and blob thumbnails alike.
     *
     * <p>Derived from the server's hard cap, not invented: rschat rejects an
     * encrypted content bean whose serialized JSON exceeds <strong>1 048 576
     * bytes</strong> ({@code validation.max-encrypted-message-content-size-bytes}
     * → {@code ValidationUtils.validateEncryptedContentSize}). Working backwards:
     * decoded preview bytes are carried as base64 inside the plaintext JSON
     * (x1.34), which is then encrypted and base64-encoded again (x1.34), so
     * <em>d</em> decoded bytes cost about <em>1.8 d</em> on the wire the server
     * measures. {@value} bytes ⇒ ~700 KB, leaving comfortable room for the text
     * message, the {@code sed} descriptors and envelope overhead.</p>
     *
     * <p>⚠️ Clients cannot see the server's cap — it is not in the
     * {@code serverinfo} manifest — so this constant is the only pre-send
     * defence against a rejection the user would otherwise meet at the server.</p>
     *
     * @since 1.9.0
     */
    public static final int MAX_TOTAL_PREVIEW_BYTES = 384 * 1024; // 393_216

    /**
     * Top-level MIME family allowed for inline content carrying image
     * data. Inline placeholders carrying {@code mediaType} starting
     * with this prefix MUST additionally match the receiver's
     * context-specific whitelist (e.g. the reaction-payload whitelist
     * is narrower than the general inline-image whitelist — see the
     * message-actions spec §11.4).
     */
    public static final String INLINE_IMAGE_MIME_FAMILY = "image/";

    /**
     * Closed whitelist of MIME types allowed for the inline-image
     * payload of an {@code action == "reaction"} message.
     *
     * <p>This whitelist is <strong>normative</strong> and intentionally
     * narrower than the general inline-image set recognized by
     * {@code ThumbnailService.IMAGE_MIME_TYPES}. A reaction whose
     * inline-image {@code mediaType} is not in this set MUST be
     * rejected at the action-validation layer (decoration code
     * {@code INLINE_MIME_VIOLATION}). The parent message's
     * {@code text_message} is still rendered.</p>
     *
     * <p>Animated variants of {@code image/gif} and {@code image/webp}
     * are permitted iff they also satisfy {@link #MAX_INLINE_BYTES}.</p>
     *
     * <p>Excluded by design: {@code image/bmp} and {@code image/tiff}
     * (uncompressed / archival formats — disproportionate size for
     * trivial reaction content, and not produced by common emoji /
     * sticker tooling).</p>
     *
     * <p>The set is immutable. Callers must check membership with
     * a case-insensitive comparison: lower-case the incoming
     * {@code mediaType} before {@code contains()}.</p>
     */
    public static final Set<String> REACTION_ALLOWED_IMAGE_MIMES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif"
    );

    /**
     * Convenience predicate: returns {@code true} iff the given
     * {@code mediaType} is acceptable for a reaction's inline-image
     * payload. Performs a case-insensitive lookup against
     * {@link #REACTION_ALLOWED_IMAGE_MIMES}.
     *
     * @param mediaType the placeholder's {@code mediaType} value;
     *                  may be {@code null}
     * @return {@code true} iff non-null and recognized
     */
    public static boolean isReactionImageMimeAllowed(String mediaType) {
        return mediaType != null
                && REACTION_ALLOWED_IMAGE_MIMES.contains(mediaType.toLowerCase());
    }

    /**
     * Types a generated PREVIEW may be encoded in ({@code is_object=false}).
     *
     * <p>Deliberately a distinct constant from {@link #REACTION_ALLOWED_IMAGE_MIMES} even though the
     * members coincide today: that set governs what a REACTION may carry as its inline payload,
     * this one governs what a generated thumbnail of a separately-transferred blob may be encoded
     * in. They answer different questions and will diverge the moment either side changes.</p>
     *
     * <p><strong>{@code image/webp} is PERMITTED but MUST NOT be produced yet.</strong> A producer
     * must not emit a type some consumer cannot decode, and as of 2026-08-13 stock
     * {@code javax.imageio} can neither read nor write WebP (verified on JDK 21), so a Java CLI
     * consumer could not render it. It stays in the set so that adopting it later is additive
     * rather than a protocol change — see {@link #isPreviewMimeProducible}.</p>
     *
     * @since 1.9.0
     */
    public static final Set<String> PREVIEW_ALLOWED_MIMES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    /**
     * Types a producer may currently EMIT as a preview — the producible subset of
     * {@link #PREVIEW_ALLOWED_MIMES}.
     *
     * <p>Consumers accept the full permitted set; producers are held to this narrower one. The gap
     * between the two sets IS the gate: a type is added here only once every client can decode it.</p>
     *
     * @since 1.9.0
     */
    public static final Set<String> PREVIEW_PRODUCIBLE_MIMES = Set.of(
            "image/jpeg",
            "image/png"
    );

    /**
     * Whether a preview MAY be carried by this protocol (consumer side).
     *
     * @param mediaType the preview's own type, determined by MAGIC BYTES — not by the placeholder's
     *                  {@code media_type}, which describes the ORIGINAL OBJECT
     * @return {@code true} iff non-null and permitted
     * @since 1.9.0
     */
    public static boolean isPreviewMimeAllowed(String mediaType) {
        return mediaType != null && PREVIEW_ALLOWED_MIMES.contains(mediaType.toLowerCase());
    }

    /**
     * Whether a producer may currently EMIT a preview in this type.
     *
     * @param mediaType candidate encoding for a generated preview
     * @return {@code true} iff non-null and currently producible
     * @since 1.9.0
     */
    public static boolean isPreviewMimeProducible(String mediaType) {
        return mediaType != null && PREVIEW_PRODUCIBLE_MIMES.contains(mediaType.toLowerCase());
    }


    /**
     * Maximum length of the BASE64 STRING that can decode to
     * {@link #MAX_INLINE_BYTES}, used as a cheap pre-check.
     *
     * <p>Standard base64 emits 4 characters per 3 input bytes, so 51 200 bytes
     * encodes to 68 268 characters (padding included). Anything longer cannot
     * decode to a legal payload, and rejecting on the STRING avoids allocating
     * a hostile payload just to measure it.</p>
     */
    public static final int MAX_INLINE_PREVIEW_B64_CHARS = ((MAX_INLINE_BYTES + 2) / 3) * 4;

    /**
     * The verdict of {@link #checkInlinePayload(String)} — three states,
     * because "not checked" must never read as "within limits".
     */
    public enum InlineVerdict {
        /** Decoded payload is within {@link #MAX_INLINE_BYTES}. */
        OK,
        /** Decoded payload exceeds {@link #MAX_INLINE_BYTES}. MUST be rejected. */
        TOO_LARGE,
        /** {@code preview} is absent or not valid base64 — nothing to measure. */
        UNDECODABLE
    }

    /**
     * Size-check an inline {@code preview} payload. The single definition of
     * the inline byte rule, shared by producers (which MUST refuse to send)
     * and receivers (which MUST reject on arrival).
     *
     * <p>Measured on the DECODED bytes, never on the base64 text — the two
     * differ by ~1.33x, and checking the string would let a payload ~33% over
     * the limit through.</p>
     *
     * <p>A cheap length pre-check runs first so an oversized payload is
     * rejected without being decoded into memory.</p>
     *
     * @param previewB64 the placeholder's {@code preview}, standard base64
     * @return the verdict; never {@code null}
     */
    public static InlineVerdict checkInlinePayload(String previewB64) {
        if (previewB64 == null || previewB64.isEmpty()) {
            return InlineVerdict.UNDECODABLE;
        }
        // Cheap first: too long to POSSIBLY be legal ⇒ reject without decoding.
        if (previewB64.length() > MAX_INLINE_PREVIEW_B64_CHARS) {
            return InlineVerdict.TOO_LARGE;
        }
        try {
            return java.util.Base64.getDecoder().decode(previewB64).length > MAX_INLINE_BYTES
                    ? InlineVerdict.TOO_LARGE
                    : InlineVerdict.OK;
        } catch (IllegalArgumentException ex) {
            return InlineVerdict.UNDECODABLE;
        }
    }

    /**
     * The decoded byte length of an inline payload, for reporting a rejection
     * to a human. Returns -1 when the payload cannot be decoded, and does not
     * decode anything larger than the pre-check allows.
     */
    public static int decodedLengthOrMinusOne(String previewB64) {
        if (previewB64 == null || previewB64.isEmpty()) {
            return -1;
        }
        try {
            return java.util.Base64.getDecoder().decode(previewB64).length;
        } catch (IllegalArgumentException ex) {
            return -1;
        }
    }


    /**
     * Receiver-side gate: is this inline placeholder safe to RENDER?
     *
     * <p>Receivers MUST reject an inline placeholder that violates the byte
     * rule, and the rejection MUST be surfaced to the user with a reason —
     * silently dropping it would make a protocol violation indistinguishable
     * from a failed image load.
     *
     * <p><b>Rejection is at the INLINE-CONTENT level only.</b> The parent
     * message's {@code text_message} MUST still be rendered. Rejecting the whole
     * message would hand any sender a censorship primitive: attach an oversized
     * inline payload and the accompanying text disappears.
     *
     * @param media the placeholder; blob placeholders always pass (the rule is
     *              about inline delivery, not about file size)
     * @return {@link InlineVerdict#OK} when it may be rendered
     */
    public static InlineVerdict checkReceivedInline(
            io.takamaka.messages.chat.attachment.ChatMediaPlaceholderBean media) {
        if (media == null || !Boolean.TRUE.equals(media.getIsTheObject())) {
            return InlineVerdict.OK;
        }
        return checkInlinePayload(media.getPreview());
    }

    /**
     * A human-readable reason for a rejected inline payload, for the decoration
     * a client shows the user. Names the limit and the actual size, because
     * "attachment blocked" without a number is not actionable.
     */
    public static String rejectionReason(InlineVerdict verdict, String previewB64, String fileName) {
        String who = (fileName == null || fileName.isBlank()) ? "inline content" : "'" + fileName + "'";
        switch (verdict) {
            case TOO_LARGE:
                int actual = decodedLengthOrMinusOne(previewB64);
                return who + " was blocked: inline content is limited to "
                        + MAX_INLINE_BYTES + " bytes"
                        + (actual < 0 ? "" : " and this is " + actual)
                        + ". The sender should have sent it as a regular attachment.";
            case UNDECODABLE:
                return who + " was blocked: its inline content is missing or not valid base64.";
            default:
                return "";
        }
    }

    private InlineContentLimits() {
        // no instances
    }
}
