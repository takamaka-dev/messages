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
 * Signed content (the {@code pl}) for a {@code setuserprofile} write. The
 * Ed25519 signature in the enclosing envelope covers {@code canonical(pl)}.
 *
 * <p>Mechanism: {@code USER_PROFILE_DESIGN.md} §6.2, D4/D5. One request carries
 * both the sealed card and the grants for it, so a reader that can see the new
 * blob can already unwrap it — there is no window in which a peer holds a blob
 * it cannot open.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SetUserProfileSignedContentBean {

    /**
     * Server-issued nonce. Supplies replay immunity AND, through its issue
     * time, the AUTHORITATIVE ordering clock — the phone and the desktop both
     * editing a status message are ordered by the server, never by their own
     * clocks (design D5).
     */
    @JsonProperty("nonce")
    private NonceResponseBean nonce;

    /**
     * The sealed card. Opaque to the server.
     */
    @JsonProperty("profile")
    private EncryptedProfileBean profile;

    /**
     * The grants for {@code profile.key_epoch} — one per peer permitted to
     * read.
     *
     * <p>A client SHOULD refresh grants on EVERY profile write, not only on
     * rotation: that is what closes the re-registration hole for any actively
     * used profile (design §7.6/D12).</p>
     */
    @JsonProperty("grants")
    private List<ProfileGrantBean> grants;

    /**
     * Client-asserted write time. ADVISORY/audit only — never authoritative for
     * ordering (design D5).
     */
    @JsonProperty("client_timestamp")
    private Long clientTimestamp;
}
