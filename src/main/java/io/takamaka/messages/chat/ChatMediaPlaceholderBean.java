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
package io.takamaka.messages.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class ChatMediaPlaceholderBean {

    /**
     * mime type of the encoded b64 object
     *
     * @see
     * <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/MIME_types/Common_types">MDN
     * Common media types</a>
     */
    @JsonProperty("media_type")
    private String mediaType;
    /**
     * base64 utf-8 char count of the media
     */
    @JsonProperty("size")
    private Long size;
    /**
     * hash of the base64 content of the unencrypted object
     */
    @JsonProperty("unencrypted_content_hash")
    private String conentHash;
    /**
     * signature used to identify the object to be requested at the server
     * endpoint
     */
    @JsonProperty("request_signature")
    private String objectSignature;

}
