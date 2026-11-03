package io.takamaka.messages.call.rschat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.takamaka.messages.call.CallBytes;
import io.takamaka.messages.call.CallConstants;
import io.takamaka.messages.call.CallError;
import io.takamaka.messages.call.CallJson;
import io.takamaka.messages.call.CallProtocolException;
import io.takamaka.messages.call.CallSignatures;
import io.takamaka.messages.call.CallTestKeys;
import io.takamaka.messages.call.beans.CallRingBean;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-6 (ruling O-1 b, 2026-11-03): the caller's signed ring inside the {@code CALL_RING} push — its canonical form,
 * the reader's check ({@link CallRschat#checkPushRing}), the freshness rule, and the cross-platform vector file
 * {@code call/call_ring_push_vector_v1.json} the Dart port (tkm_calls {@code call_ring_push_vector_test.dart})
 * verifies case by case: the committed copy must equal what this reference generates now (drift guard); regenerate
 * with {@code -Dcall.vectors.write=true}.
 */
class CallRingPushTest {

    static final String RESOURCE = "/call/call_ring_push_vector_v1.json";
    static final String AUD = CallRschat.audience("test");
    static final String CALL = "ab".repeat(32);
    static final String NONCE = "00".repeat(31) + "aa";
    static final long TS = 1_700_000_000_000L;
    static final String SVC = "london-test-1";
    static final AsymmetricCipherKeyPair OWNER = CallTestKeys.identity("g6-owner");

    static String id(AsymmetricCipherKeyPair kp) {
        return CallSignatures.identityOf(kp);
    }

    static List<String> callees(int n) {
        List<String> to = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            to.add(id(CallTestKeys.identity("g6-callee-" + i)));
        }
        return to;
    }

    static CallRingBean signed(List<String> to, String aud, long ts, String k) {
        CallRingBean r = CallRschat.ring(CALL, SVC, to, CallConstants.MODE_STANDARD, NONCE, aud, ts);
        r.setK(k);
        CallSignatures.sign(r, OWNER);
        return r;
    }

    static CallRingNoticeBean noticeOf(CallRingBean r) {
        return CallRschat.notice(r);
    }

    /** The push data of a ring: today's four fields + framing, plus the signed ring. */
    static Map<String, String> pushData(CallRingBean r, String ringField) {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("type", CallRschat.NOTIFICATION_TYPE);
        d.put("call_id", r.getCall());
        d.put("svc", r.getSvc());
        d.put("f", r.getF());
        d.put("mode", r.getMode());
        d.put("v", "1");
        if (ringField != null) {
            d.put(CallRschat.PUSH_RING_FIELD, ringField);
        }
        return d;
    }

    @Test
    void pushRingIsCanonicalAsciiAndCarriesTheSignature() throws Exception {
        CallRingBean r = signed(callees(1), AUD, TS, null);
        String p = CallRschat.pushRing(r);
        assertTrue(CallBytes.isAscii(p));
        assertEquals(p, CallJson.canonical(CallJson.parse(p, CallRingBean.class)), "idempotent");
        assertTrue(p.contains("\"sg\":\"" + r.getSg() + "\""));
        assertTrue(p.startsWith("{\"aud\":"), "JCS: keys sorted");
    }

    @Test
    void aPushedRingVerifiesAndNamesTheCaller() throws Exception {
        CallRingBean r = signed(callees(3), AUD, TS, null);
        String me = callees(3).get(1);
        CallRingBean v = CallRschat.checkPushRing(CallRschat.pushRing(r), AUD, noticeOf(r), me, CallRschat.RING_TO_MAX);
        assertEquals(id(OWNER), v.getF());
        // the reader without a known push identity still verifies the signature and the binding
        assertEquals(id(OWNER), CallRschat.checkPushRing(CallRschat.pushRing(r), AUD, noticeOf(r), null, CallRschat.RING_TO_MAX).getF());
    }

    @Test
    void forgedOrMismatchedPushRingsAreRefused() throws Exception {
        CallRingBean r = signed(callees(2), AUD, TS, null);
        String me = callees(2).get(0);
        String good = CallRschat.pushRing(r);
        // tampered signature
        String tampered = good.replace("\"sg\":\"" + r.getSg().substring(0, 4), "\"sg\":\"" + (r.getSg().startsWith("0000") ? "1111" : "0000"));
        refused(tampered, AUD, noticeOf(r), me);
        // a callee swapped after signing
        refused(good.replace(callees(2).get(1), callees(3).get(2)), AUD, noticeOf(r), me);
        // the wrong network's audience
        refused(good, CallRschat.audience("prod"), noticeOf(r), me);
        // the push's own fields do not match the signed ring (a signed ring of one call dressing another push)
        refused(good, AUD, new CallRingNoticeBean("cd".repeat(32), SVC, r.getF(), r.getMode()), me);
        refused(good, AUD, new CallRingNoticeBean(CALL, "other-svc", r.getF(), r.getMode()), me);
        refused(good, AUD, new CallRingNoticeBean(CALL, SVC, callees(2).get(1), r.getMode()), me);
        refused(good, AUD, new CallRingNoticeBean(CALL, SVC, r.getF(), CallConstants.MODE_CONFERENCE), me);
        // not addressed to this device's identity
        refused(good, AUD, noticeOf(r), callees(3).get(2));
        // an answered notice is not a ring
        CallRingBean a = CallRschat.answered(CALL, SVC, CallConstants.MODE_STANDARD, id(OWNER), NONCE, AUD, TS);
        CallSignatures.sign(a, OWNER);
        refused(CallRschat.pushRing(a), AUD, noticeOf(a), id(OWNER));
        // junk
        refused("not json", AUD, noticeOf(r), me);
        refused(null, AUD, noticeOf(r), me);
        refused(good, null, noticeOf(r), me);
    }

    static void refused(String ring, String aud, CallRingNoticeBean n, String me) {
        CallProtocolException ex = assertThrows(CallProtocolException.class,
                () -> CallRschat.checkPushRing(ring, aud, n, me, CallRschat.RING_TO_MAX));
        assertEquals(CallError.BAD_SIGNATURE, ex.getError());
    }

    @Test
    void freshnessWindow() {
        CallRingBean r = signed(callees(1), AUD, TS, null);
        assertTrue(CallRschat.isFreshPushRing(r, TS));
        assertTrue(CallRschat.isFreshPushRing(r, TS + CallRschat.PUSH_RING_MAX_AGE_MS));
        assertFalse(CallRschat.isFreshPushRing(r, TS + CallRschat.PUSH_RING_MAX_AGE_MS + 1));
        assertTrue(CallRschat.isFreshPushRing(r, TS - CallRschat.PUSH_RING_MAX_SKEW_MS));
        assertFalse(CallRschat.isFreshPushRing(r, TS - CallRschat.PUSH_RING_MAX_SKEW_MS - 1));
        r.setTs(null);
        assertFalse(CallRschat.isFreshPushRing(r, TS));
    }

    // ---- the cross-platform vector file ------------------------------------------------------------------------------

    /** The vector file as this reference generates it now. */
    static String generate() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("_origin", "Messages CallRingPushTest (G-6, 2026-11-03): the caller's signed ring inside the CALL_RING push — "
                + "each case is the FCM data map a device receives; `expect.verified` is the verdict of CallRschat.checkPushRing "
                + "under `aud` for identity `me`, `expect.fresh` that of isFreshPushRing at `now_ms`. Keys: seeds are "
                + "SHA3-256(ASCII(\"tkm-call/v1/vectors/identity/\" + name)) (CallTestKeys.identity).");
        root.put("aud", AUD);
        root.put("now_ms", TS + 30_000L);
        root.put("max_age_ms", CallRschat.PUSH_RING_MAX_AGE_MS);
        root.put("max_skew_ms", CallRschat.PUSH_RING_MAX_SKEW_MS);
        root.put("fcm_data_max_bytes", CallRschat.FCM_DATA_MAX_BYTES);
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("g6-owner", key("g6-owner"));
        for (int i = 1; i <= 25; i++) {
            keys.put("g6-callee-" + i, key("g6-callee-" + i));
        }
        root.put("keys", keys);
        List<Map<String, Object>> cases = new ArrayList<>();
        List<String> one = callees(1);
        List<String> all = callees(25);
        CallRingBean r1 = signed(one, AUD, TS, null);
        CallRingBean r25 = signed(all, AUD, TS, null);
        cases.add(vectorCase("one_callee", pushData(r1, CallRschat.pushRing(r1)), one.get(0), true, true));
        cases.add(vectorCase("twenty_five_callees", pushData(r25, CallRschat.pushRing(r25)), all.get(24), true, true));
        cases.add(vectorCase("no_push_identity_known", pushData(r1, CallRschat.pushRing(r1)), null, true, true));
        String good = CallRschat.pushRing(r1);
        cases.add(vectorCase("tampered_signature",
                pushData(r1, good.replace("\"sg\":\"" + r1.getSg().substring(0, 4), "\"sg\":\"" + (r1.getSg().startsWith("0000") ? "1111" : "0000"))),
                one.get(0), false, null));
        cases.add(vectorCase("callee_swapped_after_signing", pushData(r1, good.replace(one.get(0), all.get(1))), all.get(1), false, null));
        cases.add(vectorCase("not_for_me", pushData(r1, good), all.get(1), false, null));
        Map<String, String> other = pushData(r1, good);
        other.put("call_id", "cd".repeat(32));
        cases.add(vectorCase("call_id_mismatch", other, one.get(0), false, null));
        Map<String, String> otherF = pushData(r1, good);
        otherF.put("f", all.get(2));
        cases.add(vectorCase("caller_mismatch", otherF, one.get(0), false, null));
        CallRingBean prod = signed(one, CallRschat.audience("prod"), TS, null);
        cases.add(vectorCase("wrong_audience", pushData(prod, CallRschat.pushRing(prod)), one.get(0), false, null));
        CallRingBean a = CallRschat.answered(CALL, SVC, CallConstants.MODE_STANDARD, id(OWNER), NONCE, AUD, TS);
        CallSignatures.sign(a, OWNER);
        cases.add(vectorCase("answered_notice", pushData(a, CallRschat.pushRing(a)), id(OWNER), false, null));
        CallRingBean stale = signed(one, AUD, TS + 30_000L - CallRschat.PUSH_RING_MAX_AGE_MS - 1, null);
        cases.add(vectorCase("stale", pushData(stale, CallRschat.pushRing(stale)), one.get(0), true, false));
        CallRingBean future = signed(one, AUD, TS + 30_000L + CallRschat.PUSH_RING_MAX_SKEW_MS + 1, null);
        cases.add(vectorCase("from_the_future", pushData(future, CallRschat.pushRing(future)), one.get(0), true, false));
        cases.add(vectorCase("no_ring_field", pushData(r1, null), one.get(0), false, null));
        root.put("cases", cases);
        return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(root) + "\n";
    }

    static Map<String, Object> key(String name) {
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("seed_hex", CallBytes.hex(CallTestKeys.seed("identity/" + name)));
        k.put("identity", id(CallTestKeys.identity(name)));
        return k;
    }

    static Map<String, Object> vectorCase(String name, Map<String, String> data, String me, boolean verified, Boolean fresh) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", name);
        c.put("me", me);
        c.put("data", data);
        int bytes = 0;
        for (Map.Entry<String, String> e : data.entrySet()) {
            bytes += e.getKey().getBytes(StandardCharsets.UTF_8).length + e.getValue().getBytes(StandardCharsets.UTF_8).length;
        }
        c.put("data_bytes", bytes);
        Map<String, Object> expect = new LinkedHashMap<>();
        expect.put("verified", verified);
        if (verified) {
            expect.put("f", data.get("f"));
        }
        if (fresh != null) {
            expect.put("fresh", fresh);
        }
        c.put("expect", expect);
        return c;
    }

    @Test
    void vectorFileEqualsWhatTheReferenceGeneratesNow() throws Exception {
        String generated = generate();
        if (Boolean.getBoolean("call.vectors.write")) {
            Path out = Path.of("src/test/resources" + RESOURCE);
            Files.createDirectories(out.getParent());
            Files.writeString(out, generated, StandardCharsets.UTF_8);
        }
        String committed;
        try (InputStream in = CallRingPushTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "missing " + RESOURCE + " — run with -Dcall.vectors.write=true");
            committed = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(generated, committed, "the committed vector file drifted from the reference");
    }

    @Test
    void everyVectorCaseReDerivesFromTheFileAlone() throws Exception {
        JsonNode root;
        try (InputStream in = CallRingPushTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in);
            root = new ObjectMapper().readTree(in);
        }
        String aud = root.get("aud").asText();
        long now = root.get("now_ms").asLong();
        for (JsonNode c : root.get("cases")) {
            String name = c.get("name").asText();
            JsonNode d = c.get("data");
            CallRingNoticeBean n = new CallRingNoticeBean(d.get("call_id").asText(), d.get("svc").asText(), d.get("f").asText(), d.get("mode").asText());
            String me = c.get("me").isNull() ? null : c.get("me").asText();
            String ring = d.has(CallRschat.PUSH_RING_FIELD) ? d.get(CallRschat.PUSH_RING_FIELD).asText() : null;
            boolean expectVerified = c.get("expect").get("verified").asBoolean();
            try {
                CallRingBean r = CallRschat.checkPushRing(ring, aud, n, me, CallRschat.RING_TO_MAX);
                assertTrue(expectVerified, name + ": verified but expected a refusal");
                assertEquals(c.get("expect").get("f").asText(), r.getF(), name);
                assertEquals(c.get("expect").get("fresh").asBoolean(), CallRschat.isFreshPushRing(r, now), name + ": fresh");
            } catch (CallProtocolException ex) {
                assertFalse(expectVerified, name + ": refused (" + ex.getReason() + ") but expected verified");
            }
            assertTrue(c.get("data_bytes").asInt() <= CallRschat.FCM_DATA_MAX_BYTES, name + ": over the FCM cap");
        }
    }
}
