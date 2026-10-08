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
 * The stateless service nonce of spec §8.1 (N20), draft 0.2:
 *
 * <pre>
 * nonce = BE64(t_ms) ‖ r ‖ HMAC-SHA-512(k_nonce, BE64(t_ms) ‖ r ‖ ASCII(aud))[0..16)      — 32 bytes, hex
 * </pre>
 *
 * with {@code r} = 8 random bytes per request. <b>[0.2]</b> The random field is mandatory: without it every requester
 * in the same millisecond received the same nonce and the replay cache refused all but the first (shell oracle
 * finding N-1). Valid for 60 s; a replay cache lives in memory for the validity window only. {@code k_nonce} is a
 * per-instance random key that changes at every restart (the constructors with an explicit key are for vectors and
 * tests).
 *
 * <p>Verification is stateless: the MAC is recomputed from the nonce's own {@code t} and {@code r}. Interpretation
 * (A-14 of the C182 status report): {@code aud} is the audience string of the object the nonce will be carried in (e.g.
 * {@code svc:<id>}); a nonce dated in the future is refused. The replay cache is keyed by nonce + aud and holds
 * nothing else: no call object, no identity (spec §8.7).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class ServiceNonce {

    /** Result of a check. */
    public enum Check {
        OK, MALFORMED, BAD_MAC, EXPIRED, FUTURE, REPLAYED
    }

    /** Total length in bytes: BE64(t) ‖ r ‖ MAC. */
    public static final int LENGTH = 8 + CallConstants.NONCE_RAND_LEN + CallConstants.NONCE_MAC_LEN;

    private final byte[] kNonce;
    private final SecureRandom random;
    private final Map<String, Long> seen = new LinkedHashMap<>();

    /** Production: a fresh random {@code k_nonce} per instance; {@code r} from the same CSPRNG. */
    public ServiceNonce(SecureRandom random) {
        this.random = random;
        this.kNonce = new byte[32];
        random.nextBytes(kNonce);
    }

    /** Explicit key: vectors and tests only ({@code r} still random; see {@link #issue(long, String, byte[])}). */
    public ServiceNonce(byte[] kNonce) {
        this(kNonce, new SecureRandom());
    }

    /** Explicit key and random source: vectors and tests only. */
    public ServiceNonce(byte[] kNonce, SecureRandom random) {
        this.kNonce = kNonce.clone();
        this.random = random;
    }

    /** {@code BE64(t) ‖ r ‖ HMAC-SHA-512(k, BE64(t) ‖ r ‖ ASCII(aud))[0..16)}. */
    public static byte[] compute(byte[] kNonce, long tMs, byte[] r, String aud) {
        if (r == null || r.length != CallConstants.NONCE_RAND_LEN) {
            throw new IllegalArgumentException("r must be " + CallConstants.NONCE_RAND_LEN + " bytes");
        }
        byte[] t = CallBytes.be64(tMs);
        byte[] mac = CallCrypto.hmacSha512(kNonce, t, r, CallBytes.ascii(aud));
        return CallBytes.concat(t, r, Arrays.copyOf(mac, CallConstants.NONCE_MAC_LEN));
    }

    /** A nonce for {@code aud} at {@code nowMs} with a fresh random {@code r}. */
    public String issue(long nowMs, String aud) {
        byte[] r = new byte[CallConstants.NONCE_RAND_LEN];
        random.nextBytes(r);
        return issue(nowMs, aud, r);
    }

    /** A nonce with an explicit {@code r}: vectors and tests only. */
    public String issue(long nowMs, String aud, byte[] r) {
        return CallBytes.hex(compute(kNonce, nowMs, r, aud));
    }

    /** Checks a nonce without consuming it. */
    public Check peek(String nonceHex, String aud, long nowMs) {
        byte[] n;
        try {
            n = CallBytes.unhex(nonceHex, LENGTH);
        } catch (IllegalArgumentException ex) {
            return Check.MALFORMED;
        }
        long t = CallBytes.readBe64(n, 0);
        if (t < 0) {
            return Check.MALFORMED;
        }
        byte[] r = Arrays.copyOfRange(n, 8, 8 + CallConstants.NONCE_RAND_LEN);
        if (!CallCrypto.constantTimeEquals(n, compute(kNonce, t, r, aud))) {
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

    /** Entries in the replay cache (tests: the cache empties itself after the validity window). */
    public synchronized int cached(long nowMs) {
        evict(nowMs);
        return seen.size();
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
