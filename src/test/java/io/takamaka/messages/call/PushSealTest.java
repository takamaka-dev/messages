package io.takamaka.messages.call;

import io.takamaka.messages.call.PushSeal.PushSealException;
import io.takamaka.messages.call.PushSeal.Reason;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PushSeal} (DR-063, {@code tkm-push/v1}): round trips with random keys for the four call push types, the
 * seal's own refusals, the opener's refusals (one per reason, plus the order on two-fault inputs), the header read,
 * and the push-key check. The cross-platform cases are in {@link PushSealVectorFileTest}.
 */
class PushSealTest {

    static final SecureRandom RND = new SecureRandom();
    static final String CALL = "ab".repeat(32);

    static byte[] randomScalar() {
        byte[] d = new byte[32];
        RND.nextBytes(d);
        return d;
    }

    static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    static Reason sealRefusal(byte[] pkD, String type, Map<String, String> m) {
        return assertThrows(PushSealException.class, () -> PushSeal.seal(pkD, type, m, RND)).getReason();
    }

    static Reason openRefusal(byte[] d, String type, String sp) {
        return assertThrows(PushSealException.class, () -> PushSeal.open(d, type, sp)).getReason();
    }

    @Test
    void constants() {
        assertEquals(41, PushSeal.HEADER_LEN);
        assertEquals(2105, PushSeal.BLOB_LEN);
        assertEquals(2808, PushSeal.SP_LEN);
        assertEquals(2046, PushSeal.MAX_J);
        assertEquals(44, PushSeal.OKM_LEN);
    }

    @Test
    void roundTripsForTheFourTypes() throws Exception {
        for (Map.Entry<String, Map<String, String>> c : Map.of(
                "CALL_RING", PushSealVectorGenerator.ringMap(),
                "CALL_ANSWERED", PushSealVectorGenerator.answeredMap(),
                "CALL_CANCELLED", PushSealVectorGenerator.cancelledMap(),
                "CALL_MEET", PushSealVectorGenerator.meetMap()).entrySet()) {
            byte[] d = randomScalar();
            byte[] pk = CallCrypto.x25519Public(d);
            String sp = PushSeal.seal(pk, c.getKey(), c.getValue(), RND);
            assertEquals(PushSeal.SP_LEN, sp.length());
            assertTrue(sp.endsWith(".") && !sp.endsWith(".."), "exactly one '.' pad");
            assertEquals(c.getValue(), PushSeal.open(d, c.getKey(), sp));
            assertEquals(PushSeal.kidHex(pk), PushSeal.parseHeader(sp).kidHex());
            // a fresh ephemeral per seal
            assertNotEquals(sp, PushSeal.seal(pk, c.getKey(), c.getValue(), RND));
        }
    }

    @Test
    void theOpenedMapKeepsJOrderAndIsUnmodifiable() throws Exception {
        byte[] d = randomScalar();
        String sp = PushSeal.seal(CallCrypto.x25519Public(d), "CALL_RING", PushSealVectorGenerator.ringMap(), RND);
        Map<String, String> m = PushSeal.open(d, "CALL_RING", sp);
        assertEquals(List.of("call_id", "f", "mode", "ring", "svc", "type", "v"), List.copyOf(m.keySet()));
        assertThrows(UnsupportedOperationException.class, () -> m.put("x", "y"));
    }

    @Test
    void unknownKeysAreTheHandlersBusiness() throws Exception {
        // DR-063 §2: the suite checks frames, not per-type key sets
        byte[] d = randomScalar();
        Map<String, String> m = map("type", "CALL_ANSWERED", "call_id", CALL, "v", "1", "future_field", "x");
        assertEquals(m, PushSeal.open(d, "CALL_ANSWERED", PushSeal.seal(CallCrypto.x25519Public(d), "CALL_ANSWERED", m, RND)));
    }

    @Test
    void exactFitAndOneByteOver() throws Exception {
        byte[] d = randomScalar();
        byte[] pk = CallCrypto.x25519Public(d);
        Map<String, String> m = map("type", "CALL_MEET", "v", "1", "x", "");
        int base = PushSeal.frame(m).length;
        m.put("x", "a".repeat(PushSeal.MAX_J - base));
        assertEquals(PushSeal.MAX_J, PushSeal.frame(m).length);
        assertEquals(m, PushSeal.open(d, "CALL_MEET", PushSeal.seal(pk, "CALL_MEET", m, RND)));
        m.put("x", "a".repeat(PushSeal.MAX_J - base + 1));
        assertEquals(Reason.TOO_LONG, sealRefusal(pk, "CALL_MEET", m));
    }

    @Test
    void theSealRefusesWhatTheOpenerWouldRefuse() {
        byte[] pk = CallCrypto.x25519Public(randomScalar());
        assertEquals(Reason.TYPE_MISMATCH, sealRefusal(pk, "CALL_RING", map("type", "CALL_ANSWERED", "v", "1")));
        assertEquals(Reason.TYPE_MISMATCH, sealRefusal(pk, "CALL_RING", map("v", "1")));
        assertEquals(Reason.BAD_INNER_V, sealRefusal(pk, "CALL_RING", map("type", "CALL_RING", "v", "2")));
        assertEquals(Reason.BAD_INNER_V, sealRefusal(pk, "CALL_RING", map("type", "CALL_RING")));
        assertEquals(Reason.NON_STRING, sealRefusal(pk, "CALL_RING", map("type", "CALL_RING", "v", "1", "x", null)));
        assertEquals(Reason.NON_STRING, sealRefusal(pk, "CALL_RING", null));
        // a low-order, high-bit, non-canonical or short device key
        assertEquals(Reason.LOW_ORDER, sealRefusal(new byte[32], "CALL_RING", map("type", "CALL_RING", "v", "1")));
        byte[] hb = pk.clone();
        hb[31] |= (byte) 0x80;
        assertEquals(Reason.HIGH_BIT, sealRefusal(hb, "CALL_RING", map("type", "CALL_RING", "v", "1")));
        assertEquals(Reason.BAD_KEY_LENGTH, sealRefusal(Arrays.copyOf(pk, 31), "CALL_RING", map("type", "CALL_RING", "v", "1")));
    }

    @Test
    void openerRefusalsInOrder() throws Exception {
        byte[] d = randomScalar();
        byte[] pk = CallCrypto.x25519Public(d);
        Map<String, String> m = map("type", "CALL_ANSWERED", "call_id", CALL, "v", "1");
        String sp = PushSeal.seal(pk, "CALL_ANSWERED", m, RND);
        byte[] blob = PushSealVectorGenerator.blob(sp);
        assertEquals(Reason.BAD_ENCODING, openRefusal(d, "CALL_ANSWERED", sp.substring(0, sp.length() - 1) + "="));
        assertEquals(Reason.BAD_ENCODING, openRefusal(d, "CALL_ANSWERED", sp + " "));
        assertEquals(Reason.BAD_ENCODING, openRefusal(d, "CALL_ANSWERED", null));
        assertEquals(Reason.BAD_LENGTH, openRefusal(d, "CALL_ANSWERED", CallSignatures.urlBase64(Arrays.copyOf(blob, 2104))));
        byte[] v2 = blob.clone();
        v2[0] = 2;
        assertEquals(Reason.BAD_VERSION, openRefusal(d, "CALL_ANSWERED", CallSignatures.urlBase64(v2)));
        assertEquals(Reason.KID_MISMATCH, openRefusal(randomScalar(), "CALL_ANSWERED", sp));
        byte[] lo = blob.clone();
        Arrays.fill(lo, 9, 41, (byte) 0);
        assertEquals(Reason.LOW_ORDER, openRefusal(d, "CALL_ANSWERED", CallSignatures.urlBase64(lo)));
        assertEquals(Reason.AEAD, openRefusal(d, "CALL_RING", sp));
        byte[] ct = blob.clone();
        ct[100] ^= 1;
        assertEquals(Reason.AEAD, openRefusal(d, "CALL_ANSWERED", CallSignatures.urlBase64(ct)));
    }

    /** The frame rules, exercised through a raw seal of a caller-built J. */
    static Reason frameRefusal(String j) throws Exception {
        return frameRefusal(j.getBytes(StandardCharsets.UTF_8));
    }

    static Reason frameRefusal(byte[] j) throws Exception {
        byte[] d = randomScalar();
        String sp = PushSeal.sealPlaintext(CallCrypto.x25519Public(d), PushSeal.VERSION, "CALL_ANSWERED",
                PushSeal.plaintext(j), randomScalar());
        return openRefusal(d, "CALL_ANSWERED", sp);
    }

    @Test
    void frameRefusals() throws Exception {
        String ok = "{\"call_id\":\"" + CALL + "\",\"type\":\"CALL_ANSWERED\",\"v\":\"1\"}";
        assertEquals(Reason.BAD_JSON, frameRefusal("nope"));
        assertEquals(Reason.BAD_JSON, frameRefusal(""));
        assertEquals(Reason.BAD_JSON, frameRefusal("\ufeff" + ok));
        assertEquals(Reason.BAD_JSON, frameRefusal(ok + "{}"));
        assertEquals(Reason.BAD_JSON, frameRefusal("{\"call_id\":01,\"type\":\"CALL_ANSWERED\",\"v\":\"1\"}"));
        assertEquals(Reason.BAD_JSON, frameRefusal(new byte[]{'{', '"', (byte) 0xff, '"', ':', '"', '"', '}'}));
        assertEquals(Reason.DUPLICATE_KEY, frameRefusal("{\"v\":\"1\",\"v\":\"1\"}"));
        assertEquals(Reason.NON_STRING, frameRefusal("{\"call_id\":[1,{\"a\":2}],\"type\":\"CALL_ANSWERED\",\"v\":\"1\"}"));
        assertEquals(Reason.NOT_JCS, frameRefusal("{\"call_id\":\"\\u0041\",\"type\":\"CALL_ANSWERED\",\"v\":\"1\"}"));
        assertEquals(Reason.NOT_JCS, frameRefusal("{\"v\":\"1\",\"type\":\"CALL_ANSWERED\"}"));
        assertEquals(Reason.TYPE_MISMATCH, frameRefusal("{\"type\":\"CALL_RING\",\"v\":\"1\"}"));
        assertEquals(Reason.BAD_INNER_V, frameRefusal("{\"type\":\"CALL_ANSWERED\",\"v\":\"01\"}"));
        // a JCS J with control characters and non-ASCII opens
        byte[] d = randomScalar();
        Map<String, String> m = map("type", "CALL_ANSWERED", "v", "1", "x", "a\"b\\c\n\u0001\u00e9\ud83d\ude00");
        assertEquals(m, PushSeal.open(d, "CALL_ANSWERED", PushSeal.seal(CallCrypto.x25519Public(d), "CALL_ANSWERED", m, RND)));
    }

    @Test
    void paddingAndLengthPrefix() throws Exception {
        byte[] d = randomScalar();
        byte[] pk = CallCrypto.x25519Public(d);
        byte[] j = PushSeal.frame(map("type", "CALL_ANSWERED", "v", "1"));
        byte[] pt = PushSeal.plaintext(j);
        pt[PushSeal.PAD - 1] = 1;
        assertEquals(Reason.BAD_PADDING, openRefusal(d, "CALL_ANSWERED",
                PushSeal.sealPlaintext(pk, 1, "CALL_ANSWERED", pt, randomScalar())));
        byte[] pt2 = PushSeal.plaintext(j);
        pt2[0] = 0x07;
        pt2[1] = (byte) 0xff;   // 2047
        assertEquals(Reason.TOO_LONG, openRefusal(d, "CALL_ANSWERED",
                PushSeal.sealPlaintext(pk, 1, "CALL_ANSWERED", pt2, randomScalar())));
        byte[] pt3 = PushSeal.plaintext(j);
        pt3[1] = (byte) (j.length - 1);   // J cut by one: the brace becomes padding
        assertEquals(Reason.BAD_PADDING, openRefusal(d, "CALL_ANSWERED",
                PushSeal.sealPlaintext(pk, 1, "CALL_ANSWERED", pt3, randomScalar())));
    }

    @Test
    void headerIsReadWithoutTheKey() throws Exception {
        byte[] d = randomScalar();
        byte[] pk = CallCrypto.x25519Public(d);
        byte[] e = randomScalar();
        String sp = PushSeal.seal(pk, "CALL_ANSWERED", map("type", "CALL_ANSWERED", "v", "1"), e);
        PushSeal.Header h = PushSeal.parseHeader(sp);
        assertEquals(1, h.version());
        assertArrayEquals(PushSeal.kid(pk), h.kid());
        assertArrayEquals(CallCrypto.x25519Public(e), h.epk());
        // the version is reported, not checked, by the header read
        byte[] b = PushSealVectorGenerator.blob(sp);
        b[0] = 7;
        assertEquals(7, PushSeal.parseHeader(CallSignatures.urlBase64(b)).version());
        assertEquals(Reason.BAD_LENGTH, assertThrows(PushSealException.class,
                () -> PushSeal.parseHeader(CallSignatures.urlBase64(new byte[41]))).getReason());
        assertEquals(Reason.BAD_ENCODING, assertThrows(PushSealException.class,
                () -> PushSeal.parseHeader("!")).getReason());
    }

    @Test
    void kidIsSha3OfLabelAndKey() {
        byte[] pk = CallCrypto.x25519Public(randomScalar());
        byte[] full = CallCrypto.h("tkm-push/v1/kid".getBytes(StandardCharsets.US_ASCII), pk);
        assertArrayEquals(Arrays.copyOf(full, 8), PushSeal.kid(pk));
        assertEquals(16, PushSeal.kidHex(pk).length());
        assertTrue(PushSeal.kidHex(pk).matches("[0-9a-f]{16}"));
    }

    @Test
    void validatePushKey() throws Exception {
        byte[] pk = CallCrypto.x25519Public(randomScalar());
        assertArrayEquals(pk, PushSeal.validatePushKey(CallSignatures.urlBase64(pk)));
        assertEquals(Reason.BAD_ENCODING, keyRefusal(null));
        assertEquals(Reason.BAD_ENCODING, keyRefusal(java.util.Base64.getEncoder().encodeToString(pk)));
        assertEquals(Reason.BAD_KEY_LENGTH, keyRefusal(CallSignatures.urlBase64(new byte[31])));
        assertEquals(Reason.LOW_ORDER, keyRefusal(CallSignatures.urlBase64(new byte[32])));
        assertEquals(Reason.LOW_ORDER, keyRefusal(CallSignatures.urlBase64(PushSealVectorGenerator.ORDER8)));
        // every listed low-order point gives an all-zero X25519 with the fixed scalar AND with a random one
        for (byte[] lo : List.of(new byte[32], PushSealVectorGenerator.le(java.math.BigInteger.ONE),
                PushSealVectorGenerator.ORDER8,
                PushSealVectorGenerator.le(PushSeal.P.subtract(java.math.BigInteger.ONE)))) {
            assertEquals(null, PushSeal.dhOrNull(PushSeal.LOW_ORDER_CHECK_SCALAR, lo));
            assertEquals(null, PushSeal.dhOrNull(randomScalar(), lo));
        }
        assertFalse(PushSeal.dhOrNull(PushSeal.LOW_ORDER_CHECK_SCALAR, pk) == null);
    }

    static Reason keyRefusal(String b64) {
        return assertThrows(PushSealException.class, () -> PushSeal.validatePushKey(b64)).getReason();
    }
}
