package io.takamaka.messages.call;

import io.takamaka.messages.call.beans.CallRingBean;
import io.takamaka.messages.call.rschat.CallRschat;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DR-063 §8 / Iris §7.194 Q3: the worst-case {@code J} of the sealed call pushes, built through the real beans and
 * {@link CallRschat} (as rschat's {@code FcmService} builds the clear maps), pinned against {@code PAD − 2}. A new field
 * in the ring, a longer value or a raised {@link CallRschat#RING_TO_MAX} fails THIS test, not a ring on a phone.
 *
 * <p>Worst case = {@code RING_TO_MAX} callees, {@code svc} at its 64-character maximum, {@code mode = conference}
 * (the longest value), {@code media = audio} on the ring (S0, only a ring carries it), {@code aud} present, a 64-hex
 * nonce (32 bytes, the §8.1 form), a 13-digit {@code ts}, a 128-hex signature. Escaping adds nothing: every value
 * is ASCII without quotes or backslashes, except the {@code ring} string itself whose quotes JCS escapes.
 * The network name of {@code aud} is the deployment's: measured for {@code test} / {@code prod}, for
 * {@code production} (a 10-character name) and for the 32-character maximum {@code CallRschat.audience} admits —
 * which does NOT fit. DR-063 draft 2 settles that case with no cap on the name: rschat's boot check
 * ({@code CallGate.worstCaseJ}, this test's computation) refuses any configuration whose worst case exceeds
 * {@code PAD − 2} (§3.2), and an oversize push is never sent (§3.1).
 *
 * <p>Iris §7.194 measured 2039 B; this test measures 2031 B at a 4-character network WITH {@code media = audio}.
 * The design's 2011 B (design §1.4) already included {@code aud} (the vector ring carries it), so 2011 + 20
 * ({@code ,\"media\":\"audio\"} inside the escaped ring) = 2031.
 */
class PushSealSizeTest {

    static final String CALL = "cd".repeat(32);
    static final String SVC_MAX = "s".repeat(64);
    static final String NONCE = "0f".repeat(32);
    static final long TS = 1_799_999_999_999L;
    static final AsymmetricCipherKeyPair CALLER = CallTestKeys.identity("push-seal-size-caller");

    static List<String> callees(int n) {
        List<String> to = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            to.add(CallSignatures.identityOf(CallTestKeys.identity("push-seal-size-callee-" + i)));
        }
        return to;
    }

    /** The clear CALL_RING map, as FcmService.buildCallRingMessageBody: type, call_id, svc, f, mode, v, ring. */
    static Map<String, String> worstRingMap(String network) throws Exception {
        CallRingBean r = CallRschat.ring(CALL, SVC_MAX, callees(CallRschat.RING_TO_MAX), CallConstants.MODE_CONFERENCE,
                NONCE, CallRschat.audience(network), TS);
        r.setMedia(CallConstants.RING_MEDIA_AUDIO);
        CallSignatures.sign(r, CALLER);
        CallRschat.checkRing(r, CallRschat.RING_TO_MAX);
        Map<String, String> m = new LinkedHashMap<>();
        m.put("type", CallRschat.NOTIFICATION_TYPE);
        m.put("call_id", r.getCall());
        m.put("svc", r.getSvc());
        m.put("f", r.getF());
        m.put("mode", r.getMode());
        m.put("v", "1");
        m.put(CallRschat.PUSH_RING_FIELD, CallRschat.pushRing(r));
        return m;
    }

    /** The clear CALL_CANCELLED map: type, call_id, f, v, ring = the caller's signed cancel. */
    static Map<String, String> worstCancelMap(String network) throws Exception {
        CallRingBean c = CallRschat.cancelled(CALL, SVC_MAX, callees(CallRschat.RING_TO_MAX),
                CallConstants.MODE_CONFERENCE, NONCE, CallRschat.audience(network), TS);
        CallSignatures.sign(c, CALLER);
        CallRschat.checkRing(c, CallRschat.RING_TO_MAX);
        Map<String, String> m = new LinkedHashMap<>();
        m.put("type", CallRschat.NOTIFICATION_TYPE_CANCELLED);
        m.put("call_id", c.getCall());
        m.put("f", c.getF());
        m.put("v", "1");
        m.put(CallRschat.PUSH_RING_FIELD, CallRschat.pushRing(c));
        return m;
    }

    /** The clear CALL_ANSWERED map: type, call_id, v (fixed size). */
    static Map<String, String> answeredMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("type", CallRschat.NOTIFICATION_TYPE_ANSWERED);
        m.put("call_id", CALL);
        m.put("v", "1");
        return m;
    }

    static int j(Map<String, String> m) throws Exception {
        return PushSeal.frame(m).length;
    }

    /** Seals it for real and checks the clear FCM data {type, v, sp} fits FCM. */
    static void sealsAndFits(String type, Map<String, String> m) throws Exception {
        SecureRandom rnd = new SecureRandom();
        byte[] d = new byte[32];
        rnd.nextBytes(d);
        String sp = PushSeal.seal(CallCrypto.x25519Public(d), type, m, rnd);
        assertEquals(PushSeal.SP_LEN, sp.length());
        assertEquals(m, PushSeal.open(d, type, sp));
        int data = ("{\"type\":\"" + type + "\",\"v\":\"2\",\"sp\":\"" + sp + "\"}").length();
        assertTrue(data <= CallRschat.FCM_DATA_MAX_BYTES, "data " + data);
    }

    /**
     * A 10-character production network name. rschat has no list of production names any more: under DR-063 §3.2
     * {@code CallGate.isNonProductionNetwork} allow-lists {@code test}, {@code dev}, {@code localhost} and
     * {@code rig*}, and every other name is production. This one is a sample, not a maximum.
     */
    static final String PRODUCTION = "production";
    /** The longest name {@link CallRschat#audience} admits ({@code [a-z0-9-]{1,32}}). */
    static final String NET32 = "n".repeat(32);

    /** The longest network name for which the worst-case ring still fits. */
    static int longestFittingNetwork() throws Exception {
        int n = 1;
        while (n < 32 && j(worstRingMap("n".repeat(n + 1))) <= PushSeal.MAX_J) {
            n++;
        }
        return n;
    }

    @Test
    void worstCaseRingFits() throws Exception {
        int prod = j(worstRingMap("prod"));
        int production = j(worstRingMap(PRODUCTION));
        int net32 = j(worstRingMap(NET32));
        int fitting = longestFittingNetwork();
        System.out.printf("PushSealSizeTest CALL_RING worst-case J (to=%d, svc=64, mode=conference, media=audio, aud):"
                + " test/prod=%d production=%d net32=%d; longest network name that fits=%d; limit PAD-2=%d%n",
                CallRschat.RING_TO_MAX, prod, production, net32, fitting, PushSeal.MAX_J);
        // pinned: a new ring field, a longer value or a raised RING_TO_MAX changes these numbers and fails here
        assertEquals(2031, prod, "worst-case ring J at a 4-character network name");
        assertEquals(j(worstRingMap("test")), prod);
        assertEquals(2037, production);
        assertEquals(19, fitting, "each network character adds one byte to J");
        assertTrue(production <= PushSeal.MAX_J, "worst-case ring J " + production + " > " + PushSeal.MAX_J);
        // a 32-character network name does NOT fit (2059 > 2046): DR-063 §3.2's boot check refuses that configuration,
        // and §3.1 sends nothing for an oversize push (no ring is sealed without `ring`); here the seal refuses it
        assertEquals(2059, net32);
        Map<String, String> tooLong = worstRingMap(NET32);
        assertEquals(PushSeal.Reason.TOO_LONG, org.junit.jupiter.api.Assertions.assertThrows(
                PushSeal.PushSealException.class, () -> sealsAndFits(CallRschat.NOTIFICATION_TYPE, tooLong)).getReason());
        sealsAndFits(CallRschat.NOTIFICATION_TYPE, worstRingMap(PRODUCTION));
        sealsAndFits(CallRschat.NOTIFICATION_TYPE, worstRingMap("n".repeat(fitting)));
    }

    @Test
    void worstCaseCancelFits() throws Exception {
        int prod = j(worstCancelMap("prod"));
        int net32 = j(worstCancelMap(NET32));
        System.out.printf("PushSealSizeTest CALL_CANCELLED worst-case J (to=%d, svc=64, mode=conference, aud):"
                + " test/prod=%d net32=%d; limit PAD-2=%d%n", CallRschat.RING_TO_MAX, prod, net32, PushSeal.MAX_J);
        assertEquals(1943, prod, "worst-case cancel J at a 4-character network name");
        assertEquals(1971, net32);
        assertTrue(net32 <= PushSeal.MAX_J, "worst-case cancel J " + net32 + " > " + PushSeal.MAX_J);
        sealsAndFits(CallRschat.NOTIFICATION_TYPE_CANCELLED, worstCancelMap(NET32));
    }

    @Test
    void answeredIsFixedSize() throws Exception {
        int a = j(answeredMap());
        System.out.printf("PushSealSizeTest CALL_ANSWERED J=%d (fixed); limit PAD-2=%d%n", a, PushSeal.MAX_J);
        assertEquals(109, a);
        sealsAndFits(CallRschat.NOTIFICATION_TYPE_ANSWERED, answeredMap());
    }
}
