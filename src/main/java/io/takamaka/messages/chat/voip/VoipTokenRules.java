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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.utils.CHAT_MESSAGE_TYPES;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * The rules of the VoIP-token content that the server and every client must agree on (DR-074 §5.2, §5.4, §5.5): the
 * literals, the patterns, the error codes, the content check, and the {@code token_hash} preimage. Pure functions; no
 * state, no I/O.
 *
 * <p><b>Nothing here normalises.</b> A value is accepted as sent or refused: an uppercase token is refused, never
 * lowercased, so the string the signature covers is the string the server stores.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class VoipTokenRules {

    private VoipTokenRules() {
    }

    public static final String OP_REGISTER = "register";
    public static final String OP_UNREGISTER = "unregister";
    public static final String ENV_SANDBOX = "sandbox";
    public static final String ENV_PRODUCTION = "production";

    /** {@code vt}: lowercase hex, 64 to 200 characters; the even length is checked beside the pattern. */
    public static final Pattern VT = Pattern.compile("[0-9a-f]{64,200}");
    /** {@code tp}: a bundle id, without the {@code .voip} suffix. */
    public static final Pattern TP = Pattern.compile("[A-Za-z0-9.-]{1,155}");
    /** {@code did}: the per-install id (a UUID v4 or a SHA-256 hex both fit). */
    public static final Pattern DID = Pattern.compile("[A-Za-z0-9._-]{8,128}");

    /** The first line of the {@code token_hash} preimage. */
    public static final String TOKEN_HASH_DOMAIN = "tkm-voip-token-v1";

    // ---- error codes of VoipTokenResponseBean.error_code (§5.4) ----
    /**
     * A <b>retry-later</b> code, on the VoIP-token routes and on the four FCM token routes alike: a bucket was empty
     * (the connection's, taken before the signature, or the verified identity's). The request was NOT judged and its
     * nonce was NOT spent. A client retries after a pause (a bucket refills within a minute) with a fresh nonce, and
     * must not read it as «the release failed for good» (DR-069 point 13 counts FAILED releases, not delayed ones).
     * See {@link #isRetryLater(String)}.
     */
    public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";

    /** True for the one error code of the token routes that means «not judged, try again later». */
    public static boolean isRetryLater(String errorCode) {
        return RATE_LIMIT_EXCEEDED.equals(errorCode);
    }
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String SIGNATURE_ERROR = "SIGNATURE_ERROR";
    public static final String NONCE_INVALID = "NONCE_INVALID";
    public static final String VOIP_FIELD_MISSING = "VOIP_FIELD_MISSING";
    public static final String OP_MISMATCH = "OP_MISMATCH";
    public static final String AUD_MISMATCH = "AUD_MISMATCH";
    public static final String VOIP_DISABLED = "VOIP_DISABLED";
    public static final String VOIP_TOPIC_REFUSED = "VOIP_TOPIC_REFUSED";
    public static final String VOIP_ENV_REFUSED = "VOIP_ENV_REFUSED";
    public static final String VOIP_TOKEN_IDENTITY_CAP = "VOIP_TOKEN_IDENTITY_CAP";
    public static final String USER_NOT_REGISTERED = "USER_NOT_REGISTERED";
    /** The action itself failed after every check passed (a server fault). */
    public static final String VOIP_TOKEN_ERROR = "VOIP_TOKEN_ERROR";

    /** A refusal of the content check: the code and a message that names the member, never its value. */
    public record Refusal(String code, String message) {
    }

    public static boolean isOp(String s) {
        return OP_REGISTER.equals(s) || OP_UNREGISTER.equals(s);
    }

    public static boolean isEnv(String s) {
        return ENV_SANDBOX.equals(s) || ENV_PRODUCTION.equals(s);
    }

    public static boolean isVt(String s) {
        return s != null && (s.length() & 1) == 0 && VT.matcher(s).matches();
    }

    public static boolean isTp(String s) {
        return s != null && TP.matcher(s).matches();
    }

    public static boolean isDid(String s) {
        return s != null && DID.matcher(s).matches();
    }

    /**
     * The content check of §5.5, steps 5 to 8, in that order: every member present, {@code op} equal to the route's,
     * {@code aud} equal to this server's, then the patterns on the strings as sent. To be run AFTER the signature.
     * Reads nothing but its arguments.
     *
     * @param content the parsed signed content (may be null)
     * @param routeOp the operation of the route: {@link #OP_REGISTER} or {@link #OP_UNREGISTER}
     * @param serverAudience this server's audience, {@code rschat:<net>}
     * @return null when the content passes, else the refusal
     */
    public static Refusal check(VoipTokenSignedContentBean content, String routeOp, String serverAudience) {
        if (content == null) {
            return new Refusal(VOIP_FIELD_MISSING, "voip_token_signed_content is required");
        }
        if (content.getNonce() == null || content.getNonce().getNonce() == null) {
            return new Refusal(VOIP_FIELD_MISSING, "nonce is required");
        }
        if (content.getOp() == null) {
            return new Refusal(VOIP_FIELD_MISSING, "op is required");
        }
        if (content.getAud() == null) {
            return new Refusal(VOIP_FIELD_MISSING, "aud is required");
        }
        if (content.getVt() == null) {
            return new Refusal(VOIP_FIELD_MISSING, "vt is required");
        }
        if (content.getEnv() == null) {
            return new Refusal(VOIP_FIELD_MISSING, "env is required");
        }
        if (content.getTp() == null) {
            return new Refusal(VOIP_FIELD_MISSING, "tp is required");
        }
        if (content.getDid() == null) {
            return new Refusal(VOIP_FIELD_MISSING, "did is required");
        }
        if (content.hasUnknownKeys()) {
            return new Refusal(VALIDATION_ERROR, "the signed content has a key outside the seven");
        }
        if (!isOp(routeOp) || !routeOp.equals(content.getOp())) {
            return new Refusal(OP_MISMATCH, "op is not this route's");
        }
        if (serverAudience == null || !serverAudience.equals(content.getAud())) {
            return new Refusal(AUD_MISMATCH, "aud is not this server's audience");
        }
        if (!isVt(content.getVt())) {
            return new Refusal(VALIDATION_ERROR, "vt must be lowercase hex, even length, 64 to 200 characters");
        }
        if (!isEnv(content.getEnv())) {
            return new Refusal(VALIDATION_ERROR, "env must be sandbox or production");
        }
        if (!isTp(content.getTp())) {
            return new Refusal(VALIDATION_ERROR, "tp must match [A-Za-z0-9.-]{1,155}");
        }
        if (!isDid(content.getDid())) {
            return new Refusal(VALIDATION_ERROR, "did must match [A-Za-z0-9._-]{8,128}");
        }
        return null;
    }

    /**
     * Step 3 of §5.5: the Ed25519 signature over the canonical form re-derived from the PARSED content, through the
     * type's own case of {@code ChatCryptoUtils.verifySignedMessage}. A request whose {@code message_type} is not
     * {@code VOIP_TOKEN_REGISTRATION}, or that carries no {@code voip_token_signed_content}, is refused here: nothing
     * else in the envelope is covered by a signature.
     *
     * @return null when the signature verifies, else the {@link #SIGNATURE_ERROR} refusal
     */
    public static Refusal verifySignature(VoipTokenRequestBean request) {
        final Refusal refused = new Refusal(SIGNATURE_ERROR, "Invalid request signature");
        if (request == null || !CHAT_MESSAGE_TYPES.VOIP_TOKEN_REGISTRATION.name().equals(request.getMessageType())) {
            return refused;
        }
        try {
            Object verified = ChatCryptoUtils.verifySignedMessage(TkmTextUtils.getJacksonMapper().writeValueAsString(request));
            return verified instanceof VoipTokenRequestBean ? null : refused;
        } catch (ChatMessageException | JsonProcessingException | RuntimeException ex) {
            return refused;
        }
    }

    /**
     * The exact bytes hashed by {@link #tokenHash}: the UTF-8 bytes of {@code tkm-voip-token-v1} LF {@code env} LF
     * {@code tp} LF {@code vt}, LF = the single byte {@code 0x0A}, no trailing LF. The three patterns exclude LF, so
     * two different triples never share a preimage.
     */
    public static byte[] tokenHashPreimage(String env, String tp, String vt) {
        if (!isEnv(env) || !isTp(tp) || !isVt(vt)) {
            throw new IllegalArgumentException("env, tp and vt must pass their patterns");
        }
        return (TOKEN_HASH_DOMAIN + "\n" + env + "\n" + tp + "\n" + vt).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * {@code token_hash}: lowercase hex of SHA-256 over {@link #tokenHashPreimage}. It names the device endpoint (the
     * identity is NOT in it), so it is equal across the identities of one install.
     */
    public static String tokenHash(String env, String tp, String vt) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(tokenHashPreimage(env, tp, vt)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
