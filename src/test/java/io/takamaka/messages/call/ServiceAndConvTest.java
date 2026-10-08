package io.takamaka.messages.call;

import io.takamaka.messages.call.beans.CallGrantBean;
import io.takamaka.messages.call.conv.ConvSeed;
import io.takamaka.messages.call.service.ServiceNonce;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServiceAndConvTest {

    static CallVectorScenario s;

    @BeforeAll
    static void build() throws Exception {
        s = new CallVectorScenario();
    }

    @Test
    void nonceLayoutAndChecks() {
        byte[] k = CallTestKeys.seed("k_nonce");
        ServiceNonce n = new ServiceNonce(k);
        long t = 1_800_000_000_000L;
        String nonce = n.issue(t, "svc:a");
        assertEquals(64, nonce.length(), "[0.2] BE64(t) || r(8) || MAC(16)");
        assertEquals(CallBytes.hex(CallBytes.be64(t)), nonce.substring(0, 16));
        byte[] r = CallBytes.unhex(nonce.substring(16, 32));
        byte[] mac = CallCrypto.hmacSha512(k, CallBytes.be64(t), r, CallBytes.ascii("svc:a"));
        assertEquals(CallBytes.hex(Arrays.copyOf(mac, 16)), nonce.substring(32));
        // explicit r reproduces the layout; r is covered by the MAC
        assertEquals(nonce, n.issue(t, "svc:a", r));
        byte[] r2 = r.clone();
        r2[0] ^= 1;
        String forged = nonce.substring(0, 16) + CallBytes.hex(r2) + nonce.substring(32);
        assertEquals(ServiceNonce.Check.BAD_MAC, n.peek(forged, "svc:a", t + 1000), "r is authenticated");
        // the old 24-byte form is malformed
        assertEquals(ServiceNonce.Check.MALFORMED, n.peek(nonce.substring(0, 16) + nonce.substring(32), "svc:a", t + 1000));

        assertEquals(ServiceNonce.Check.OK, n.consume(nonce, "svc:a", t + 1000));
        assertEquals(ServiceNonce.Check.REPLAYED, n.consume(nonce, "svc:a", t + 2000));
        assertEquals(ServiceNonce.Check.BAD_MAC, n.consume(nonce, "svc:b", t + 1000));
        assertEquals(ServiceNonce.Check.EXPIRED, n.consume(n.issue(t, "svc:a"), "svc:a", t + 60_001));
        assertEquals(ServiceNonce.Check.FUTURE, n.consume(n.issue(t + 5000, "svc:a"), "svc:a", t));
        assertEquals(ServiceNonce.Check.MALFORMED, n.consume("00", "svc:a", t));
        assertEquals(ServiceNonce.Check.BAD_MAC, new ServiceNonce(CallTestKeys.seed("other")).consume(n.issue(t, "svc:a"), "svc:a", t));
        // after the window the replay cache entry is evicted, but the nonce itself has expired
        assertEquals(ServiceNonce.Check.EXPIRED, n.consume(nonce, "svc:a", t + 61_000));
    }

    /**
     * [0.2] N-1 regression: many requesters in the SAME millisecond get distinct nonces, and every one of them is
     * consumable once (before the fix: 8 simultaneous requests gave 1–2 distinct values and the replay cache refused
     * the rest).
     */
    @Test
    void sameMillisecondNoncesAreDistinctAndEachConsumableOnce() {
        ServiceNonce n = new ServiceNonce(new java.security.SecureRandom());
        long t = 1_800_000_000_000L;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String nonce = n.issue(t, "svc:a");
            assertTrue(seen.add(nonce), "distinct in the same millisecond");
            assertEquals(ServiceNonce.Check.OK, n.consume(nonce, "svc:a", t));
        }
        assertEquals(1000, n.cached(t));
        assertEquals(0, n.cached(t + 60_001), "the replay cache lives for the validity window only");
    }

    @Test
    void grantShape() throws Exception {
        CallGrantBean g = s.grantB;
        CallSignatures.verify(g, "grant", "leg:" + CallBytes.hex(s.legs.get("bob").legId), s.id("service"));
        assertEquals(g.getTs() + 60_000, g.getExp());
        assertEquals(1, g.getLegIndex());
        assertEquals("free", g.getTier());
        assertEquals(1500, g.getCaps().getHqKbps());
        assertEquals(s.create.getParams(), g.getParams());
    }

    @Test
    void convSeed() {
        byte[] seed = s.convSeed;
        assertTrue(ConvSeed.matches(seed, s.create.getCseed()));
        assertFalse(ConvSeed.matches(CallTestKeys.seed("x"), s.create.getCseed()));
        assertTrue(ConvSeed.title(seed).matches("call-[0-9a-f]{16}"));
        assertEquals("call-" + CallBytes.hex(Arrays.copyOf(CallCrypto.hkdf(seed, CallBytes.ascii("tkm-call/v1/conv/title")), 8)), ConvSeed.title(seed));
        assertArrayEquals(CallCrypto.hkdf(seed, CallBytes.ascii("tkm-call/v1/conv/key")), ConvSeed.key(seed));
        assertArrayEquals(CallCrypto.hkdf(seed, CallBytes.ascii("tkm-call/v1/conv/salt")), ConvSeed.salt(seed));
        assertThrows(IllegalArgumentException.class, () -> ConvSeed.key(new byte[31]));
    }
}
