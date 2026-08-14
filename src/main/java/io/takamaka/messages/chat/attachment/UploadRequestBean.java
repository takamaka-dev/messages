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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.extra.beans.StreamEncryptedDescriptor;
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
public class UploadRequestBean {

    /**
     * conversation to which the message will be mapped
     */
    @JsonProperty("topic_title")
    private String topicTitle;
    /**
     * hash of the encrypted file, the remote server only konw this, the hash of
     * the unencrypted file should never be leaked to the server
     */
    @JsonProperty("upload_content_id_hash")
    private String uploadContentIdentifingHash;
    /**
     * Upper bound, in bytes, of the content size — of the <strong>ENCODED body as it will arrive</strong>,
     * i.e. the same quantity as {@code ChatMediaPlaceholderBean.size} ({@code ATTACHMENT_PROTOCOL.md} §4.2).
     *
     * <p>The server enforces this against the bytes it actually receives, with a configured tolerance
     * ({@code rschat.file-upload.encryption-tolerance-bytes}, 1 MB by default), and errors hard on the
     * first chunk past it. Declaring the <em>ciphertext</em> byte count instead therefore under-declares
     * by ~33% and cuts off any upload whose base64 overhead exceeds the tolerance — everything above
     * roughly 3 MB. Do not "correct" this field to a decoded size; it is measured on the wire because
     * that is where it is checked.</p>
     */
    private Long size;
    private StreamEncryptedDescriptor sed;

}
