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
/*
 * Unknown fields are IGNORED, and that is load-bearing rather than defensive tidiness.
 *
 * The manifest's entire premise is that it grows additively: a server may advertise a field this client
 * has never heard of, and the client must still read the fields it DOES know. Without this, adding any
 * field here breaks every older client whose decoder happens to be strict — and the decoder is not ours to
 * assume: rsclient deserialises through RSocket's codec (DefaultCalls.retrieveMono), not through the
 * lenient ChatUtils mapper. Putting the guarantee on the BEAN makes it a property of the contract instead
 * of something every consumer must remember to configure.
 */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServerInfoResponseBean {

    /**
     * Current capability-manifest schema version. Bump on additive field
     * changes to this bean. Decoupled from {@code MessageProtocolVersion}.
     * Absent on pre-1.1 servers ⇒ clients treat as {@code "1.0"}.
     */
    public static final String MANIFEST_VERSION_CURRENT = "1.8";

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

    /**
     * DR-025: how long after a message was sent its OWNER may still "delete for everyone", in
     * MILLISECONDS ({@code rschat.message.edit-delete-window-ms}, default 48h). Manifest {@code 1.3+}.
     * {@code 0} ⇒ not advertised (older server).
     *
     * <p><b>Why the client needs it.</b> The window is operator-configurable, so a client that hardcodes
     * "2 days" silently drifts the moment it is retuned — offering a delete that can only be rejected, or
     * hiding one that would have worked. With the real value a client can stop offering
     * "delete for everyone" on a message that is already too old and point the user at a local delete
     * instead, which is the action still available to them.</p>
     *
     * <p><b>Advisory, never enforcement.</b> The server checks its OWN clock against the server-assigned
     * {@code messages.message_timestamp}; a client gate is a courtesy that avoids a doomed round trip. Near
     * the boundary the two can disagree, so a client must still handle {@code window_expired} gracefully
     * rather than treating this value as authoritative.</p>
     */
    private long editDeleteWindowMs;

    /**
     * Manifest {@code 1.4+}: the chat routes this build actually serves — the {@code @MessageMapping}
     * values verbatim, e.g. {@code "retrievedeletions"}. Absent/empty ⇒ a server that predates this field;
     * a client MUST then fall back to trying the call, never to assuming the route is missing.
     *
     * <p><b>Why a route SET and not another version bump.</b> A monotonic version can only say
     * "everything up to N", which assumes deployments are clean cumulative points on a line. They are not.
     * On 2026-07-30 the test VM served {@code deletemessage} but NOT {@code retrievedeletions}, while its
     * schema was fully migrated — a mid-branch build that no single version number can describe honestly:
     * it would have to under-report (disabling features that work) or over-report (lying). Three distinct
     * jars all reporting {@code 0.8.2-SNAPSHOT} existed simultaneously. A set states what is true.</p>
     *
     * <p><b>What it is FOR.</b> Distinguishing "this server cannot do X" from "X failed this time".
     * {@code retrievedeletions} is the case that motivated it: a client whose deletion catch-up quietly
     * swallows the error believes it is caught up when it never was, so a peer's honoured delete is
     * silently not applied — a correctness and privacy consequence, not a cosmetic one. It also turns a
     * whole class of false-pass test runs into visible skips.</p>
     *
     * <p><b>Advisory, like every other field here (DR-023): UNSIGNED.</b> A relay can add or remove
     * entries. So a client may use it to explain and to skip, never to decide something is safe — the
     * route must still be handled as absent at runtime, and a route listed here can still fail. Treat it
     * as a hint that upgrades a SILENT degrade into an informed one, not as a capability guarantee.</p>
     */
    private java.util.Set<String> supportedRoutes;

    /**
     * Manifest {@code 1.5+}: the server's hard cap on one message's ENCRYPTED content, in BYTES
     * ({@code rschat.validation.max-encrypted-message-content-size-bytes}, default 1 048 576).
     * {@code 0} ⇒ not advertised (older server).
     *
     * <p><b>Why the client needs it.</b> Until §PREVIEW-CONFORMANCE W5 this number appeared in no
     * manifest and in no client: greps across all four Java clients returned zero. So the one limit
     * that actually rejects an oversized message was invisible to every producer, and the only way
     * to discover it was to send a message and have the server refuse it — after the attachment had
     * been encrypted and uploaded. A client that knows the figure can refuse before that work.</p>
     *
     * <p><b>What it bounds.</b> The serialized JSON of the encrypted {@code EncMessageBean}, i.e.
     * AFTER encryption and base64 — roughly 1.8x the decoded payload it carries. That factor is
     * what {@link io.takamaka.messages.chat.attachment.InlineContentLimits#MAX_TOTAL_PREVIEW_BYTES}
     * is derived from; a client that reads this field should re-derive rather than compare against
     * it directly.</p>
     *
     * <p><b>Advisory, like every other field here (DR-023): UNSIGNED.</b> A relay can lie. Use it
     * to pre-reject and to explain, never to conclude that a message will be accepted — the server
     * still checks, and a client must handle its refusal. Where this value and a compiled-in
     * constant disagree, the SMALLER is the safe one to enforce locally.</p>
     */
    private long maxEncryptedContentSizeBytes;

    /**
     * Manifest {@code 1.6+} (DR-032): the user-profile channel's server-enforced
     * limits, or {@code null} on a server without the channel.
     *
     * <p>A nested object rather than another ten flat fields — the flat shape is
     * already at its readable limit, and these ten belong together: they are one
     * feature's envelope, and a client either speaks this channel or does not.</p>
     *
     * <p><b>Absent does not mean "unlimited", it means "no channel".</b> The
     * authoritative check for availability is {@code supportedRoutes}; this field
     * tells a client that already knows the routes exist what shape to produce.
     * Advisory and UNSIGNED like every other field here (DR-023) — clamp to the
     * SMALLER of this and the compiled-in constant.</p>
     */
    private io.takamaka.messages.chat.profile.ProfileLimitsBean profile;

    /**
     * The server audience, the ASCII string {@code rschat:<net>} ({@code CallRschat.audience}): the value the signed
     * {@code aud} of the VoIP-token routes (DR-074 §5.8) and of the call beans is compared with, byte for byte.
     * Manifest {@code 1.7+}: always present, never empty, whatever the calls switch and the APNs switch say, so a
     * client can sign {@code unregistervoiptoken} when {@code callnonce} refuses. {@code null} &rArr; a server older
     * than 1.7 (it has no VoIP route either). Unsigned, like the whole manifest: a client with a pinned audience for
     * this server prefers the pin.
     */
    private String audience;

    /**
     * Manifest {@code 1.8+} (DR-074 amendment 1, A-17): the {@code cap} values this server VALIDATES, STORES and
     * HONOURS at {@code registerfcmtoken}, e.g. {@code ["calls"]} ({@code FcmCapabilities.CALLS}). A wire and storage
     * capability, advertised whatever the calls, FCM and APNs switches say.
     *
     * <p><b>It is NOT «calls are available».</b> A client must never read it so: that is the job of the call-service
     * pin and the call manifest. A client sends {@code cap} = (its own capabilities) &cap; (this set, read on this
     * connection), never more (A-18). {@code null} or empty &rArr; the server knows no value (a server older than
     * 1.8 refuses any {@code cap} with {@code SIGNATURE_ERROR}): send no {@code cap}. Unsigned, like the whole
     * manifest: a relay that removes a value costs the device that capability; one that adds a value makes the server
     * refuse ({@code INVALID_CAP}) and the client retry once without {@code cap} (A-19, A-22).</p>
     *
     * <p><b>A set: the order on the wire is not significant.</b> Servers send it sorted (a {@code TreeSet}); a client
     * reads it as a set and never relies on the order. (The order of the SIGNED {@code cap} is a different rule:
     * strictly ascending, refused otherwise.)</p>
     */
    private java.util.Set<String> fcmTokenCapabilities;

    /**
     * DR-063 §3.3 (design F2 §3.1, PS-6): the sealed-push suites this server seals call pushes with, e.g.
     * {@code ["tkm-push/v1"]} ({@code io.takamaka.messages.call.PushSeal.SUITE}). Advertised ONLY when FCM is enabled
     * AND calls are enabled; otherwise absent. Wire name {@code push_seal_suites}; OMITTED when null or empty
     * ({@code NON_EMPTY} on this field only), so every existing manifest serialises byte-identically.
     *
     * <p>A client sends {@code push_key} / {@code push_seal} at {@code registerfcmtoken} only when this list holds its
     * suite: an older server would drop the unknown fields, re-canonicalise without them and refuse the signature.
     * The client keeps the value per endpoint in three states (DR-063 §4.4, PS-5): unknown (not read, or the read
     * failed: never drop a key because of it), none (absent or empty), listed. Unsigned, like the whole manifest.</p>
     */
    @com.fasterxml.jackson.annotation.JsonProperty("push_seal_suites")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
    private java.util.List<String> pushSealSuites;

    /**
     * The manifest without {@link #pushSealSuites} (the 1.8 field set); kept explicitly so existing callers (rschat's
     * {@code serverinfo} route) compile unchanged.
     */
    public ServerInfoResponseBean(String serverVersion, String protocolCurrent, int protocolMajor, int protocolMinMinor,
            int protocolMaxMinor, String manifestVersion, long maxFramePayloadLength, long maxAttachmentSizeBytes,
            int messageBurst, int messagePerMinute, long editDeleteWindowMs, java.util.Set<String> supportedRoutes,
            long maxEncryptedContentSizeBytes, io.takamaka.messages.chat.profile.ProfileLimitsBean profile,
            String audience, java.util.Set<String> fcmTokenCapabilities) {
        this(serverVersion, protocolCurrent, protocolMajor, protocolMinMinor, protocolMaxMinor, manifestVersion,
                maxFramePayloadLength, maxAttachmentSizeBytes, messageBurst, messagePerMinute, editDeleteWindowMs,
                supportedRoutes, maxEncryptedContentSizeBytes, profile, audience, fcmTokenCapabilities, null);
    }

}
