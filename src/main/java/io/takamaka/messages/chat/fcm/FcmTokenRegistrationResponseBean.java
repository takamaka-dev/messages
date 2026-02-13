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
