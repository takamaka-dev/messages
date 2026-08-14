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
package io.takamaka.messages.chat.attachment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.extra.beans.StreamEncryptedDescriptor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata placeholder for an attached media file.
 *
 * <p>Contains all information needed to identify and download an attachment.
 * The actual file content is stored separately and downloaded via the
 * retrieveattachment endpoint.</p>
 *
 * <p><strong>When {@code isTheObject=true} (inline content):</strong></p>
 * <ul>
 *   <li>{@code preview} MUST be populated (contains the full content)</li>
 *   <li>{@code encryptedFileHash} = null (no server upload)</li>
 *   <li>{@code sed} = null (no encryption descriptor needed)</li>
 *   <li>{@code unencryptedContentHash} = SHA3-256 of the Base64-decoded preview bytes</li>
 *   <li>{@code originalSize} = byte length of decoded preview content</li>
 *   <li>{@code size} = same as {@code originalSize} (no encryption overhead)</li>
 * </ul>
 *
 * <p><strong>When {@code isTheObject=false} (regular attachment):</strong></p>
 * <ul>
 *   <li>{@code encryptedFileHash} MUST be populated</li>
 *   <li>{@code sed} MUST be populated</li>
 *   <li>{@code preview} is optional (256x256 WebP thumbnail)</li>
 *   <li>{@code size} = the encoded length of the encrypted data, as emitted — the TRANSFER
 *       quantity, ~1.33× the file (`ATTACHMENT_PROTOCOL.md` §4.2; see the field's own javadoc)</li>
 *   <li>{@code originalSize} = plaintext file size in bytes</li>
 * </ul>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @version 1.9.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMediaPlaceholderBean {

    /**
     * MIME type of the <strong>ORIGINAL OBJECT</strong> — never of the {@link #preview}.
     *
     * <p>This field always describes the thing the placeholder refers to. Whether it also happens
     * to describe the bytes in {@code preview} depends on {@link #isTheObject}:</p>
     * <ul>
     *   <li>{@code isTheObject=true} — the object IS the preview bytes, so this describes them too;</li>
     *   <li>{@code isTheObject=false} — this describes the separately-transferred blob and says
     *       <strong>nothing</strong> about the preview, which is an independently generated
     *       thumbnail and is routinely a different format (a JPEG preview of an
     *       {@code image/heic} object, for example).</li>
     * </ul>
     *
     * <p><strong>A consumer MUST NOT use this field to decode the preview.</strong> Identify the
     * preview by magic bytes ({@code FFD8} JPEG, {@code 89504E47} PNG, {@code GIF8} GIF,
     * {@code RIFF….WEBP} WebP). There is no field that declares the preview's own type, by design —
     * previews are decoded by content, and that mismatch is a feature: a preview lets a client
     * render something for an object whose format it cannot decode at all.</p>
     *
     * <p>The previous wording here — "MIME type of the encoded b64 object" — read most naturally as
     * the base64 payload carried in this bean, i.e. the preview. It meant the opposite.</p>
     *
     * @see
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types/Common_types">MDN
     * Common media types</a>
     */
    @JsonProperty("media_type")
    private String mediaType;

    /**
     * The byte length of the <strong>ENCODED form of the encrypted data, as emitted</strong> —
     * i.e. what actually traverses the wire.
     *
     * <ul>
     *   <li>{@code isTheObject=false} (blob): the length of the base64 body, <em>including</em> any
     *       line breaks the producer emits;</li>
     *   <li>{@code isTheObject=true} (inline): the plaintext byte count — there is no blob and no
     *       encryption overhead, so it equals {@code originalSize}.</li>
     * </ul>
     *
     * <p>Stated in terms of <em>the encoding</em> rather than of base64 specifically, so the
     * definition survives an encoding change instead of silently becoming false. The normative text
     * is {@code ATTACHMENT_PROTOCOL.md} §4.2.</p>
     *
     * <p><strong>⚠️ "the size of the encrypted bytes" is a natural reading and it is wrong by ~33%</strong>
     * (43 269 vs 57 692 on the reference blob). This field is the <em>transfer</em> quantity: it is
     * the download-progress denominator in all three clients, and the only field that carries the
     * number, since the encoded length cannot be derived exactly from a byte count — the encoders
     * differ by design. For the plaintext size use {@link #originalSize}.</p>
     *
     * <p><strong>"As emitted" is load-bearing, and producer-relative on purpose.</strong> Java writes
     * the base64 CRLF-wrapped at 76 (Apache Commons) and counts those breaks; the Dart port emits one
     * line. The same file therefore declares 59 212 (Java) or 57 692 (Dart). That is correct rather
     * than divergent: every encryption produces a fresh blob with its own {@code sed} and its own
     * {@code encryptedFileHash}, so this number describes <em>this</em> producer's body and is never
     * compared against another's.</p>
     *
     * <p><strong>DR-030 (2026-08-14) deliberately does NOT apply here.</strong> It moved
     * {@code encrypted_content_hash} onto the ciphertext bytes because a hash is an <em>identity</em>
     * and must be producer-independent. {@code size} is a transfer descriptor, not an identity, and
     * was briefly moved with it — which broke uploads above ~3 MB and made every download bar read
     * ~133%. DR-030 is scoped to the hash.</p>
     */
    @JsonProperty("size")
    private Long size;

    /**
     * Hash of the base64 content of the unencrypted object.
     */
    @JsonProperty("unencrypted_content_hash")
    private String unencryptedContentHash;

    /**
     * Signature used to identify the object to be requested at the server
     * endpoint. Null when {@code isTheObject=true} (inline content).
     */
    @JsonProperty("encrypted_file_hash")
    private String encryptedFileHash;

    /**
     * Stream encryption descriptor with decryption parameters.
     * Null when {@code isTheObject=true} (inline content).
     */
    private StreamEncryptedDescriptor sed;

    // ============================================================
    // Phase 1 fields
    // ============================================================

    /**
     * Standard-Base64 payload. What it holds depends on {@link #isTheObject}.
     *
     * <p>✅ <strong>CONFORMANCE STATUS (2026-08-13, updated): the rules below are NORMATIVE and now
     * IMPLEMENTED by every client.</strong> One shared engine per platform —
     * {@code io.takamaka.extra.imaging.ThumbnailHelper} (Java, consumed by shell and chat-web-gui)
     * and its mirror {@code wallet-extra-flutter/lib/src/imaging/thumbnail_helper.dart} (Dart,
     * consumed by tkmChat). Per-rule status:</p>
     * <ul>
     *   <li>inline threshold — honoured; a source within {@link InlineContentLimits#MAX_INLINE_BYTES}
     *       generates no preview. <em>Exception, deliberate:</em> a producer that has already
     *       committed to the blob transport calls {@code generatePreviewForBlob}, because the rule's
     *       premise (the receiver holds the object instead) does not hold for it. The shell is such a
     *       producer; making it choose the transport is N-23, not this work;</li>
     *   <li>256px longest edge, never upscaling — honoured on both platforms;</li>
     *   <li>EXIF orientation baked into the pixels, failing open to 1 — honoured on both, landed
     *       together as the coordination rule required;</li>
     *   <li>JPEG for opaque / PNG for real transparency — honoured; "real" means a scan for a
     *       non-opaque pixel, so a fully-opaque alpha channel does not force PNG;</li>
     *   <li>byte ceiling checked AFTER encoding, preview dropped and logged if exceeded — honoured;</li>
     *   <li>the ceiling now actually applies to blob previews, on both the producer guard and the
     *       receiver check — it never had before (§PREVIEW-CONFORMANCE W1).</li>
     * </ul>
     *
     * <p>The notice this replaced said the opposite, and said it deliberately: the wording before
     * THAT ("256x256 WebP") described a format no client had ever emitted, and was believed for
     * months precisely because a specification that states behaviour without stating its
     * implementation status reads as a description of what the code does. Keep stating the status
     * here, whichever way it points.</p>
     *
     * <p><strong>{@code isTheObject=true} — this IS the object.</strong> The content fitted within
     * {@link InlineContentLimits#MAX_INLINE_BYTES} and travels in the message envelope, so there is
     * no blob and <strong>no preview is generated</strong>: a preview of content you already hold
     * would be pure overhead. {@link #unencryptedContentHash} is the hash of these bytes.</p>
     *
     * <p><strong>{@code isTheObject=false} — this is a generated thumbnail of a separately
     * transferred blob.</strong> Rules:</p>
     * <ul>
     *   <li>images only;</li>
     *   <li>if {@code max(width,height) > 256}, scale so the longest edge is 256 —
     *       <strong>never upscale</strong> a smaller source;</li>
     *   <li>EXIF orientation is <strong>baked into the pixels</strong> (JPEG sources only), because
     *       a re-encoded preview cannot carry the tag. Unreadable or absent orientation is treated
     *       as 1 — a preview must never fail because peer-supplied EXIF was malformed;</li>
     *   <li>permitted types: JPEG, PNG, GIF, WebP. <strong>This implementation emits JPEG (opaque)
     *       or PNG (alpha) only</strong> — WebP is permitted for future producers but MUST NOT be
     *       emitted until every client can decode it (as of 2026-08-13 stock {@code javax.imageio}
     *       can neither read nor write it);</li>
     *   <li>{@link InlineContentLimits#MAX_INLINE_BYTES} is an <strong>upper limit, not a
     *       target</strong>. Compressing far below it is an implementation-side optimisation and is
     *       encouraged: the reference 256px JPEG measures ~6 KB against a 51 200 B ceiling.
     *       <em>Known and accepted downside:</em> at the boundary — an object just over the inline
     *       threshold — a conformant but lazy producer may spend nearly the inline budget on a
     *       preview AND still send a blob;</li>
     *   <li>the preview is <strong>unhashed</strong>. {@link #unencryptedContentHash} refers to the
     *       ORIGINAL object, never to this transformed thumbnail — so a preview MUST NOT be stored
     *       in a content-addressed cache keyed by that hash: it is not the bytes that hash to it.</li>
     * </ul>
     *
     * <p>Cross-platform contract is <strong>policy parity, not byte parity</strong>. Encoders differ
     * between platforms, so two clients will not produce identical thumbnail bytes from identical
     * input, and nothing requires them to — no interop path compares preview bytes across
     * platforms. Never pin a cross-platform thumbnail vector; assert the properties instead.</p>
     *
     * <p>The previous wording here specified "256x256 WebP", a format no client has ever emitted
     * and which stock ImageIO cannot even read.</p>
     */
    @JsonProperty("preview")
    private String preview;

    /**
     * When true, the {@code preview} field IS the full content and no
     * server upload is needed. Default false.
     */
    @JsonProperty("is_the_object")
    private Boolean isTheObject;

    /**
     * Original filename (E2E encrypted within the message envelope).
     */
    @JsonProperty("file_name")
    private String fileName;

    /**
     * Plaintext file size in bytes (before encryption).
     */
    @JsonProperty("original_size")
    private Long originalSize;

    // ============================================================
    // Phase 2 reserved fields (sticker collections)
    // ============================================================

    /**
     * Phase 2 reserved: sticker pack reference hash.
     */
    @JsonProperty("pack_hash")
    private String packHash;

    /**
     * Phase 2 reserved: sticker ID within a pack.
     */
    @JsonProperty("sticker_id")
    private String stickerId;

    /**
     * Phase 2 reserved: emoji mapping for the sticker.
     */
    @JsonProperty("emoji")
    private String emoji;

}
