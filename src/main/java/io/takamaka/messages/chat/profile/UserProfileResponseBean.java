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
 * Response to {@code getuserprofile} — the owner reading its OWN profile back.
 *
 * <p>No grant is returned: the owner holds the profile key already. What this
 * call is for is convergence — a second device learning what the first one
 * wrote, and the {@code nonce_issue_time} it must beat to overwrite it.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfileResponseBean {

    /**
     * The stored blob, or {@code null} when {@link #cleared} or when no profile
     * has ever been written.
     */
    @JsonProperty("profile")
    private EncryptedProfileBean profile;

    /**
     * Whether the row is a D6 tombstone. {@code cleared} and "never written"
     * are DIFFERENT states to the owner: the first carries a watermark a
     * straggler must beat, the second does not.
     */
    @JsonProperty("cleared")
    private boolean cleared;

    /**
     * The row's authoritative LWW clock: the issue time of the nonce that last
     * wrote it (design D5). A client rewriting the profile must obtain a nonce
     * issued after this, or lose.
     */
    @JsonProperty("nonce_issue_time")
    private Long nonceIssueTime;

    /**
     * How many grants are currently published for the stored epoch. A count,
     * not the grants themselves — the owner can regenerate every wrap from the
     * profile key, and shipping ~700 B per grantee to answer "did my fan-out
     * land?" would be waste.
     */
    @JsonProperty("grant_count")
    private Integer grantCount;
}
