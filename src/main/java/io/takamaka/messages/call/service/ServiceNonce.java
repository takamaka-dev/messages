package io.takamaka.messages.call.service;

import io.takamaka.messages.call.CallBytes;
import io.takamaka.messages.call.CallConstants;
import io.takamaka.messages.call.CallCrypto;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The stateless service nonce of spec §8.1 (N20):
 *
 * <pre>
 * nonce = BE64(t_ms) ‖ HMAC-SHA-512(k_nonce, BE64(t_ms) ‖ ASCII(aud))[0..16)          — 24 bytes, hex
 * </pre>
 *
 * Valid for 60 s; a replay cache lives in memory for the validity window only. {@code k_nonce} is a per-instance
 * random key that changes at every restart (the constructor with an explicit key is for vectors and tests).
 *
 * <p>Interpretation (A-14 of the C182 status report): {@code aud} is the audience string of the object the nonce
 * will be carried in (e.g. {@code svc:<id>}); a nonce dated in the future is refused.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class ServiceNonce {

    /** Result of a check. */
    public enum Check {
        OK, MALFORMED, BAD_MAC, EXPIRED, FUTURE, REPLAYED
    }

    private final byte[] kNonce;
    private final Map<String, Long> seen = new LinkedHashMap<>();

    public ServiceNonce(SecureRandom random) {
        this.kNonce = new byte[32];
        random.nextBytes(kNonce);
    }

    /** Explicit key: vectors and tests only. */
    public ServiceNonce(byte[] kNonce) {
        this.kNonce = kNonce.clone();
    }

    public static byte[] compute(byte[] kNonce, long tMs, String aud) {
        byte[] t = CallBytes.be64(tMs);
        byte[] mac = CallCrypto.hmacSha512(kNonce, t, CallBytes.ascii(aud));
        return CallBytes.concat(t, Arrays.copyOf(mac, CallConstants.NONCE_MAC_LEN));
    }

    public String issue(long nowMs, String aud) {
        return CallBytes.hex(compute(kNonce, nowMs, aud));
    }

    /** Checks a nonce without consuming it. */
    public Check peek(String nonceHex, String aud, long nowMs) {
        byte[] n;
        try {
            n = CallBytes.unhex(nonceHex, 8 + CallConstants.NONCE_MAC_LEN);
        } catch (IllegalArgumentException ex) {
            return Check.MALFORMED;
        }
        long t = CallBytes.readBe64(n, 0);
        if (t < 0) {
            return Check.MALFORMED;
        }
        if (!CallCrypto.constantTimeEquals(n, compute(kNonce, t, aud))) {
            return Check.BAD_MAC;
        }
        if (t > nowMs) {
            return Check.FUTURE;
        }
        if (nowMs - t > CallConstants.NONCE_VALIDITY_MS) {
            return Check.EXPIRED;
        }
        return Check.OK;
    }

    /** Checks and consumes a nonce (first use wins; a second use inside the window is a replay). */
    public synchronized Check consume(String nonceHex, String aud, long nowMs) {
        Check c = peek(nonceHex, aud, nowMs);
        if (c != Check.OK) {
            return c;
        }
        evict(nowMs);
        if (seen.containsKey(nonceHex + "|" + aud)) {
            return Check.REPLAYED;
        }
        seen.put(nonceHex + "|" + aud, CallBytes.readBe64(CallBytes.unhex(nonceHex), 0));
        return Check.OK;
    }

    private void evict(long nowMs) {
        Iterator<Map.Entry<String, Long>> it = seen.entrySet().iterator();
        while (it.hasNext()) {
            if (nowMs - it.next().getValue() > CallConstants.NONCE_VALIDITY_MS) {
                it.remove();
            }
        }
    }
}
