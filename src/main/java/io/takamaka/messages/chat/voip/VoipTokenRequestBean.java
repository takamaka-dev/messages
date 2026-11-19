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
import io.takamaka.messages.chat.core.SignedMessageBean;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * The envelope of {@code registervoiptoken} / {@code unregistervoiptoken} (DR-074 §5.3): the standard signed envelope
 * plus the content under {@code voip_token_signed_content}. {@code message_type} is
 * {@code VOIP_TOKEN_REGISTRATION} on both routes; the signature covers the content only, so what tells the two
 * operations apart is {@code op} INSIDE the content.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class VoipTokenRequestBean extends SignedMessageBean {

    @JsonProperty("voip_token_signed_content")
    private VoipTokenSignedContentBean voipTokenSignedContentBean;

    public VoipTokenRequestBean(VoipTokenSignedContentBean voipTokenSignedContentBean, String from, String signature,
            String messageType, String signatureType) {
        super(from, signature, messageType, signatureType);
        this.voipTokenSignedContentBean = voipTokenSignedContentBean;
    }
}
