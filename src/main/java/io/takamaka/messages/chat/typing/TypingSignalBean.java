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
 * Outbound typing signal delivered to subscribers' typing sinks
 * (TYPING_INDICATOR_DESIGN.md D2/D10). PLAIN (not signed): receivers trust the
 * server's attribution (DR-007 — the one ecosystem signal whose outbound
 * attribution is server-trusted, accepted for an ephemeral ~2 s presence hint).
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Per-identity</b> (small conversations): {@code from} = the typing
 *       member's pubkey, {@code active = true}. The client renders "X is
 *       typing…" and expires it after a 2 s TTL (no explicit off frame, D6).</li>
 *   <li><b>Fuzzy aggregate</b> (conversations above {@code typing.fuzzy-threshold},
 *       D10): {@code from = null}, {@code active} toggles the per-conversation
 *       boolean "someone is typing" on its first-typer / all-expired
 *       transitions — no individual identities propagated.</li>
 * </ul>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TypingSignalBean {

    /** Which conversation (64-hex routing key). */
    @JsonProperty("conv")
    private String conversationHashName;

    /**
     * The typing member's pubkey (set by the SERVER from the bound identity, D3).
     * {@code null} ⇒ this is a fuzzy aggregate signal (no identity, D10).
     */
    @JsonProperty("from")
    private String senderPublicKey;

    /** Feature/format protocol version. */
    @JsonProperty("pv")
    private String protocolVersion;

    /**
     * Per-identity: always {@code true}. Aggregate: {@code true} when the
     * conversation flips to "someone typing", {@code false} when all typers
     * expire.
     */
    @JsonProperty("active")
    private boolean active;
}
