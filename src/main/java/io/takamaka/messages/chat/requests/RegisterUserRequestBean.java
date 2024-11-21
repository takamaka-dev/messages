/*
 * Copyright 2024 AiliA SA.
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
package io.takamaka.messages.chat.requests;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.chat.SignedMessageBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RegisterUserRequestBean extends SignedMessageBean {

    public RegisterUserRequestBean(RegisterUserRequestSignedContentBean registerUserRequestSignedContentBean, String from, String signature, String type) {
        super(from, signature, type);
        this.registerUserRequestSignedContentBean = registerUserRequestSignedContentBean;
    }

    @JsonProperty("register-user-request-signed-content")
    private RegisterUserRequestSignedContentBean registerUserRequestSignedContentBean;
}