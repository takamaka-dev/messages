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
package io.takamaka.messages.chat.fcm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response bean for FCM token registration operations.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FcmTokenRegistrationResponseBean {

    /**
     * Indicates whether the registration was successful.
     */
    private boolean success;

    /**
     * Human-readable message describing the result.
     */
    private String message;

    /**
     * Error code if registration failed (null on success).
     */
    @JsonProperty("error_code")
    private String errorCode;

    /**
     * Timestamp when the token was registered (epoch millis).
     */
    @JsonProperty("registration_time")
    private Long registrationTime;

    /**
     * DR-063 §3.3: the key id of the push key the server STORED for this registration, lower hex of
     * {@code PushSeal.kid} (16 characters), computed by the server itself. Echoed only when stored; OMITTED otherwise
     * ({@code NON_NULL} on this field only). The echo is the device's proof that sealing is active for its row, and
     * what its «closed-app calls» state reads (DR-063 §4.6).
     */
    @JsonProperty("push_key_id")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String pushKeyId;

    /** The response without {@code push_key_id} (the form before DR-063); kept so existing callers compile unchanged. */
    public FcmTokenRegistrationResponseBean(boolean success, String message, String errorCode, Long registrationTime) {
        this(success, message, errorCode, registrationTime, null);
    }

    /**
     * True when the refusal is {@code RATE_LIMIT_EXCEEDED} (a bucket of the connection or of the identity was empty):
     * the request was not judged and its nonce was not spent. Retry later with a fresh nonce. Not a wire member.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isRetryLater() {
        return !success && "RATE_LIMIT_EXCEEDED".equals(errorCode);
    }

    /**
     * True when the refusal is {@link FcmCapabilities#ERROR_INVALID_CAP} (DR-074 amendment 1, A-13): the signed
     * {@code cap} broke the canonical form or named a value this server does not advertise. The nonce was not
     * spent. A client retries ONCE without {@code cap} (A-22) and never rotates its token on this code. Not a wire
     * member.
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isCapRefused() {
        return !success && FcmCapabilities.ERROR_INVALID_CAP.equals(errorCode);
    }

    /**
     * Create a success response.
     */
    public static FcmTokenRegistrationResponseBean success(Long registrationTime) {
        return new FcmTokenRegistrationResponseBean(
                true,
                "FCM token registered successfully",
                null,
                registrationTime
        );
    }

    /**
     * Create an error response.
     */
    public static FcmTokenRegistrationResponseBean error(String errorCode, String message) {
        return new FcmTokenRegistrationResponseBean(
                false,
                message,
                errorCode,
                null
        );
    }
}
