/*
 * Copyright 2026 AiliA SA.
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
package io.takamaka.messages.chat.voip;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The answer of {@code registervoiptoken} / {@code unregistervoiptoken} (DR-074 §5.4): the shape of
 * {@code FcmTokenRegistrationResponseBean}. Never an error signal on the stream; always a bean. The codes are in
 * {@link VoipTokenRules}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VoipTokenResponseBean {

    private boolean success;
    private String message;
    /** Null on success. */
    @JsonProperty("error_code")
    private String errorCode;
    /** The server clock at the accepted call, epoch ms. Null on an error. */
    @JsonProperty("registration_time")
    private Long registrationTime;

    public static VoipTokenResponseBean success(String message, Long registrationTime) {
        return new VoipTokenResponseBean(true, message, null, registrationTime);
    }

    public static VoipTokenResponseBean error(String errorCode, String message) {
        return new VoipTokenResponseBean(false, message, errorCode, null);
    }
}
