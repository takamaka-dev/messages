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
package io.takamaka.messages.chat.options;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ACK for a {@code setuseroption} / {@code resetuseroptions} write (D9). Echoes
 * the request signature so a client can correlate the ACK to a specific
 * in-flight write, and carries {@code applied} + machine-readable {@code error}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserOptionResponseBean {

    /** Common machine-readable error codes (null on success). */
    public static final String ERR_STALE_NONCE = "stale_nonce";
    public static final String ERR_BELOW_RESET_WATERMARK = "below_reset_watermark";
    public static final String ERR_UNKNOWN_PARAMETER = "unknown_parameter";
    public static final String ERR_UNKNOWN_VERSION = "unknown_version";
    public static final String ERR_OFF_SCHEMA = "off_schema";
    public static final String ERR_INVALID_SIGNATURE = "invalid_signature";
    public static final String ERR_RATE_LIMITED = "rate_limited";
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

    public static UserOptionResponseBean applied(String requestSignature) {
        return new UserOptionResponseBean(requestSignature, true, null);
    }

    public static UserOptionResponseBean rejected(String requestSignature, String error) {
        return new UserOptionResponseBean(requestSignature, false, error);
    }
}
