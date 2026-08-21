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
 * One entry in a {@code getprofiledigests} answer: enough to decide whether to
 * fetch a body, and nothing more — design D11.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileDigestBean {

    /**
     * The identity this digest describes (Base64URL).
     */
    @JsonProperty("target")
    private String target;

    /**
     * The stored blob's key epoch, or {@code null} when there is nothing to
     * fetch (no profile, cleared, or not visible to this caller).
     */
    @JsonProperty("key_epoch")
    private Long keyEpoch;

    /**
     * The stored {@code blob_hash}. A client fetches a body only when this
     * differs from its cached value.
     */
    @JsonProperty("blob_hash")
    private String blobHash;

    /**
     * When the row last changed — the write's nonce issue time (server clock).
     */
    @JsonProperty("updated_at")
    private Long updatedAt;
}
