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
 * Signed content for a {@code getprofiledigests} batch read — design D11.
 *
 * <p>Returns {@code (key_epoch, blob_hash, updated_at)} per target: a few
 * hundred bytes that tell a client which of N peers actually changed. Fetching
 * bodies to render a member list is non-conformant, not merely slow.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetProfileDigestsSignedContentBean {

    /**
     * The identities to digest, at most
     * {@link ProfileConstants#MAX_DIGEST_BATCH} — the same bound as
     * {@code max-user-key-request-batch}. Over the cap the server rejects the
     * batch rather than truncating it: a silently short answer would read as
     * "these peers have no profile".
     */
    @JsonProperty("target_public_keys")
    private List<String> targetPublicKeys;

    /**
     * ADVISORY/audit only.
     */
    @JsonProperty("client_timestamp")
    private Long clientTimestamp;
}
