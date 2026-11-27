package io.takamaka.messages.call;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.takamaka.messages.call.channel.BcMlKem768;
import io.takamaka.messages.call.channel.MlKem768;
import java.io.InputStream;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FIPS 203 known answers for {@link BcMlKem768}. Fixture {@code call/mlkem768_acvp_subset.json}: ML-KEM-768 cases
 * copied verbatim from {@code spikes/c182-mlkem-java/data/acvp/} (NIST ACVP-Server, FIPS203 internalProjection,
 * vsId 42) — keyGen tc 26/27, encapsulation tc 26/27, decapsulation tc 86 (valid) and 88 (modified ciphertext →
 * implicit rejection), encapsulationKeyCheck tc 136 (valid) and 137 (coefficient ≥ q).
 */
class BcMlKem768KatTest {

    static JsonNode kat;
    final MlKem768 kem = new BcMlKem768();

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = BcMlKem768KatTest.class.getResourceAsStream("/call/mlkem768_acvp_subset.json")) {
            kat = new ObjectMapper().readTree(in);
        }
    }

    static byte[] hx(JsonNode n, String k) {
        return CallBytes.unhex(n.get(k).asText().toLowerCase());
    }

    @Test
    void keyGenInternal() {
        assertEquals(2, kat.get("keyGen").size());
        for (JsonNode t : kat.get("keyGen")) {
            MlKem768.KeyPair kp = kem.keyGen(hx(t, "d"), hx(t, "z"));
            assertArrayEquals(hx(t, "ek"), kp.ek(), "ek tc " + t.get("tcId"));
            assertArrayEquals(hx(t, "dk"), kp.dk(), "dk tc " + t.get("tcId"));
        }
    }

    @Test
    void encapsInternalWithM() {
        assertEquals(2, kat.get("encapsulation").size());
        for (JsonNode t : kat.get("encapsulation")) {
            MlKem768.Encapsulation e = kem.encaps(hx(t, "ek"), hx(t, "m"));
            assertArrayEquals(hx(t, "c"), e.ciphertext(), "c tc " + t.get("tcId"));
            assertArrayEquals(hx(t, "k"), e.sharedSecret(), "k tc " + t.get("tcId"));
            assertArrayEquals(hx(t, "k"), kem.decaps(hx(t, "dk"), e.ciphertext()), "round trip tc " + t.get("tcId"));
        }
    }

    @Test
    void decapsIncludingImplicitRejection() {
        boolean sawRejection = false;
        for (JsonNode t : kat.get("decapsulation")) {
            assertArrayEquals(hx(t, "k"), kem.decaps(hx(t, "dk"), hx(t, "c")), "tc " + t.get("tcId") + " " + t.get("reason"));
            sawRejection |= t.get("reason").asText().contains("modified");
        }
        assertTrue(sawRejection, "an implicit-rejection case is covered");
    }

    @Test
    void encapsulationKeyCheck() {
        boolean sawFail = false;
        for (JsonNode t : kat.get("encapsulationKeyCheck")) {
            boolean expected = t.get("testPassed").asBoolean();
            assertEquals(expected, kem.checkEncapsulationKey(hx(t, "ek")), "tc " + t.get("tcId") + " " + t.get("reason"));
            if (!expected) {
                sawFail = true;
                byte[] bad = hx(t, "ek");
                assertThrows(IllegalArgumentException.class, () -> kem.encaps(bad, new byte[32]), "encaps refuses a bad ek");
            }
        }
        assertTrue(sawFail);
        assertFalse(kem.checkEncapsulationKey(new byte[1183]));
    }

    @Test
    void randomPathsRoundTripAndNoProviderRegistered() {
        SecureRandom r = new SecureRandom();
        MlKem768.KeyPair kp = kem.keyGen(r);
        MlKem768.Encapsulation e = kem.encaps(kp.ek(), r);
        assertArrayEquals(e.sharedSecret(), kem.decaps(kp.dk(), e.ciphertext()));
        for (Provider p : Security.getProviders()) {
            assertFalse(p.getClass().getName().startsWith("io.takamaka.shaded"), "relocated provider registered: " + p);
        }
    }
}
