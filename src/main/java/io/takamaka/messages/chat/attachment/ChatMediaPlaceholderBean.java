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
 *   <li>{@code size} = encrypted/Base64 size (existing behavior)</li>
 *   <li>{@code originalSize} = plaintext file size in bytes</li>
 * </ul>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMediaPlaceholderBean {

    /**
     * MIME type of the encoded b64 object.
     *
     * @see
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types/Common_types">MDN
     * Common media types</a>
     */
    @JsonProperty("media_type")
    private String mediaType;

    /**
     * Base64 UTF-8 char count of the media (encrypted size for regular
     * attachments, plaintext size for inline content).
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
     * Base64-encoded preview thumbnail (256x256 WebP).
     * When {@code isTheObject=true}, this contains the full content.
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
