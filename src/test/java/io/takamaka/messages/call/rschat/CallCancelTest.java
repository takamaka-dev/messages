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
import io.takamaka.messages.chat.notification.UserNotificationJsonBean;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2 (caller cancel, design CALL_RING_ANY_STATE_DESIGN_2026-11-05 §5, operator Q5, Iris §7.123 I2/RA-5/RA-8,
 * 2026-11-05): the caller's signed cancel ({@code ring} with {@code k = cancelled}) — its shape, the device's ONE
 * verifier for the live notice and the silent push ({@link CallRschat#checkCancel}), the ring-ordering rule
 * ({@link CallRschat#isNotOlderThanRing}), and the cross-platform vector {@code call/call_cancel_vector_v1.json} the
 * Dart port (tkm_calls {@code call_cancel_vector_test.dart}) verifies case by case, in BOTH forms (the push data and
 * the live notification): the committed copy must equal what this reference generates now (drift guard); regenerate
 * with {@code -Dcall.vectors.write=true}.
 */
class CallCancelTest {

    static final String RESOURCE = "/call/call_cancel_vector_v1.json";
    static final String AUD = CallRschat.audience("test");
    static final String CALL = "ab".repeat(32);
    static final String NONCE = "00".repeat(31) + "bb";
    /** The ring's own signed ts; the cancels below are 20 s later. */
    static final long RING_TS = 1_700_000_000_000L;
    static final long TS = RING_TS + 20_000L;
    static final String SVC = "london-test-1";
    static final AsymmetricCipherKeyPair CALLER = CallTestKeys.identity("p2-caller");

    static String id(AsymmetricCipherKeyPair kp) {
        return CallSignatures.identityOf(kp);
    }

    static AsymmetricCipherKeyPair callee(int i) {
        return CallTestKeys.identity("p2-callee-" + i);
    }

    static List<String> callees(int n) {
        List<String> to = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            to.add(id(callee(i)));
        }
        return to;
    }

    static CallRingBean signedCancel(AsymmetricCipherKeyPair signer, List<String> to, String aud, long ts) {
        CallRingBean r = CallRschat.cancelled(CALL, SVC, to, CallConstants.MODE_STANDARD, NONCE, aud, ts);
        CallSignatures.sign(r, signer);
        return r;
    }

    /** The push data rschat sends (FcmService.buildCallCancelledMessageBody): type, call_id, f, v, ring. */
    static Map<String, String> pushData(String callId, String f, String ringField) {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("type", CallRschat.NOTIFICATION_TYPE_CANCELLED);
        d.put("call_id", callId);
        d.put("f", f);
        d.put("v", "1");
        if (ringField != null) {
            d.put(CallRschat.PUSH_RING_FIELD, ringField);
        }
        return d;
    }

    /** The live notification rschat emits for the same cancel (CallRingService.deliverCancelled). */
    static UserNotificationJsonBean liveNotice(String callId, String f, String me, String signed) {
        return new UserNotificationJsonBean("00".repeat(16), f, me, TS, CallRschat.NOTIFICATION_TYPE_CANCELLED, null, false,
                new CallRingNoticeBean(callId, SVC, f, CallConstants.MODE_STANDARD), signed);
    }

    // ---- the object ---------------------------------------------------------------------------------------------------

    @Test
    void aCancelIsTheCallersRingWithKCancelledAndARingsShape() throws Exception {
        CallRingBean c = signedCancel(CALLER, callees(2), AUD, TS);
        assertEquals("cancelled", c.getK());
        assertTrue(CallRschat.isCancelled(c));
        assertFalse(CallRschat.isAnswered(c));
        assertTrue(CallJson.toWire(c).contains("\"k\":\"cancelled\""));
        CallSignatures.verify(c, CallConstants.T_RING, AUD);
        CallRschat.checkRing(c, CallRschat.RING_TO_MAX);
        // a ring is not a cancel
        CallRingBean ring = CallRschat.ring(CALL, SVC, callees(1), CallConstants.MODE_STANDARD, NONCE, AUD, TS);
        assertFalse(CallRschat.isCancelled(ring));
        assertFalse(CallRschat.isCancelled(null));
    }

    @Test
    void aCancelNeverNamesItsSignerAndTheSignatureCoversK() throws Exception {
        CallRingBean self = signedCancel(CALLER, List.of(id(CALLER)), AUD, TS);
        assertEquals(CallError.BAD_SIGNATURE,
                assertThrows(CallProtocolException.class, () -> CallRschat.checkRing(self, 25)).getError());
        CallRingBean mixed = signedCancel(CALLER, List.of(id(callee(1)), id(CALLER)), AUD, TS);
        assertThrows(CallProtocolException.class, () -> CallRschat.checkRing(mixed, 25));
        // over ring-to-max is the limit, as for a ring
        CallRingBean over = signedCancel(CALLER, callees(26), AUD, TS);
        assertEquals(CallError.RATE_LIMITED,
                assertThrows(CallProtocolException.class, () -> CallRschat.checkRing(over, 25)).getError());
        // a ring turned into a cancel after signing (or back) does not verify
        CallRingBean ring = CallRschat.ring(CALL, SVC, callees(1), CallConstants.MODE_STANDARD, NONCE, AUD, TS);
        CallSignatures.sign(ring, CALLER);
        ring.setK(CallConstants.RING_K_CANCELLED);
        CallRingBean tampered = CallJson.parse(CallJson.toWire(ring), CallRingBean.class);
        assertThrows(CallProtocolException.class, () -> CallSignatures.verify(tampered, CallConstants.T_RING, AUD));
        CallRingBean c = signedCancel(CALLER, callees(1), AUD, TS);
        c.setK(null);
        CallRingBean stripped = CallJson.parse(CallJson.toWire(c), CallRingBean.class);
        assertThrows(CallProtocolException.class, () -> CallSignatures.verify(stripped, CallConstants.T_RING, AUD));
        // an unknown k is still refused
        CallRingBean other = CallRschat.ring(CALL, SVC, callees(1), CallConstants.MODE_STANDARD, NONCE, AUD, TS);
        other.setK("hungup");
        CallSignatures.sign(other, CALLER);
        assertThrows(CallProtocolException.class, () -> CallRschat.checkRing(other, 25));
    }

    // ---- the device's verifier ----------------------------------------------------------------------------------------

    @Test
    void aCancelVerifiesForACalleeAndNamesItsCaller() throws Exception {
        CallRingBean c = signedCancel(CALLER, callees(3), AUD, TS);
        String signed = CallRschat.pushRing(c);
        CallRingBean v = CallRschat.checkCancel(signed, AUD, CALL, id(CALLER), callees(3).get(2), CallRschat.RING_TO_MAX);
        assertEquals(id(CALLER), v.getF());
        assertEquals(TS, v.getTs());
        assertTrue(CallRschat.isFreshPushRing(v, TS));
        assertTrue(CallRschat.isNotOlderThanRing(v, RING_TS));
        assertTrue(CallRschat.isNotOlderThanRing(v, TS), "same ms");
        assertTrue(CallRschat.isNotOlderThanRing(v, null), "a ring of unknown ts is matched");
        assertFalse(CallRschat.isNotOlderThanRing(v, TS + 1), "a cancel older than the ring never ends it (a re-ring)");
        assertFalse(CallRschat.isNotOlderThanRing(null, RING_TS));
    }

    @Test
    void theLiveNoticeCarriesTheSameSignedCancelAndReadsBack() throws Exception {
        CallRingBean c = signedCancel(CALLER, callees(1), AUD, TS);
        String me = callees(1).get(0);
        UserNotificationJsonBean n = liveNotice(CALL, id(CALLER), me, CallRschat.pushRing(c));
        String json = CallJson.mapper().writeValueAsString(n);
        assertTrue(json.contains("\"signed_ring\":"), json);
        UserNotificationJsonBean back = CallJson.mapper().readValue(json, UserNotificationJsonBean.class);
        CallRingNoticeBean notice = CallRschat.cancelledOf(back).orElseThrow();
        assertEquals(CALL, notice.getCall());
        assertEquals(id(CALLER),
                CallRschat.checkCancel(back.getSignedRing(), AUD, notice.getCall(), notice.getF(), me, 25).getF());
        // a cancel is not a ring nor an answered notice
        assertTrue(CallRschat.ringOf(back).isEmpty());
        assertTrue(CallRschat.answeredOf(back).isEmpty());
        // every other notification is unchanged: no signed_ring on the wire
        UserNotificationJsonBean chat = new UserNotificationJsonBean("h", "s", "r", 1L, "NEW_MESSAGE", "c", false);
        assertFalse(CallJson.mapper().writeValueAsString(chat).contains("signed_ring"));
        assertNull(chat.getSignedRing());
        // a notice without the signed object names a cancel, but nothing verifies it (the device ignores it)
        UserNotificationJsonBean bare = liveNotice(CALL, id(CALLER), me, null);
        assertTrue(CallRschat.cancelledOf(bare).isPresent());
        assertThrows(CallProtocolException.class,
                () -> CallRschat.checkCancel(bare.getSignedRing(), AUD, CALL, id(CALLER), me, 25));
    }

    @Test
    void refusals() throws Exception {
        CallRingBean c = signedCancel(CALLER, callees(2), AUD, TS);
        String good = CallRschat.pushRing(c);
        String me = callees(2).get(0);
        // a co-callee's own cancel verifies — as HIS — so it never matches the caller's ring (the device rule)
        CallRingBean m = signedCancel(callee(2), List.of(me), AUD, TS);
        assertEquals(id(callee(2)), CallRschat.checkCancel(CallRschat.pushRing(m), AUD, CALL, id(callee(2)), me, 25).getF());
        // … and it cannot pass as the caller's
        refused(CallRschat.pushRing(m), AUD, CALL, id(CALLER), me);
        // the wrong signer: f rewritten after signing
        refused(good.replace(id(CALLER), id(callee(3))), AUD, CALL, id(callee(3)), me);
        // not for me
        refused(good, AUD, CALL, id(CALLER), id(callee(3)));
        // me unknown (F-9): never acted on
        refused(good, AUD, CALL, id(CALLER), null);
        // another call
        refused(good, AUD, "cd".repeat(32), id(CALLER), me);
        // another network
        refused(good, CallRschat.audience("prod"), CALL, id(CALLER), me);
        // a ring, an answered notice
        CallRingBean ring = CallRschat.ring(CALL, SVC, callees(2), CallConstants.MODE_STANDARD, NONCE, AUD, TS);
        CallSignatures.sign(ring, CALLER);
        refused(CallRschat.pushRing(ring), AUD, CALL, id(CALLER), me);
        CallRingBean a = CallRschat.answered(CALL, SVC, CallConstants.MODE_STANDARD, me, NONCE, AUD, TS);
        CallSignatures.sign(a, callee(1));
        refused(CallRschat.pushRing(a), AUD, CALL, me, me);
        // junk, absent
        refused("not json", AUD, CALL, id(CALLER), me);
        refused(null, AUD, CALL, id(CALLER), me);
        refused(good, null, CALL, id(CALLER), me);
        // stale: verifies, not fresh
        CallRingBean stale = signedCancel(CALLER, callees(1), AUD, TS);
        assertFalse(CallRschat.isFreshPushRing(stale, TS + CallRschat.PUSH_RING_MAX_AGE_MS + 1));
    }

    static void refused(String signed, String aud, String callId, String f, String me) {
        CallProtocolException ex = assertThrows(CallProtocolException.class,
                () -> CallRschat.checkCancel(signed, aud, callId, f, me, CallRschat.RING_TO_MAX));
        assertEquals(CallError.BAD_SIGNATURE, ex.getError());
    }

    // ---- the cross-platform vector file ------------------------------------------------------------------------------

    static String generate() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("_origin", "Messages CallCancelTest (P2 caller cancel, 2026-11-05; Iris §7.123 I2/RA-5): the caller's signed "
                + "cancel (ring with k=cancelled). Each case is the silent push `data` AND the live `notification` a device "
                + "receives for the same cancel; ONE verifier (CallRschat.checkCancel: signed object, call id, f, me) gives "
                + "`expect.verified` for both under `aud`; `expect.fresh` = isFreshPushRing at `now_ms`; `expect.after_ring` = "
                + "isNotOlderThanRing against the ring's signed ts `ring_ts`. A verified cancel stops a ring only when its f is "
                + "that ring's VERIFIED caller (co_callee_cancel verifies as the co-callee, never as the caller). Keys: seeds "
                + "are SHA3-256(ASCII(\"tkm-call/v1/vectors/identity/\" + name)) (CallTestKeys.identity).");
        root.put("aud", AUD);
        root.put("now_ms", TS + 10_000L);
        root.put("ring_ts", RING_TS);
        root.put("max_age_ms", CallRschat.PUSH_RING_MAX_AGE_MS);
        root.put("max_skew_ms", CallRschat.PUSH_RING_MAX_SKEW_MS);
        root.put("fcm_data_max_bytes", CallRschat.FCM_DATA_MAX_BYTES);
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("p2-caller", key("p2-caller"));
        for (int i = 1; i <= 25; i++) {
            keys.put("p2-callee-" + i, key("p2-callee-" + i));
        }
        root.put("keys", keys);
        final String caller = id(CALLER);
        final List<String> one = callees(1);
        final List<String> all = callees(25);
        final long now = TS + 10_000L;
        List<Map<String, Object>> cases = new ArrayList<>();
        CallRingBean c1 = signedCancel(CALLER, one, AUD, TS);
        CallRingBean c25 = signedCancel(CALLER, all, AUD, TS);
        String good = CallRschat.pushRing(c1);
        cases.add(vectorCase("one_callee", CALL, caller, good, one.get(0), now));
        cases.add(vectorCase("twenty_five_callees", CALL, caller, CallRschat.pushRing(c25), all.get(24), now));
        cases.add(vectorCase("no_push_identity_known", CALL, caller, good, null, now));
        cases.add(vectorCase("not_for_me", CALL, caller, good, all.get(1), now));
        cases.add(vectorCase("tampered_signature", CALL, caller,
                good.replace("\"sg\":\"" + c1.getSg().substring(0, 4), "\"sg\":\"" + (c1.getSg().startsWith("0000") ? "1111" : "0000")),
                one.get(0), now));
        cases.add(vectorCase("callee_swapped_after_signing", CALL, caller, good.replace(one.get(0), all.get(1)), all.get(1), now));
        cases.add(vectorCase("call_id_mismatch", "cd".repeat(32), caller, good, one.get(0), now));
        cases.add(vectorCase("caller_mismatch", CALL, all.get(2), good, one.get(0), now));
        CallRingBean prod = signedCancel(CALLER, one, CallRschat.audience("prod"), TS);
        cases.add(vectorCase("wrong_audience", CALL, caller, CallRschat.pushRing(prod), one.get(0), now));
        CallRingBean ring = CallRschat.ring(CALL, SVC, one, CallConstants.MODE_STANDARD, NONCE, AUD, TS);
        CallSignatures.sign(ring, CALLER);
        cases.add(vectorCase("a_ring_is_not_a_cancel", CALL, caller, CallRschat.pushRing(ring), one.get(0), now));
        CallRingBean a = CallRschat.answered(CALL, SVC, CallConstants.MODE_STANDARD, one.get(0), NONCE, AUD, TS);
        CallSignatures.sign(a, callee(1));
        cases.add(vectorCase("answered_notice", CALL, one.get(0), CallRschat.pushRing(a), one.get(0), now));
        CallRingBean self = signedCancel(CALLER, List.of(one.get(0), caller), AUD, TS);
        cases.add(vectorCase("to_names_the_caller", CALL, caller, CallRschat.pushRing(self), one.get(0), now));
        // a co-callee (p2-callee-2) signs a cancel of the caller's call id as himself: it verifies as HIS, so a device
        // ringing for the caller's ring must not stop (f differs from the ring's verified caller)
        CallRingBean m = signedCancel(callee(2), one, AUD, TS);
        cases.add(vectorCase("co_callee_cancel", CALL, id(callee(2)), CallRschat.pushRing(m), one.get(0), now));
        CallRingBean stale = signedCancel(CALLER, one, AUD, now - CallRschat.PUSH_RING_MAX_AGE_MS - 1);
        cases.add(vectorCase("stale", CALL, caller, CallRschat.pushRing(stale), one.get(0), now));
        CallRingBean future = signedCancel(CALLER, one, AUD, now + CallRschat.PUSH_RING_MAX_SKEW_MS + 1);
        cases.add(vectorCase("from_the_future", CALL, caller, CallRschat.pushRing(future), one.get(0), now));
        CallRingBean older = signedCancel(CALLER, one, AUD, RING_TS - 1);
        cases.add(vectorCase("older_than_the_ring", CALL, caller, CallRschat.pushRing(older), one.get(0), now));
        cases.add(vectorCase("no_ring_field", CALL, caller, null, one.get(0), now));
        root.put("cases", cases);
        return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(root) + "\n";
    }

    static Map<String, Object> key(String name) {
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("seed_hex", CallBytes.hex(CallTestKeys.seed("identity/" + name)));
        k.put("identity", id(CallTestKeys.identity(name)));
        return k;
    }

    static Map<String, Object> vectorCase(String name, String callId, String f, String signed, String me, long now)
            throws Exception {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", name);
        c.put("me", me);
        Map<String, String> data = pushData(callId, f, signed);
        c.put("data", data);
        int bytes = 0;
        for (Map.Entry<String, String> e : data.entrySet()) {
            bytes += e.getKey().getBytes(StandardCharsets.UTF_8).length + e.getValue().getBytes(StandardCharsets.UTF_8).length;
        }
        c.put("data_bytes", bytes);
        c.put("notification", CallJson.mapper().readTree(CallJson.mapper().writeValueAsString(
                liveNotice(callId, f, me == null ? callees(1).get(0) : me, signed))));
        Map<String, Object> expect = new LinkedHashMap<>();
        CallRingBean v = null;
        try {
            v = CallRschat.checkCancel(signed, AUD, callId, f, me, CallRschat.RING_TO_MAX);
        } catch (CallProtocolException ex) {
            // refused
        }
        expect.put("verified", v != null);
        if (v != null) {
            expect.put("f", v.getF());
            expect.put("ts", v.getTs());
            expect.put("fresh", CallRschat.isFreshPushRing(v, now));
            expect.put("after_ring", CallRschat.isNotOlderThanRing(v, RING_TS));
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
        try (InputStream in = CallCancelTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "missing " + RESOURCE + " — run with -Dcall.vectors.write=true");
            committed = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(generated, committed, "the committed vector file drifted from the reference");
    }

    @Test
    void theVectorPinsTheIntendedVerdicts() throws Exception {
        JsonNode root;
        try (InputStream in = CallCancelTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in);
            root = new ObjectMapper().readTree(in);
        }
        Map<String, String> verdict = new LinkedHashMap<>();
        for (JsonNode c : root.get("cases")) {
            JsonNode e = c.get("expect");
            String v = e.get("verified").asBoolean()
                    ? "ok" + (e.get("fresh").asBoolean() ? "" : "/stale") + (e.get("after_ring").asBoolean() ? "" : "/older")
                    : "refused";
            verdict.put(c.get("name").asText(), v);
            assertTrue(c.get("data_bytes").asInt() <= CallRschat.FCM_DATA_MAX_BYTES - 256, c.get("name") + ": over the push budget");
        }
        Map<String, String> want = new LinkedHashMap<>();
        want.put("one_callee", "ok");
        want.put("twenty_five_callees", "ok");
        want.put("no_push_identity_known", "refused");
        want.put("not_for_me", "refused");
        want.put("tampered_signature", "refused");
        want.put("callee_swapped_after_signing", "refused");
        want.put("call_id_mismatch", "refused");
        want.put("caller_mismatch", "refused");
        want.put("wrong_audience", "refused");
        want.put("a_ring_is_not_a_cancel", "refused");
        want.put("answered_notice", "refused");
        want.put("to_names_the_caller", "refused");
        want.put("co_callee_cancel", "ok");
        want.put("stale", "ok/stale/older"); // 90 s back is also before the ring
        want.put("from_the_future", "ok/stale");
        want.put("older_than_the_ring", "ok/older");
        want.put("no_ring_field", "refused");
        assertEquals(want, verdict);
        // the co-callee's cancel names HIM, never the caller
        for (JsonNode c : root.get("cases")) {
            if ("co_callee_cancel".equals(c.get("name").asText())) {
                assertEquals(root.get("keys").get("p2-callee-2").get("identity").asText(), c.get("expect").get("f").asText());
            }
        }
    }

    @Test
    void everyVectorCaseReDerivesFromTheFileAloneInBothForms() throws Exception {
        JsonNode root;
        try (InputStream in = CallCancelTest.class.getResourceAsStream(RESOURCE)) {
            root = new ObjectMapper().readTree(in);
        }
        String aud = root.get("aud").asText();
        for (JsonNode c : root.get("cases")) {
            String name = c.get("name").asText();
            String me = c.get("me").isNull() ? null : c.get("me").asText();
            boolean want = c.get("expect").get("verified").asBoolean();
            JsonNode d = c.get("data");
            String push = d.has("ring") ? d.get("ring").asText() : null;
            assertEquals(want, verifies(push, aud, d.get("call_id").asText(), d.get("f").asText(), me), name + " (push)");
            UserNotificationJsonBean n = CallJson.mapper().treeToValue(c.get("notification"), UserNotificationJsonBean.class);
            CallRingNoticeBean notice = CallRschat.cancelledOf(n).orElseThrow();
            assertEquals(want, verifies(n.getSignedRing(), aud, notice.getCall(), notice.getF(), me), name + " (live)");
        }
    }

    static boolean verifies(String signed, String aud, String callId, String f, String me) {
        try {
            CallRschat.checkCancel(signed, aud, callId, f, me, CallRschat.RING_TO_MAX);
            return true;
        } catch (CallProtocolException ex) {
            return false;
        }
    }
}
