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
 * Signed content for a {@code getuserprofilepeer} read — design §6.2, D9/D11.
 *
 * <p>Nonce-free and signed (design D4): the server needs an authenticated
 * caller to run the co-membership check, and that check is
 * ANTI-ENUMERATION ONLY — it stops a registered identity walking the user
 * table. It is not what keeps the profile private; possession of a grant is
 * (D9).</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetUserProfilePeerSignedContentBean {

    /**
     * The identity whose profile is being read (Base64URL).
     */
    @JsonProperty("target_public_key")
    private String targetPublicKey;

    /**
     * The caller's cached {@code blob_hash}, or {@code null} for an
     * unconditional read. On a match the server answers {@code unchanged} with
     * NO body — a conditional GET in protocol form.
     *
     * <p>Sending it is a CONFORMANCE requirement, not an optimisation: a
     * 200-member conversation rendered without it is ~18 MB per screen open,
     * which defeats the read bucket in seconds and makes the feature feel
     * broken (design D11).</p>
     */
    @JsonProperty("known_blob_hash")
    private String knownBlobHash;

    /**
     * ADVISORY/audit only.
     */
    @JsonProperty("client_timestamp")
    private Long clientTimestamp;
}
