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
package io.takamaka.messages.chat.typing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Signed content ({@code pl}) of the one-time typing subscribe
 * (TYPING_INDICATOR_DESIGN.md D2). The subscribe is the ONLY signed call; after
 * it, the server binds the verified pubkey to the connection and emits are plain
 * (D3). Carries an advisory client timestamp for freshness; replay of the
 * opt-in is bounded by the connection binding + TLS (§9 open item — a nonce can
 * be added later as defense-in-depth, mirroring read-receipt F3).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TypingSubscribeSignedContentBean {

    /** Advisory client timestamp (freshness only). */
    @JsonProperty("ts")
    private Long clientTimestamp;

    /** Feature/format protocol version. */
    @JsonProperty("pv")
    private String protocolVersion;
}
