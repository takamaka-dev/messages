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
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Signed content for a {@code putprofilegrants} write: republish the grant set
 * for an EXISTING epoch without rewriting the blob — design §6.2.
 *
 * <p>This is the cheap half of grant maintenance. Joining a conversation adds
 * co-members who need a grant; a peer that re-registered needs a fresh wrap to
 * its new encryption key (D12). Neither changes the card, so neither should
 * pay for re-uploading ~93 KB of ciphertext.</p>
 *
 * <p>{@link #keyEpoch} must equal the stored blob's epoch — a mismatch is
 * {@code ERR_EPOCH_MISMATCH}, not a silent no-op. Grants that unwrap a
 * superseded key are worthless, and accepting them quietly would leave a
 * client believing it had granted access it had not.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PutProfileGrantsSignedContentBean {

    /**
     * Server-issued nonce: replay immunity and the authoritative ordering clock
     * (design D4/D5).
     */
    @JsonProperty("nonce")
    private NonceResponseBean nonce;

    /**
     * The epoch these grants unwrap. Must match the stored blob's epoch.
     */
    @JsonProperty("key_epoch")
    private long keyEpoch;

    /**
     * The grants to publish for {@link #keyEpoch}.
     */
    @JsonProperty("grants")
    private List<ProfileGrantBean> grants;

    /**
     * ADVISORY/audit only — never authoritative for ordering (design D5).
     */
    @JsonProperty("client_timestamp")
    private Long clientTimestamp;
}
