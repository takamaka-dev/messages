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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.chat.core.NonceResponseBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Signed content (the {@code pl}) for a {@code setuseroption} write. The
 * Ed25519 signature in the enclosing envelope covers {@code canonical(pl)}.
 *
 * <p>Authoritative mechanism: {@code USER_OPTIONS_DESIGN.md} §6.2. The
 * server-issued {@link NonceResponseBean} supplies the authoritative ordering
 * clock (its issue time) and single-use replay immunity (D4/D5).</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SetUserOptionSignedContentBean {

    /**
     * Server-issued nonce; supplies the authoritative write time and replay
     * protection. Validated and consumed atomically with the write.
     */
    @JsonProperty("nonce")
    private NonceResponseBean nonce;

    /**
     * The option's immutable wire key (validation-matrix key).
     */
    @JsonProperty("pn")
    private String parameterName;

    /**
     * Schema version of the value (validation-matrix key).
     */
    @JsonProperty("v")
    private String version;

    /**
     * The option value as a JSON string, validated against the matrix entry for
     * {@code (parameterName, version)}.
     */
    @JsonProperty("val")
    private String parameterJson;

    /**
     * Client-asserted action time. ADVISORY/audit only — never authoritative
     * for ordering (the nonce issue time is, D5).
     */
    @JsonProperty("cts")
    private Long clientTimestamp;
}
