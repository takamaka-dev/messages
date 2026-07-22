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
 * <h3>Transport characteristics (manifest v1.1)</h3>
 * <p>Since manifest {@code 1.1} the probe also advertises the server's
 * <b>transport limits</b> — {@link #maxFramePayloadLength} and
 * {@link #maxAttachmentSizeBytes} — so a client can size its upload chunk and
 * reject oversized files <em>before</em> a transfer silently tears down on the
 * frame codec. These are <b>advisory hints on an UNSIGNED probe</b>: a client
 * MUST validate them against its own sane bounds and clamp to its own decoder
 * limit, never adopt them blindly (see the client {@code resolve()} policy).
 * A client reading an older server that omits them (value {@code 0} / absent
 * {@link #manifestVersion}) MUST fall back to conservative defaults.</p>
 *
 * <h3>Rate-limit hints (manifest v1.2)</h3>
 * <p>Since manifest {@code 1.2} the probe also advertises the per-user
 * <b>message-send rate-limit budget</b> — {@link #messageBurst} (instantaneous
 * burst capacity) and {@link #messagePerMinute} (sustained rate) — so a client
 * can render a submission-burst meter and self-throttle before the server
 * rejects. Like the transport hints these are <b>advisory on an UNSIGNED
 * probe</b>: informational only (they gate nothing on the client), and a client
 * reading an older server that omits them (value {@code 0}) simply shows no
 * meter. The server may still reject regardless of these hints.</p>
 *
 * <p>This manifest carries its <b>own</b> {@link #manifestVersion}, deliberately
 * decoupled from {@code MessageProtocolVersion} (the signed-message wire
 * protocol): growing the capability probe must NOT perturb message signing or
 * the client/server compatibility gate. Bump {@link #MANIFEST_VERSION_CURRENT}
 * when fields are added here; keep it additive and backward-compatible.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServerInfoResponseBean {

    /**
     * Current capability-manifest schema version. Bump on additive field
     * changes to this bean. Decoupled from {@code MessageProtocolVersion}.
     * Absent on pre-1.1 servers ⇒ clients treat as {@code "1.0"}.
     */
    public static final String MANIFEST_VERSION_CURRENT = "1.2";

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

    /**
     * Capability-manifest schema version (see {@link #MANIFEST_VERSION_CURRENT}).
     * Its own axis, NOT the message wire protocol. Absent/empty ⇒ treat as
     * {@code "1.0"} (a server that predates the transport-characteristics fields).
     */
    private String manifestVersion;
    /**
     * Effective RSocket-over-WebSocket max frame payload the server accepts, in
     * BYTES, sourced from the same {@code spring.rsocket.server.spec.max-frame-payload-length}
     * property that configures the codec (single source of truth). {@code 0} ⇒
     * not advertised (older server) — client assumes a conservative default.
     */
    private long maxFramePayloadLength;
    /**
     * Max single-attachment size the server accepts, in BYTES
     * ({@code rschat.file-upload.max-attachment-size-bytes}). {@code 0} ⇒ not
     * advertised (older server). Advisory: clients may pre-reject oversized files.
     */
    private long maxAttachmentSizeBytes;

    /**
     * Per-user message-send rate-limit BURST (bucket capacity) for the {@code messages}
     * endpoint — the max instantaneous run of sends before throttling. Manifest {@code 1.2+}.
     * {@code 0} ⇒ not advertised (older server); the client shows no submission meter.
     * Advisory only — informational, gates nothing client-side.
     */
    private int messageBurst;
    /**
     * Per-user message-send SUSTAINED rate for the {@code messages} endpoint, in sends per
     * minute (the greedy token refill). Manifest {@code 1.2+}. {@code 0} ⇒ not advertised.
     * Advisory only.
     */
    private int messagePerMinute;

}
