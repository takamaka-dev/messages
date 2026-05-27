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

    /**
     * Maximum pixel dimension (width <strong>and</strong> height) for an
     * inline image. An image qualifies for inline delivery only if
     * both {@code width <= MAX_THUMBNAIL_DIMENSION_PX} and
     * {@code height <= MAX_THUMBNAIL_DIMENSION_PX}.
     *
     * <p>Value: {@value} pixels.</p>
     *
     * <p>Rationale: 256×256 is a common sticker / reaction canvas size
     * and aligns with the 256×256 WebP thumbnail format already
     * produced by the shell and Flutter thumbnail pipelines.</p>
     */
    public static final int MAX_THUMBNAIL_DIMENSION_PX = 256;

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
     * are permitted iff they also satisfy {@link #MAX_INLINE_BYTES}
     * and {@link #MAX_THUMBNAIL_DIMENSION_PX}.</p>
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

    private InlineContentLimits() {
        // no instances
    }
}
