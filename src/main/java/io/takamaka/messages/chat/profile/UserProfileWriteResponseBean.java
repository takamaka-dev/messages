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
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ACK for a {@code setuserprofile} / {@code putprofilegrants} /
 * {@code clearuserprofile} write. Echoes the request signature so a client can
 * correlate the ACK to a specific in-flight write, and carries {@code applied}
 * plus a machine-readable {@code error}.
 *
 * <p>Mirrors {@code UserOptionResponseBean}, including the reason it exists: a
 * raw error signal from an FK violation would otherwise reach the client
 * carrying the SQL.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfileWriteResponseBean {

    /** The envelope signature did not verify against {@code from}. */
    public static final String ERR_INVALID_SIGNATURE = "invalid_signature";
    /**
     * The nonce was spent or expired, OR the write lost the last-writer-wins
     * comparison. Both mean the same thing to a client: the write did not
     * apply. Refetch, re-derive, retry with a FRESH nonce — the rejected write
     * already consumed the old one.
     */
    public static final String ERR_STALE_NONCE = "stale_nonce";
    /** The envelope's structure is wrong (e.g. the blob is not decodable base64). */
    public static final String ERR_OFF_SCHEMA = "off_schema";
    /** The blob exceeds {@code rschat.profile.max-blob-b64-chars}. */
    public static final String ERR_TOO_LARGE = "too_large";
    /** {@code blob_version} is not in {@code allowed-blob-versions}. */
    public static final String ERR_UNKNOWN_VERSION = "unknown_version";
    /** {@code cipher} is not in {@code allowed-ciphers}. */
    public static final String ERR_UNKNOWN_CIPHER = "unknown_cipher";
    /** More grants than {@code max-grants-per-write}, or than the per-user cap. */
    public static final String ERR_TOO_MANY_GRANTS = "too_many_grants";
    /**
     * A grantee is not registered on this server. Returned INSTEAD of letting
     * the grant table's foreign key fail: an FK violation would surface as a
     * raw error signal carrying the SQL.
     */
    public static final String ERR_UNKNOWN_GRANTEE = "unknown_grantee";
    /**
     * {@code putprofilegrants} named an epoch that is not the stored blob's.
     * Rejected rather than ignored — grants that unwrap a superseded key are
     * worthless, and accepting them quietly would leave a client believing it
     * had granted access it had not.
     */
    public static final String ERR_EPOCH_MISMATCH = "epoch_mismatch";
    /** The caller exceeded the {@code write-profile} bucket. */
    public static final String ERR_RATE_LIMITED = "rate_limited";
    /**
     * The signing identity is not registered on this server, so the profile
     * tables have no user row to reference. Register first, then retry with a
     * FRESH nonce.
     */
    public static final String ERR_USER_NOT_REGISTERED = "user_not_registered";
    /** Anything else. Never carries server internals to the client. */
    public static final String ERR_INTERNAL = "internal_error";

    /**
     * Echo of the request envelope signature (ACK correlation).
     */
    @JsonProperty("request_signature")
    private String requestSignature;

    /**
     * Whether the write was applied.
     */
    @JsonProperty("applied")
    private boolean applied;

    /**
     * Machine-readable error code; {@code null} on success.
     */
    @JsonProperty("error")
    private String error;

    public static UserProfileWriteResponseBean applied(String requestSignature) {
        return new UserProfileWriteResponseBean(requestSignature, true, null);
    }

    public static UserProfileWriteResponseBean rejected(String requestSignature, String error) {
        return new UserProfileWriteResponseBean(requestSignature, false, error);
    }
}
