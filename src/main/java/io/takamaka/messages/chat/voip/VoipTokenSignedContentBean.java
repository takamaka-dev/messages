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

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.takamaka.messages.chat.core.NonceResponseBean;
import java.util.Set;
import java.util.TreeSet;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The signed content of {@code registervoiptoken} / {@code unregistervoiptoken} (DR-074 §5.2): the PushKit token of
 * one iOS install, registered by one identity.
 *
 * <p><b>All seven members are required and non-null.</b> The server fills in nothing and normalises nothing: a member
 * that is absent, {@code null} or outside its pattern is refused ({@link VoipTokenRules#check}). The JSON keys are the
 * Java field names. No float, no double (JCS number rule). No {@code @JsonPropertyOrder}: the order is the
 * canonicaliser's.</p>
 *
 * <p><b>No key outside the seven.</b> A key this bean does not know is collected by {@link #putUnknownKey} and never
 * written back, so (a) if the client signed it, the re-derived canonical form lacks it and the signature fails, and
 * (b) if it was added unsigned, the signature verifies and {@link VoipTokenRules#check} refuses the content. Either
 * way the content is refused, whatever the decoder's own unknown-property setting.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
public class VoipTokenSignedContentBean {

    /** The {@code NonceResponseBean} of the {@code nonce} route. Single use, consumed before the action. */
    private NonceResponseBean nonce;
    /** {@code register} or {@code unregister}: binds the operation inside the signature. */
    private String op;
    /** The server audience {@code rschat:<net>}, from {@code serverinfo.audience}. */
    private String aud;
    /** The PushKit token: lowercase hex, even length, 64 to 200 characters. Never normalised by the server. */
    private String vt;
    /** The APNs environment of the BUILD: {@code sandbox} or {@code production}. */
    private String env;
    /** The app's bundle id, without the {@code .voip} suffix. */
    private String tp;
    /** The per-install id the client also sends as FCM {@code device_id}. Used for the pairing only. */
    private String did;

    /** Keys of the wire object that are not one of the seven. Never serialised. */
    @JsonIgnore
    private Set<String> unknownKeys;

    public VoipTokenSignedContentBean(NonceResponseBean nonce, String op, String aud, String vt, String env, String tp,
            String did) {
        this.nonce = nonce;
        this.op = op;
        this.aud = aud;
        this.vt = vt;
        this.env = env;
        this.tp = tp;
        this.did = did;
    }

    @JsonAnySetter
    public void putUnknownKey(String key, Object value) {
        if (unknownKeys == null) {
            unknownKeys = new TreeSet<>();
        }
        unknownKeys.add(key);
    }

    /** True when the wire object carried a key outside the seven. */
    public boolean hasUnknownKeys() {
        return unknownKeys != null && !unknownKeys.isEmpty();
    }
}
