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
package io.takamaka.messages.chat.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response of the public {@code serverinfo} endpoint: the server build version
 * and the supported wire-protocol range.
 *
 * <p>This is an unauthenticated capabilities probe (no nonce, no signature). It
 * lets a client decide up front whether the server speaks a compatible protocol
 * instead of discovering a mismatch per-message. The protocol fields are sourced
 * from {@code MessageProtocolVersion}; the supported range a client can rely on
 * is {@code protocolMajor.protocolMinMinor}..{@code protocolMajor.protocolMaxMinor}.</p>
 *
 * <p>The response is intentionally static (no timestamp): server time is already
 * provided by the {@code nonce} endpoint via {@link NonceResponseBean}.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServerInfoResponseBean {

    /** Server build version, e.g. {@code "0.5.0-SNAPSHOT"} (rschat Maven version). */
    private String serverVersion;
    /** Current wire-protocol version, e.g. {@code "1.1"}. */
    private String protocolCurrent;
    /** Major component of the supported protocol family, e.g. {@code 1}. */
    private int protocolMajor;
    /** Oldest accepted MINOR within {@link #protocolMajor} (legacy floor). */
    private int protocolMinMinor;
    /** Newest accepted MINOR within {@link #protocolMajor} (current). */
    private int protocolMaxMinor;

}
