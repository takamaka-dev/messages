package io.takamaka.messages.call;

import com.fasterxml.jackson.databind.JsonNode;
import io.takamaka.messages.call.PushSeal.PushSealException;
import io.takamaka.messages.call.PushSeal.Reason;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The committed {@code call/push_seal_vectors_v1.json} (DR-063 §8) is the Java reference output, and every case
 * replays through {@link PushSeal}: seal cases re-seal to the same {@code sp} and open to {@code m}; relabel and refusal
 * cases fail with their code; push-key cases give their verdict. rsclient-flutter (W2) reads the SAME file.
 *
 * <p>To regenerate deliberately: {@code mvn -o test -Dtest=PushSealVectorFileTest -Dpush.seal.vectors.write=true}.
 */
class PushSealVectorFileTest {

    static final String RESOURCE = "/call/push_seal_vectors_v1.json";
    static final Path SOURCE = Paths.get("src/test/resources/call/push_seal_vectors_v1.json");
    static String text;
    static JsonNode root;

    @BeforeAll
    static void load() throws Exception {
        if (Boolean.getBoolean("push.seal.vectors.write")) {
            Files.writeString(SOURCE, PushSealVectorGenerator.render());
            text = Files.readString(SOURCE);
        } else {
            try (InputStream in = PushSealVectorFileTest.class.getResourceAsStream(RESOURCE)) {
                assertNotNull(in, "missing " + RESOURCE + " — run with -Dpush.seal.vectors.write=true");
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        root = PushSealVectorGenerator.M.readTree(text);
    }

    static byte[] unhex(JsonNode n) {
        return CallBytes.unhex(n.asText());
    }

    static byte[] d(JsonNode c) {
        return unhex(root.get("keys").get(c.get("key").asText()).get("d_hex"));
    }

    static String verdict(JsonNode c, String clearType) {
        try {
            PushSeal.open(d(c), clearType, c.get("sp").asText());
            return "OK";
        } catch (PushSealException ex) {
            return ex.getReason().name();
        }
    }

    @Test
    void committedFileEqualsTheReferenceOutput() throws Exception {
        assertEquals(PushSealVectorGenerator.render(), text,
                "vectors drifted from the Java reference: regenerate deliberately (-Dpush.seal.vectors.write=true)");
        assertTrue(StandardCharsets.US_ASCII.newEncoder().canEncode(text), "the vector file is ASCII");
    }

    @Test
    void theConstantsArePinned() {
        assertEquals("tkm-push/v1", root.get("suite").asText());
        assertEquals(1, root.get("version_byte").asInt());
        assertEquals(2048, root.get("pad").asInt());
        assertEquals(2046, root.get("max_j").asInt());
        assertEquals(41, root.get("header_len").asInt());
        assertEquals(16, root.get("tag_len").asInt());
        assertEquals(44, root.get("okm_len").asInt());
        assertEquals(2105, root.get("blob_len").asInt());
        assertEquals(2808, root.get("sp_len").asInt());
        assertEquals("746b6d2d707573682f76312f6b6964", root.get("labels").get("kid_hex").asText());
        assertEquals(15, unhex(root.get("labels").get("kid_hex")).length);
        assertEquals("edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f", root.get("p_le_hex").asText());
    }

    @Test
    void keysAndKids() {
        Iterator<Map.Entry<String, JsonNode>> it = root.get("keys").fields();
        while (it.hasNext()) {
            JsonNode k = it.next().getValue();
            byte[] pk = CallCrypto.x25519Public(unhex(k.get("d_hex")));
            assertEquals(k.get("pk_hex").asText(), CallBytes.hex(pk));
            assertEquals(k.get("pk_b64url").asText(), CallSignatures.urlBase64(pk));
            assertEquals(k.get("kid_hex").asText(), PushSeal.kidHex(pk));
            assertEquals(16, k.get("kid_hex").asText().length());
        }
    }

    @Test
    void sealCasesReplay() throws Exception {
        Set<String> types = new HashSet<>();
        for (JsonNode c : root.get("seal_cases")) {
            String name = c.get("name").asText();
            String type = c.get("type").asText();
            types.add(type);
            Map<String, String> m = new LinkedHashMap<>();
            c.get("m").fields().forEachRemaining(en -> m.put(en.getKey(), en.getValue().asText()));
            byte[] pkD = unhex(root.get("keys").get(c.get("key").asText()).get("pk_hex"));
            byte[] e = unhex(c.get("e_hex"));
            assertEquals(c.get("sp").asText(), PushSeal.seal(pkD, type, m, e), name);
            assertEquals(2808, c.get("sp").asText().length(), name);
            assertEquals(c.get("epk_hex").asText(), CallBytes.hex(CallCrypto.x25519Public(e)), name);
            assertEquals(c.get("j_hex").asText(), CallBytes.hex(PushSeal.frame(m)), name);
            assertEquals(c.get("j").asText(), new String(PushSeal.frame(m), StandardCharsets.UTF_8), name);
            byte[] okm = PushSeal.keySchedule(CallCrypto.x25519(e, pkD), CallCrypto.x25519Public(e), pkD);
            assertEquals(c.get("okm_hex").asText(), CallBytes.hex(okm), name);
            assertEquals(c.get("aad_hex").asText(), CallBytes.hex(PushSeal.aad(type, unhex(c.get("hdr_hex")))), name);
            Map<String, String> opened = PushSeal.open(d(c), type, c.get("sp").asText());
            assertEquals(m, opened, name);
            PushSeal.Header h = PushSeal.parseHeader(c.get("sp").asText());
            assertEquals(1, h.version());
            assertEquals(root.get("keys").get(c.get("key").asText()).get("kid_hex").asText(), h.kidHex());
            assertEquals("2", c.get("data").get("v").asText());
            assertEquals(3, c.get("data").size(), name + ": the clear data is {type, v, sp} only");
        }
        assertEquals(Set.of("CALL_RING", "CALL_ANSWERED", "CALL_CANCELLED", "CALL_MEET"), types);
    }

    @Test
    void relabelCasesCoverEveryOrderedPair() {
        Set<String> pairs = new HashSet<>();
        for (JsonNode c : root.get("relabel_cases")) {
            assertEquals("AEAD", c.get("expect").asText());
            assertEquals("AEAD", verdict(c, c.get("clear_type").asText()), c.get("name").asText());
            // the same sp opens under its own type
            assertEquals("OK", verdict(c, c.get("sealed_type").asText()), c.get("name").asText());
            pairs.add(c.get("sealed_type").asText() + ">" + c.get("clear_type").asText());
        }
        assertEquals(12, pairs.size());
    }

    @Test
    void refusalCasesReplayAndCoverEveryOpenReason() {
        Set<Reason> seen = EnumSet.noneOf(Reason.class);
        Set<String> names = new HashSet<>();
        for (JsonNode c : root.get("refusal_cases")) {
            assertTrue(names.add(c.get("name").asText()), "duplicate name");
            String expect = c.get("expect").asText();
            assertEquals(expect, verdict(c, c.get("clear_type").asText()), c.get("name").asText());
            seen.add(Reason.valueOf(expect));
        }
        assertEquals(EnumSet.copyOf(PushSealVectorGenerator.OPEN_ORDER), seen);
        // the order in the file is the implemented order
        int i = 0;
        for (JsonNode r : root.get("open_refusal_order")) {
            assertEquals(PushSealVectorGenerator.OPEN_ORDER.get(i++).name(), r.asText());
        }
    }

    @Test
    void pushKeyCasesReplayAndCoverEveryKeyReason() throws Exception {
        Set<String> seen = new HashSet<>();
        for (JsonNode c : root.get("push_key_cases")) {
            String expect = c.get("expect").asText();
            String b64 = c.get("push_key").asText();
            if ("OK".equals(expect)) {
                byte[] k = PushSeal.validatePushKey(b64);
                assertArrayEquals(io.takamaka.messages.utils.ChatSignatureVerifier.decodeStrictOrNull(b64), k);
                assertEquals(c.get("push_key_id").asText(), PushSeal.kidHex(k));
            } else {
                PushSealException ex = assertThrows(PushSealException.class, () -> PushSeal.validatePushKey(b64),
                        c.get("name").asText());
                assertEquals(expect, ex.getReason().name(), c.get("name").asText());
            }
            seen.add(expect);
        }
        Set<String> want = new HashSet<>();
        want.add("OK");
        PushSealVectorGenerator.KEY_ORDER.forEach(r -> want.add(r.name()));
        assertEquals(want, seen);
    }
}
