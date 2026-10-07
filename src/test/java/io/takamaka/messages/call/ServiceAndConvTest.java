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
        assertEquals(48, nonce.length());
        assertEquals(CallBytes.hex(CallBytes.be64(t)), nonce.substring(0, 16));
        byte[] mac = CallCrypto.hmacSha512(k, CallBytes.be64(t), CallBytes.ascii("svc:a"));
        assertEquals(CallBytes.hex(Arrays.copyOf(mac, 16)), nonce.substring(16));

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
