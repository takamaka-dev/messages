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
 * Response to {@code getuserprofilepeer}: the blob AND the caller's grant in
 * one round-trip — design D9/D11, status literals in registry §4.4.
 *
 * <p>Returning both together is deliberate. A peer that receives ciphertext it
 * cannot unwrap has spent a round-trip and ~93 KB to learn nothing, and the
 * obvious fix — a second call for the grant — doubles the cost of the one
 * operation this feature performs most.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfilePeerResponseBean {

    /**
     * The identity that was queried (Base64URL), echoed so a client can
     * correlate a response in a fan-out.
     */
    @JsonProperty("target")
    private String target;

    /**
     * One of {@link ProfileConstants#STATUS_VISIBLE},
     * {@link ProfileConstants#STATUS_UNCHANGED},
     * {@link ProfileConstants#STATUS_NO_GRANT},
     * {@link ProfileConstants#STATUS_CLEARED},
     * {@link ProfileConstants#STATUS_NOT_VISIBLE}.
     *
     * <p>{@code not_visible} answers BOTH "you share no conversation with this
     * identity" and "no such identity" — indistinguishable on purpose, so the
     * call cannot be used to enumerate the user table (D9).</p>
     */
    @JsonProperty("status")
    private String status;

    /**
     * The sealed card. Present only when {@code status == visible}: absent on
     * {@code unchanged} (the caller's cache is current), and absent on every
     * negative status.
     */
    @JsonProperty("profile")
    private EncryptedProfileBean profile;

    /**
     * The grant that unwraps {@link #profile} for THIS caller. Present only
     * when {@code status == visible}.
     */
    @JsonProperty("grant")
    private ProfileGrantBean grant;

    public static UserProfilePeerResponseBean visible(String target, EncryptedProfileBean profile, ProfileGrantBean grant) {
        return new UserProfilePeerResponseBean(target, ProfileConstants.STATUS_VISIBLE, profile, grant);
    }

    public static UserProfilePeerResponseBean unchanged(String target) {
        return new UserProfilePeerResponseBean(target, ProfileConstants.STATUS_UNCHANGED, null, null);
    }

    public static UserProfilePeerResponseBean noGrant(String target) {
        return new UserProfilePeerResponseBean(target, ProfileConstants.STATUS_NO_GRANT, null, null);
    }

    public static UserProfilePeerResponseBean cleared(String target) {
        return new UserProfilePeerResponseBean(target, ProfileConstants.STATUS_CLEARED, null, null);
    }

    public static UserProfilePeerResponseBean notVisible(String target) {
        return new UserProfilePeerResponseBean(target, ProfileConstants.STATUS_NOT_VISIBLE, null, null);
    }
}
