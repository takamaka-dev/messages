package io.takamaka.messages.call;

import io.takamaka.messages.call.beans.CallBkeyBean;
import io.takamaka.messages.call.beans.CallDropBean;
import io.takamaka.messages.call.beans.CallHandBean;
import io.takamaka.messages.call.beans.CallLimitsBean;
import io.takamaka.messages.call.beans.CallMeetNoticeBean;
import io.takamaka.messages.call.beans.CallMeetStatusBean;
import io.takamaka.messages.call.beans.CallParamsBean;
import io.takamaka.messages.call.beans.CallSignedObject;
import io.takamaka.messages.call.channel.ChannelState;
import io.takamaka.messages.call.meet.CallMeet;
import io.takamaka.messages.call.rschat.CallRschat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * [0.4] The meet (spec draft 0.4, rulings M1–M10 of 2026-11-03): the new signed objects round-trip and verify, the
 * {@code bkey} seals and opens over a real listener channel, the acting-owner and era-gate arithmetic, the
 * {@code sameKeys} role rule, the list rules, the bounds and the meet notice checks.
 */
class CallMeetTest {

    static CallVectorScenario sc;
    static final String AUD = CallVectorScenario.AUD_SVC;

    @BeforeAll
    static void scenario() throws Exception {
        sc = new CallVectorScenario();
    }

    static AsymmetricCipherKeyPair id(String n) {
        return sc.ids.get(n);
    }

    static String identity(String n) {
        return sc.id(n);
    }

    static String CALL = "00".repeat(32);
    static String LEG = "0102030405060708";

    // ------------------------------------------------------------ the five objects

    @Test
    void newObjectsRoundTripAndVerify() throws Exception {
        CallHandBean hand = new CallHandBean();
        hand.setAud(AUD);
        hand.setN("aa");
        hand.setTs(1L);
        hand.setCall(CALL);
        hand.setLeg(LEG);
        hand.setUp(true);
        CallSignatures.sign(hand, id("carol"));
        CallSignedObject p = CallJson.parse(CallJson.toWire(hand));
        assertTrue(p instanceof CallHandBean);
        assertEquals(hand, p);
        CallSignatures.verify(p, "hand", AUD, identity("carol"));

        CallDropBean drop = new CallDropBean();
        drop.setAud(AUD);
        drop.setN("bb");
        drop.setTs(2L);
        drop.setCall(CALL);
        drop.setLeg(LEG);
        CallSignatures.sign(drop, id("bob"));
        assertEquals(drop, CallJson.parse(CallJson.toWire(drop)));
        CallSignatures.verify(drop, "drop", AUD, identity("bob"));

        CallMeetStatusBean req = new CallMeetStatusBean();
        req.setAud(AUD);
        req.setN("cc");
        req.setTs(3L);
        req.setCall(CALL);
        CallSignatures.sign(req, id("dave"));
        assertFalse(req.isAnswer());
        CallMeetStatusBean ans = new CallMeetStatusBean();
        ans.setAud(CallConstants.AUD_ID + identity("dave"));
        ans.setTs(4L);
        ans.setCall(CALL);
        ans.setLive(true);
        ans.setSpeakers(2);
        ans.setListeners(7);
        ans.setSince(0L);
        CallSignatures.sign(ans, id("service"));
        assertTrue(ans.isAnswer());
        assertEquals(ans, CallJson.parse(CallJson.toWire(ans)));
        CallSignatures.verify(ans, "meetstatus", CallConstants.AUD_ID + identity("dave"), identity("service"));

        CallMeetNoticeBean notice = CallRschat.meetNotice(CALL, "svc-1", List.of(identity("dave"), identity("carol")),
                "dd", CallVectorScenario.AUD_RSCHAT, 5L);
        CallSignatures.sign(notice, id("owner"));
        assertEquals(notice, CallJson.parse(CallJson.toWire(notice)));
        CallSignatures.verify(notice, "meetnotice", CallVectorScenario.AUD_RSCHAT, identity("owner"));
        CallRschat.checkMeetNotice(notice, CallRschat.MEETNOTICE_TO_MAX);

        // the signature context is the type: a hand presented as a drop fails
        CallProtocolException ex = assertThrows(CallProtocolException.class, () -> CallSignatures.verify(hand, "drop", AUD));
        assertEquals(CallError.BAD_SIGNATURE, ex.getError());
    }

    @Test
    void meetNoticeChecks() {
        CallMeetNoticeBean n = CallRschat.meetNotice(CALL, "svc-1", List.of(identity("dave")), "dd",
                CallVectorScenario.AUD_RSCHAT, 5L);
        n.setF(identity("owner"));
        assertDoesNotThrow(() -> CallRschat.checkMeetNotice(n, 100));
        n.setMode(CallConstants.MODE_STANDARD);
        assertEquals(CallError.BAD_SIGNATURE, assertThrows(CallProtocolException.class,
                () -> CallRschat.checkMeetNotice(n, 100)).getError());
        n.setMode(CallConstants.MODE_CONFERENCE);
        CallProtocolException ex = assertThrows(CallProtocolException.class, () -> CallRschat.checkMeetNotice(n, 0));
        assertEquals(CallError.RATE_LIMITED, ex.getError());
        assertEquals(CallRschat.LIMIT_MEETNOTICE_TO_MAX, ex.getReason());
    }

    // ------------------------------------------------------------ bkey over a listener channel

    @Test
    void bkeySealsAndOpensOverTheListenerChannel() throws Exception {
        ChannelState committerSide = sc.chOB.state();   // owner → bob, both ends at the same k after the scenario
        ChannelState listenerSide = sc.accOB.state();
        long k = committerSide.chainIndex();
        assertEquals(k, listenerSide.chainIndex());
        byte[] key = CallTestKeys.seed("broadcast/test");
        String bobLeg = CallBytes.hex(sc.legs.get("bob").legId);
        CallMeet.BuiltBkey built = CallMeet.buildBkey(AUD, 10L, sc.callId, sc.eraHash1, 5, key, Map.of(bobLeg, committerSide),
                id("owner"));
        CallBkeyBean b = built.bkey();
        assertEquals(1, b.getBoxes().size());
        assertEquals(k, b.getBoxes().get(0).getK());
        assertEquals(built.canonicalHeader(), CallMeet.canonicalHeader(b));
        assertFalse(built.canonicalHeader().contains("boxes"));
        CallBkeyBean parsed = CallJson.parse(CallJson.toWire(b), CallBkeyBean.class);
        byte[] opened = CallMeet.acceptBkey(parsed, AUD, Set.of(identity("owner")), sc.eraHash1, 4, bobLeg, listenerSide);
        assertArrayEquals(key, opened);
        // wrong signer set, wrong epoch, wrong era, wrong leg → key_refused
        assertEquals(CallError.KEY_REFUSED, assertThrows(CallProtocolException.class, () -> CallMeet.acceptBkey(parsed, AUD,
                Set.of(identity("bob")), sc.eraHash1, 4, bobLeg, listenerSide)).getError());
        assertEquals(CallError.KEY_REFUSED, assertThrows(CallProtocolException.class, () -> CallMeet.acceptBkey(parsed, AUD,
                Set.of(identity("owner")), sc.eraHash1, 5, bobLeg, listenerSide)).getError());
        assertEquals(CallError.KEY_REFUSED, assertThrows(CallProtocolException.class, () -> CallMeet.acceptBkey(parsed, AUD,
                Set.of(identity("owner")), sc.eraHash0, 4, bobLeg, listenerSide)).getError());
        assertEquals(CallError.KEY_REFUSED, assertThrows(CallProtocolException.class, () -> CallMeet.acceptBkey(parsed, AUD,
                Set.of(identity("owner")), sc.eraHash1, 4, "ffffffffffffffff", listenerSide)).getError());
        // a tampered box does not open
        parsed.getBoxes().get(0).setBox("00" + parsed.getBoxes().get(0).getBox().substring(2));
        CallSignatures.sign(parsed, id("owner"));
        assertEquals("box does not open", assertThrows(CallProtocolException.class, () -> CallMeet.acceptBkey(parsed, AUD,
                Set.of(identity("owner")), sc.eraHash1, 4, bobLeg, listenerSide)).getReason());
    }

    // ------------------------------------------------------------ §4.4 / §6.5 arithmetic

    @Test
    void actingOwnerRule() {
        String o = "O", b = "B", e = "E";
        List<CallMeet.RosterLeg> withOwner = List.of(new CallMeet.RosterLeg(0, o), new CallMeet.RosterLeg(1, b));
        List<CallMeet.RosterLeg> without = List.of(new CallMeet.RosterLeg(3, e), new CallMeet.RosterLeg(1, b));
        long ts = 1_000_000L, timeout = 30_000L;
        assertNull(CallMeet.actingOwner(o, withOwner, ts, timeout, ts + 100_000), "creator present");
        assertNull(CallMeet.actingOwner(o, without, ts, timeout, ts + 29_999), "before the timeout");
        assertEquals(b, CallMeet.actingOwner(o, without, ts, timeout, ts + 30_000), "lowest leg_index speaker");
        assertNull(CallMeet.actingOwner(o, List.of(), ts, timeout, ts + 60_000), "empty roster");
    }

    @Test
    void eraGateSlot() {
        assertEquals(10_000, CallMeet.gateSlot(0, 0, 10_000, 2_000));
        assertEquals(10_000, CallMeet.gateSlot(0, 3_000, 10_000, 2_000));
        assertEquals(11_500, CallMeet.gateSlot(0, 9_500, 10_000, 2_000), "extended by the latest era + committer_timeout");
        assertThrows(IllegalArgumentException.class, () -> CallMeet.gateSlot(5, 4, 10_000, 2_000));
        assertTrue(CallConstants.isEnlargingReason("add"));
        assertTrue(CallConstants.isEnlargingReason("promote"));
        assertTrue(CallConstants.isEnlargingReason("succeed"));
        assertFalse(CallConstants.isEnlargingReason("remove"));
        assertFalse(CallConstants.isEnlargingReason("demote"));
        assertTrue(CallConstants.ERA_REASONS.contains("succeed"));
    }

    @Test
    void sameKeysRoleRule() {
        Set<String> inv = Set.of("O", "E"), alist = Set.of("L");
        assertNull(CallMeet.roleChangeViolation("listener", "listener", "e0", "e0", "E", inv, alist), "no change");
        assertNull(CallMeet.roleChangeViolation("listener", "speaker", "e0", "e1", "E", inv, alist), "promoted under a new era");
        assertEquals("role change under an unchanged era", CallMeet.roleChangeViolation("listener", "speaker", "e0", "e0", "E", inv, alist));
        assertEquals("promoted identity not in inv", CallMeet.roleChangeViolation("listener", "speaker", "e0", "e1", "L", inv, alist));
        assertEquals("demoted identity not in alist", CallMeet.roleChangeViolation("speaker", "listener", "e0", "e1", "E", inv, alist));
        assertEquals("role", CallMeet.roleChangeViolation("speaker", "owner", "e0", "e1", "E", inv, alist));
    }

    @Test
    void meetListRules() {
        List<String> inv = List.of("A", "B");
        assertDoesNotThrow(() -> CallMeet.checkMeetLists("conference", inv, "invited", List.of("C", "D"), List.of("B"), 50, 200));
        assertDoesNotThrow(() -> CallMeet.checkMeetLists("conference", inv, "invited", null, null, 50, 200), "empty audience is legal (M2)");
        assertDoesNotThrow(() -> CallMeet.checkMeetLists("standard", inv, null, null, List.of("A"), 50, 200));
        assertEquals(CallError.APOL_UNSUPPORTED, err(() -> CallMeet.checkMeetLists("conference", inv, "open", List.of(), null, 50, 200)));
        CallProtocolException cap = assertThrows(CallProtocolException.class,
                () -> CallMeet.checkMeetLists("conference", inv, "invited", List.of(), null, 1, 200));
        assertEquals(CallError.RATE_LIMITED, cap.getError());
        assertEquals("cap", cap.getReason());
        assertEquals("alist_max", assertThrows(CallProtocolException.class,
                () -> CallMeet.checkMeetLists("conference", inv, "invited", List.of("C", "D"), null, 50, 1)).getReason());
        assertEquals(CallError.BAD_SIGNATURE, err(() -> CallMeet.checkMeetLists("conference", inv, "invited", List.of("D", "C"), null, 50, 200)));
        assertEquals(CallError.BAD_SIGNATURE, err(() -> CallMeet.checkMeetLists("conference", inv, "invited", List.of("B"), null, 50, 200)));
        assertEquals(CallError.BAD_SIGNATURE, err(() -> CallMeet.checkMeetLists("conference", inv, "invited", List.of("C"), List.of("C"), 50, 200)));
        assertEquals(CallError.BAD_SIGNATURE, err(() -> CallMeet.checkMeetLists("standard", inv, "invited", null, null, 50, 200)));
    }

    @Test
    void meetBounds() {
        CallParamsBean p = CallParamsBean.meetValues();
        assertNull(CallParamsBounds.violation(p));
        assertEquals(30_000L, p.ownerTimeoutOrDefault());
        assertEquals(10_000L, p.eraGateOrDefault());
        assertNull(CallParamsBounds.violation(CallParamsBean.initialValues()), "absent meet keys = the defaults");
        p.setEraGate(60_000L);
        assertTrue(CallParamsBounds.violation(p).startsWith("era_gate"), "above refuse_below and the cap");
        p.setEraGate(1_000L);
        assertTrue(CallParamsBounds.violation(p).startsWith("era_gate"), "below committer_timeout");
        p.setEraGate(10_000L);
        p.setOwnerTimeout(300_001L);
        assertTrue(CallParamsBounds.violation(p).startsWith("owner_timeout"));
        p.setOwnerTimeout(30_000L);
        CallLimitsBean l = CallLimitsBean.meetValues(50);
        assertEquals(200, l.getAlistMax());
        assertEquals(25, l.getRingToMax());
        assertNull(CallParamsBounds.limitsViolation(l, p));
        assertNull(CallParamsBounds.limitsViolation(CallLimitsBean.initialValues(), p), "listeners_max absent = not served");
        l.setListenersMax(200);
        assertNull(CallParamsBounds.limitsViolation(l, p), "the manifest ceiling");
        l.setListenersMax(201);
        assertTrue(CallParamsBounds.limitsViolation(l, p).startsWith("listeners_max"));
        l.setListenersMax(200);
        l.setAlistMax(100);
        assertTrue(CallParamsBounds.limitsViolation(l, p).startsWith("alist_max"));
        l.setAlistMax(200);
        p.setCap(100);
        assertTrue(CallParamsBounds.limitsViolation(l, p).startsWith("cap + listeners_max"));
        p.setCap(50);
        l.setRingToMax(51);
        assertTrue(CallParamsBounds.limitsViolation(l, p).startsWith("ring_to_max"));
    }

    static CallError err(org.junit.jupiter.api.function.Executable e) {
        return assertThrows(CallProtocolException.class, e).getError();
    }
}
