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
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The profile channel's server-enforced limits, as advertised in
 * {@code serverinfo} (DR-022). <b>Clients clamp to these rather than to their
 * own compiled-in constants</b> (DR-023) — a client built against a different
 * server generation must not refuse at a cap the server does not have, nor send
 * past one it does.
 *
 * <p>Every figure here is one the server can actually enforce. There is
 * deliberately nothing about display-name length, image format or pixel
 * dimensions: the card is ciphertext to the server (design D2), those caps are
 * client obligations, and advertising them here would imply an enforcement that
 * does not exist. They live in the registry §4 instead.</p>
 *
 * <p>A client that finds this field absent is talking to a server without the
 * profile channel, and should fall back to its compiled-in constants and expect
 * the routes to be unavailable — {@code supportedRoutes} is the authoritative
 * check for that.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProfileLimitsBean {

    /**
     * Cap on the base64 profile blob, in characters. Producers refuse above this
     * rather than relying on {@code ERR_TOO_LARGE}.
     */
    @JsonProperty("max_blob_b64_chars")
    private int maxBlobB64Chars;

    /** Cap on grants in a single write. */
    @JsonProperty("max_grants_per_write")
    private int maxGrantsPerWrite;

    /** Cap on grants one identity may hold in total. */
    @JsonProperty("max_grants_per_user")
    private int maxGrantsPerUser;

    /** Cap on targets in one {@code getprofiledigests} batch. */
    @JsonProperty("max_digest_batch")
    private int maxDigestBatch;

    /** Accepted {@code cipher} values. */
    @JsonProperty("allowed_ciphers")
    private List<String> allowedCiphers;

    /** Accepted {@code blob_version} values (the envelope, not the card). */
    @JsonProperty("allowed_blob_versions")
    private List<String> allowedBlobVersions;

    /** Burst budget for the {@code read-profile} bucket (advisory hint, DR-024). */
    @JsonProperty("read_burst")
    private int readBurst;

    /** Sustained per-minute budget for {@code read-profile} (advisory hint). */
    @JsonProperty("read_per_minute")
    private int readPerMinute;

    /** Burst budget for the {@code write-profile} bucket (advisory hint). */
    @JsonProperty("write_burst")
    private int writeBurst;

    /** Sustained per-minute budget for {@code write-profile} (advisory hint). */
    @JsonProperty("write_per_minute")
    private int writePerMinute;
}
