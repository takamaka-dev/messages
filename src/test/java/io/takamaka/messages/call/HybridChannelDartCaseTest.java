package io.takamaka.messages.call;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.takamaka.messages.call.channel.HybridChannel;
import io.takamaka.messages.call.channel.MlKem768;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-platform check of the §5.1 combiner against the case the Dart spike produced with a REAL ML-KEM-768.
 *
 * <p>Fixture {@code src/test/resources/call/combiner_case_dart.json} is a verbatim copy of
 * {@code spikes/c182-mlkem-dart/data/combiner_case_dart.json} (bin/combiner.dart, 2026-10-07 19:24). The ML-KEM
 * values are injected ({@link InjectedMlKem768}); everything else is computed here and compared intermediate by
 * intermediate, in derivation order, so the first divergence is the one reported.
 *
 * <p>Encodings ratified by the coordinator (spec reading, to be written into §5.1): raw 32-byte call_id and
 * ann_hash in the salt; raw 32-byte X25519 keys in info; info is A.x25519 ‖ B.x25519 ‖ ct for BOTH directions
 * (only the trailing direction string changes); raw 32-byte X25519 private keys, clamping inside X25519;
 * HKDF-Extract with the 32-byte SHA3-256 salt as given.
 */
class HybridChannelDartCaseTest {

    @Test
    void reproducesEveryIntermediateOfTheDartCase() throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream("/call/combiner_case_dart.json")) {
            root = new ObjectMapper().readTree(in);
        }
        JsonNode inp = root.get("inputs");
        JsonNode mid = root.get("intermediates");

        // the inputs are themselves derived by the documented rule
        JsonNode labels = root.get("inputs_labels");
        labels.fieldNames().forEachRemaining(k -> assertEquals(inp.get(k).asText(),
                CallBytes.hex(CallCrypto.h(CallBytes.ascii("c182/test-b/" + labels.get(k).asText()))), "input " + k));

        byte[] callId = hx(inp, "call_id");
        byte[] annA = hx(inp, "ann_hash_A");
        byte[] annB = hx(inp, "ann_hash_B");
        byte[] aPriv = hx(inp, "A_x25519_priv");
        byte[] bPriv = hx(inp, "B_x25519_priv");

        byte[] aPub = CallCrypto.x25519Public(aPriv);
        assertEquals(mid.get("A.x25519_pub").asText(), CallBytes.hex(aPub), "A.x25519_pub");
        byte[] bPub = CallCrypto.x25519Public(bPriv);
        assertEquals(mid.get("B.x25519_pub").asText(), CallBytes.hex(bPub), "B.x25519_pub");
        byte[] ssX = CallCrypto.x25519(aPriv, bPub);
        assertEquals(mid.get("ss_x").asText(), CallBytes.hex(ssX), "ss_x (A side)");
        assertEquals(mid.get("ss_x").asText(), CallBytes.hex(CallCrypto.x25519(bPriv, aPub)), "ss_x (B side)");

        MlKem768 kem = new InjectedMlKem768(hx(mid, "B.mlkem_ek"), hx(mid, "B.mlkem_dk"), hx(mid, "ss_k"), hx(mid, "ct"));
        MlKem768.Encapsulation enc = kem.encaps(hx(mid, "B.mlkem_ek"), hx(inp, "encaps_m"));
        assertEquals(MlKem768.EK_LEN, hx(mid, "B.mlkem_ek").length, "ek length");
        assertEquals(MlKem768.DK_LEN, hx(mid, "B.mlkem_dk").length, "dk length");
        assertEquals(MlKem768.CT_LEN, enc.ciphertext().length, "ct length");
        assertArrayEquals(enc.sharedSecret(), kem.decaps(hx(mid, "B.mlkem_dk"), enc.ciphertext()), "decaps");

        assertEquals(mid.get("salt_input").asText(), CallBytes.hex(CallBytes.concat(callId, annA, annB)), "salt_input");
        HybridChannel.Combined c = HybridChannel.combine(callId, annA, annB, aPub, bPub, ssX, enc.sharedSecret(), enc.ciphertext());
        assertEquals(mid.get("salt").asText(), CallBytes.hex(c.salt()), "salt");
        assertEquals(mid.get("ikm").asText(), CallBytes.hex(c.ikm()), "ikm");
        assertEquals(mid.get("prk").asText(), CallBytes.hex(c.prk()), "prk");
        assertEquals(mid.get("info[A>B]").asText(), CallBytes.hex(c.infoAB()), "info[A>B]");
        assertEquals(mid.get("chain_0[A>B]").asText(), CallBytes.hex(c.chainAB()), "chain_0[A>B]");
        assertEquals(mid.get("info[B>A]").asText(), CallBytes.hex(c.infoBA()), "info[B>A]");
        assertEquals(mid.get("chain_0[B>A]").asText(), CallBytes.hex(c.chainBA()), "chain_0[B>A]");
    }

    private static byte[] hx(JsonNode n, String k) {
        assertNotNull(n.get(k), "missing " + k);
        return CallBytes.unhex(n.get(k).asText());
    }
}
