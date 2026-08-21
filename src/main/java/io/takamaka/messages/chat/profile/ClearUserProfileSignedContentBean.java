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
import io.takamaka.messages.chat.core.NonceResponseBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Signed content for a {@code clearuserprofile} write — design D6.
 *
 * <p><b>Clearing is a TOMBSTONE, not a delete.</b> The server keeps the row,
 * blanks the blob and hash, sets {@code cleared = true}, drops the grants, and
 * retains THIS nonce's issue time as the row's LWW clock. Deleting the row
 * instead would let a straggler — an offline device flushing a queued edit with
 * an older nonce — recreate the profile the user just removed. The row is what
 * makes the comparison possible, so the row has to survive.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClearUserProfileSignedContentBean {

    /**
     * Server-issued nonce. Its issue time becomes the tombstone's watermark
     * (design D6).
     */
    @JsonProperty("nonce")
    private NonceResponseBean nonce;

    /**
     * ADVISORY/audit only — never authoritative for ordering (design D5).
     */
    @JsonProperty("client_timestamp")
    private Long clientTimestamp;
}
