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
