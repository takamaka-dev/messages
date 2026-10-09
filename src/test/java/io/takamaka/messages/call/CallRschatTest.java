package io.takamaka.messages.call;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.takamaka.messages.call.beans.CallLookupBean;
import io.takamaka.messages.call.beans.CallRingBean;
import io.takamaka.messages.call.rschat.CallRingNoticeBean;
import io.takamaka.messages.call.rschat.CallRschat;
import io.takamaka.messages.call.service.CallErrorBean;
import io.takamaka.messages.call.service.CallReplyBean;
import io.takamaka.messages.chat.constant.ChatServerEndpoints;
import io.takamaka.messages.chat.conversation.CreateConversationRequestBean;
import io.takamaka.messages.chat.conversation.CreateConversationResponseBean;
import io.takamaka.messages.chat.notification.UserNotificationJsonBean;
import io.takamaka.messages.utils.NOTIFICATION_TYPES;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C182 build 3: the rschat side of tkm-call/v1 — ring (§7.1), lookup (§8.3), the CALL_RING notice, the typed
 * {@code exists_member} result of {@code createconversation} (§10.1/§11), and the three rschat route names.
 */
class CallRschatTest {

    static final AsymmetricCipherKeyPair OWNER = CallTestKeys.identity("owner");
    static final AsymmetricCipherKeyPair DAVE = CallTestKeys.identity("dave");
    static final AsymmetricCipherKeyPair SERVICE = CallTestKeys.identity("service");
    static final String AUD = CallRschat.audience("test");
    static final String CALL = "ab".repeat(32);
    static final String NONCE = "00".repeat(31) + "aa";

    static String id(AsymmetricCipherKeyPair kp) {
        return CallSignatures.identityOf(kp);
    }

    static CallRingBean signedRing(List<String> to) {
        CallRingBean r = CallRschat.ring(CALL, "london-test-1", to, CallConstants.MODE_STANDARD, NONCE, AUD, 1_700_000_000_000L);
        CallSignatures.sign(r, OWNER);
        return r;
    }

    @Test
    void audienceIsRschatNet() {
        assertEquals("rschat:test", AUD);
        assertThrows(IllegalArgumentException.class, () -> CallRschat.audience("Test:x"));
        assertThrows(IllegalArgumentException.class, () -> CallRschat.audience(null));
    }

    @Test
    void ringSignsVerifiesAndPassesTheFieldChecks() throws Exception {
        CallRingBean r = signedRing(List.of(id(DAVE)));
        String wire = CallJson.toWire(r);
        CallRingBean back = CallJson.parse(wire, CallRingBean.class);
        CallSignatures.verify(back, CallConstants.T_RING, AUD);
        CallRschat.checkRing(back, CallRschat.RING_TO_MAX);
        CallRingNoticeBean n = CallRschat.notice(back);
        assertEquals(new CallRingNoticeBean(CALL, "london-test-1", id(OWNER), "standard"), n);
    }

    @Test
    void aRingVerifiesOnlyAsARingForRschatOfItsNetwork() throws Exception {
        CallRingBean r = signedRing(List.of(id(DAVE)));
        assertThrows(CallProtocolException.class, () -> CallSignatures.verify(r, CallConstants.T_LOOKUP, AUD));
        assertThrows(CallProtocolException.class, () -> CallSignatures.verify(r, CallConstants.T_RING, "rschat:prod"));
        assertThrows(CallProtocolException.class, () -> CallSignatures.verify(r, CallConstants.T_RING, "svc:london-test-1"));
        r.setTo(List.of(id(OWNER))); // altered after signing
        assertThrows(CallProtocolException.class, () -> CallSignatures.verify(r, CallConstants.T_RING, AUD));
    }

    @Test
    void ringFieldChecksRefuseMalformedRings() throws Exception {
        assertRefused(signedRing(List.of()));
        assertRefused(signedRing(List.of(id(DAVE), id(DAVE))));
        assertRefused(signedRing(List.of("not-an-identity")));
        List<String> many = new ArrayList<>();
        SecureRandom rnd = new SecureRandom();
        for (int i = 0; i < CallRschat.RING_TO_MAX + 1; i++) {
            byte[] seed = new byte[32];
            rnd.nextBytes(seed);
            many.add(id(CallTestKeys.ed25519(seed)));
        }
        assertRefused(signedRing(many));
        CallRschat.checkRing(signedRing(many.subList(0, CallRschat.RING_TO_MAX)), CallRschat.RING_TO_MAX);

        CallRingBean badCall = CallRschat.ring("AB".repeat(32), "svc", List.of(id(DAVE)), "standard", NONCE, AUD, 1L);
        CallSignatures.sign(badCall, OWNER);
        assertRefused(badCall);
        CallRingBean badMode = CallRschat.ring(CALL, "svc", List.of(id(DAVE)), "video", NONCE, AUD, 1L);
        CallSignatures.sign(badMode, OWNER);
        assertRefused(badMode);
        CallRingBean badSvc = CallRschat.ring(CALL, "svc:x", List.of(id(DAVE)), "standard", NONCE, AUD, 1L);
        CallSignatures.sign(badSvc, OWNER);
        assertRefused(badSvc);
        CallRingBean noNonce = CallRschat.ring(CALL, "svc", List.of(id(DAVE)), "standard", null, AUD, 1L);
        CallSignatures.sign(noNonce, OWNER);
        assertRefused(noNonce);
    }

    static void assertRefused(CallRingBean r) {
        CallProtocolException ex = assertThrows(CallProtocolException.class, () -> CallRschat.checkRing(r, CallRschat.RING_TO_MAX));
        assertEquals(CallError.BAD_SIGNATURE, ex.getError());
    }

    @Test
    void lookupIsOneIdentitySignedByTheServiceKey() throws Exception {
        CallLookupBean l = CallRschat.lookup(id(DAVE), NONCE, AUD, 5L);
        CallSignatures.sign(l, SERVICE);
        CallLookupBean back = CallJson.parse(CallJson.toWire(l), CallLookupBean.class);
        CallSignatures.verify(back, CallConstants.T_LOOKUP, AUD, id(SERVICE));
        CallRschat.checkLookup(back);
        // a key outside the allow-list (here: the owner pretending to be a service) fails the pinned-signer check
        CallLookupBean forged = CallRschat.lookup(id(DAVE), NONCE, AUD, 5L);
        CallSignatures.sign(forged, OWNER);
        assertThrows(CallProtocolException.class, () -> CallSignatures.verify(forged, CallConstants.T_LOOKUP, AUD, id(SERVICE)));
        // a batch is not expressible: the strict parser refuses an extra field
        String batch = CallJson.toWire(l).replace("\"id\":", "\"ids\":[\"x\"],\"id\":");
        assertThrows(Exception.class, () -> CallJson.parse(batch, CallLookupBean.class));
        CallLookupBean bad = CallRschat.lookup("x", NONCE, AUD, 5L);
        CallSignatures.sign(bad, SERVICE);
        assertThrows(CallProtocolException.class, () -> CallRschat.checkLookup(bad));
    }

    @Test
    void replyCarriesTheRegistrationRecordVerbatimOrNotRegistered() throws Exception {
        String record = "{\"from\":\"x\",\"signature\":\"y\"}";
        CallReplyBean ok = CallReplyBean.ok();
        ok.setReg(record);
        CallReplyBean back = CallJson.parse(CallJson.toWire(ok), CallReplyBean.class);
        assertEquals(record, back.getReg());
        CallReplyBean no = CallReplyBean.refused(CallErrorBean.notRegistered(List.of(id(DAVE))));
        CallReplyBean noBack = CallJson.parse(CallJson.toWire(no), CallReplyBean.class);
        assertFalse(noBack.getOk());
        assertEquals("not_registered", noBack.getError().getErr());
        assertEquals(List.of(id(DAVE)), noBack.getError().getKeys());
        assertNull(noBack.getReg());
    }

    @Test
    void ringNoticeRidesTheNotificationAndOtherNotificationsAreUnchanged() throws Exception {
        ObjectMapper m = TkmTextUtils.getJacksonMapper();
        UserNotificationJsonBean chat = new UserNotificationJsonBean("h", "s", "r", 1L, NOTIFICATION_TYPES.NEW_MESSAGE.name(), "c", false);
        String chatJson = m.writeValueAsString(chat);
        assertFalse(chatJson.contains("\"ring\""), chatJson);
        assertTrue(CallRschat.ringOf(chat).isEmpty());

        CallRingNoticeBean notice = CallRschat.notice(signedRing(List.of(id(DAVE))));
        UserNotificationJsonBean ring = new UserNotificationJsonBean("h2", id(OWNER), id(DAVE), 2L,
                CallRschat.NOTIFICATION_TYPE, null, false, notice);
        String json = m.writeValueAsString(ring);
        @SuppressWarnings("unchecked")
        Map<String, Object> ringMap = (Map<String, Object>) m.readValue(json, Map.class).get("ring");
        assertEquals(Map.of("call", CALL, "svc", "london-test-1", "f", id(OWNER), "mode", "standard"), ringMap,
                "call, svc, f, mode and nothing else (spec 7.1)");
        UserNotificationJsonBean back = m.readValue(json, UserNotificationJsonBean.class);
        assertEquals(notice, CallRschat.ringOf(back).orElseThrow());
        // a CALL_RING without a well-formed ring is not a ring
        back.getRing().setCall("zz");
        assertTrue(CallRschat.ringOf(back).isEmpty());
        assertEquals("CALL_RING", CallRschat.NOTIFICATION_TYPE);
    }

    // ---- [0.2] §7.1 (C-16 answered elsewhere): the answered notice ----------------------------------------------------

    static CallRingBean signedAnswered(AsymmetricCipherKeyPair signer, List<String> to, String k) {
        CallRingBean r = CallRschat.answered(CALL, "london-test-1", CallConstants.MODE_STANDARD, id(signer), NONCE, AUD,
                1_700_000_000_000L);
        r.setTo(to);
        r.setK(k);
        CallSignatures.sign(r, signer);
        return r;
    }

    @Test
    void answeredNoticeIsARingSignedByTheCalleeToItself() throws Exception {
        CallRingBean a = CallRschat.answered(CALL, "london-test-1", CallConstants.MODE_STANDARD, id(DAVE), NONCE, AUD, 1L);
        assertEquals(List.of(id(DAVE)), a.getTo());
        assertEquals("answered", a.getK());
        CallSignatures.sign(a, DAVE);
        String wire = CallJson.toWire(a);
        assertTrue(wire.contains("\"k\":\"answered\""), wire);
        CallRingBean back = CallJson.parse(wire, CallRingBean.class);
        CallSignatures.verify(back, CallConstants.T_RING, AUD);
        CallRschat.checkRing(back, CallRschat.RING_TO_MAX);
        assertTrue(CallRschat.isAnswered(back));
        assertEquals(new CallRingNoticeBean(CALL, "london-test-1", id(DAVE), "standard"), CallRschat.notice(back));
        // a ring is unchanged: no "k" on the wire (NON_NULL), and it is not an answered notice
        CallRingBean ring = signedRing(List.of(id(DAVE)));
        assertFalse(CallJson.toWire(ring).contains("\"k\""));
        assertFalse(CallRschat.isAnswered(ring));
        assertFalse(CallRschat.isAnswered(null));
    }

    @Test
    void answeredNoticeFieldChecksAndTheSignatureCoverK() throws Exception {
        // k is constrained to "answered"
        assertRefused(signedAnswered(DAVE, List.of(id(DAVE)), "declined"));
        assertRefused(signedAnswered(DAVE, List.of(id(DAVE)), ""));
        // the answered notice goes to the signer's own identity, and only to it
        assertRefused(signedAnswered(DAVE, List.of(id(OWNER)), "answered"));
        assertRefused(signedAnswered(DAVE, List.of(id(DAVE), id(OWNER)), "answered"));
        assertRefused(signedAnswered(DAVE, List.of(id(OWNER), id(DAVE)), "answered"));
        // a ring turned into a notice (or back) after signing does not verify: k is in the signed octets
        CallRingBean ring = CallRschat.ring(CALL, "london-test-1", List.of(id(DAVE)), "standard", NONCE, AUD, 1L);
        CallSignatures.sign(ring, DAVE);
        ring.setK(CallConstants.RING_K_ANSWERED);
        CallRingBean tampered = CallJson.parse(CallJson.toWire(ring), CallRingBean.class);
        assertThrows(CallProtocolException.class, () -> CallSignatures.verify(tampered, CallConstants.T_RING, AUD));
        CallRingBean notice = signedAnswered(DAVE, List.of(id(DAVE)), "answered");
        notice.setK(null);
        CallRingBean stripped = CallJson.parse(CallJson.toWire(notice), CallRingBean.class);
        assertThrows(CallProtocolException.class, () -> CallSignatures.verify(stripped, CallConstants.T_RING, AUD));
        // the strict parser: k is a string. NOTE Jackson still turns a JSON number into a String field (the Dart
        // port refuses it, A-4): "k":1 parses as "1" here, which the field check refuses — never an answered notice.
        String wire = CallJson.toWire(signedAnswered(DAVE, List.of(id(DAVE)), "answered"));
        try {
            CallRingBean coerced = CallJson.parse(wire.replace("\"k\":\"answered\"", "\"k\":1"), CallRingBean.class);
            assertFalse(CallRschat.isAnswered(coerced));
            assertRefused(coerced);
        } catch (com.fasterxml.jackson.core.JsonProcessingException refusedByTheParser) {
            // as good
        }
        assertThrows(Exception.class, () -> CallJson.parse(wire.replace("\"k\":\"answered\"", "\"k\":[\"answered\"]"), CallRingBean.class));
        assertThrows(Exception.class, () -> CallJson.parse(wire.replace("\"k\":\"answered\"", "\"k\":\"answered\",\"k\":\"answered\""), CallRingBean.class));
    }

    /**
     * The fixed answered notice the Dart port reproduces byte for byte (rsclient-flutter
     * {@code test/call/call_answered_notice_test.dart}): signer = the vector identity "dave" (seed
     * {@code SHA3-256("tkm-call/v1/vectors/identity/dave")}), Ed25519 deterministic.
     */
    static final String ANSWERED_FIXED = "{\"aud\":\"rschat:test\",\"call\":\"abababababababababababababababababababababababababababababababab\","
            + "\"f\":\"amArqJTrlrVef0Lda_EvR4lUGVOxXPLuDAZodvQ4kDw.\",\"k\":\"answered\",\"mode\":\"standard\","
            + "\"n\":\"00000000000000000000000000000000000000000000000000000000000000aa\","
            + "\"sg\":\"bf99072fc068483764762905dd49861f76b4aedfc35f3b9c1cd6b348eb782ea9c390055eea8168c7bf223b778c5e1dcc556927d69ca13e5e2ed2f5edb02c5101\","
            + "\"svc\":\"london-test-1\",\"t\":\"ring\",\"to\":[\"amArqJTrlrVef0Lda_EvR4lUGVOxXPLuDAZodvQ4kDw.\"],\"ts\":1700000000000,\"v\":1}";

    @Test
    void answeredNoticeFixedBytesForTheDartPort() throws Exception {
        CallRingBean a = CallRschat.answered(CALL, "london-test-1", CallConstants.MODE_STANDARD, id(CallTestKeys.identity("dave")),
                NONCE, AUD, 1_700_000_000_000L);
        CallSignatures.sign(a, CallTestKeys.identity("dave"));
        assertEquals(ANSWERED_FIXED, CallJson.canonical(a));
        CallRingBean back = CallJson.parse(ANSWERED_FIXED, CallRingBean.class);
        CallSignatures.verify(back, CallConstants.T_RING, AUD);
        CallRschat.checkRing(back, CallRschat.RING_TO_MAX);
    }

    @Test
    void answeredNoticeRidesItsOwnNotificationType() throws Exception {
        ObjectMapper m = TkmTextUtils.getJacksonMapper();
        CallRingNoticeBean notice = CallRschat.notice(signedAnswered(DAVE, List.of(id(DAVE)), "answered"));
        UserNotificationJsonBean n = new UserNotificationJsonBean("h3", id(DAVE), id(DAVE), 3L,
                CallRschat.NOTIFICATION_TYPE_ANSWERED, null, false, notice);
        UserNotificationJsonBean back = m.readValue(m.writeValueAsString(n), UserNotificationJsonBean.class);
        assertEquals("CALL_ANSWERED", back.getNotificationType());
        assertEquals(notice, CallRschat.answeredOf(back).orElseThrow());
        // never read as a ring (a client that knows only CALL_RING must not ring for it), and a ring is not one
        assertTrue(CallRschat.ringOf(back).isEmpty());
        UserNotificationJsonBean ring = new UserNotificationJsonBean("h4", id(OWNER), id(DAVE), 4L,
                CallRschat.NOTIFICATION_TYPE, null, false, CallRschat.notice(signedRing(List.of(id(DAVE)))));
        assertTrue(CallRschat.answeredOf(ring).isEmpty());
        back.getRing().setCall("zz");
        assertTrue(CallRschat.answeredOf(back).isEmpty());
    }

    @Test
    void createConversationExistsMemberIsATypedSuccess() throws Exception {
        ObjectMapper m = TkmTextUtils.getJacksonMapper();
        CreateConversationRequestBean stored = new CreateConversationRequestBean();
        CreateConversationResponseBean fresh = new CreateConversationResponseBean("conv", stored);
        assertFalse(m.writeValueAsString(fresh).contains("\"result\""));
        assertFalse(fresh.isExistsMember());
        CreateConversationResponseBean exists = CreateConversationResponseBean.existsMember("conv", stored);
        String json = m.writeValueAsString(exists);
        assertTrue(json.contains("\"result\":\"exists_member\""), json);
        assertFalse(json.contains("existsMember"), json);
        CreateConversationResponseBean back = m.readValue(json, CreateConversationResponseBean.class);
        assertTrue(back.isExistsMember());
        assertEquals(CallError.EXISTS_MEMBER.code(), CreateConversationResponseBean.RESULT_EXISTS_MEMBER);
    }

    @Test
    void theThreeRschatRoutesAreDistinctFromTheServiceRoutes() {
        List<String> routes = List.of(ChatServerEndpoints.CALL_NONCE, ChatServerEndpoints.CALL_RING, ChatServerEndpoints.CALL_LOOKUP);
        assertEquals(List.of("callnonce", "callring", "calllookup"), routes);
        for (String r : routes) {
            assertFalse(r.startsWith("call."), r);
        }
    }
}
