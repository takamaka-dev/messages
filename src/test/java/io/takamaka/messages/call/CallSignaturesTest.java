package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.takamaka.messages.call.beans.*;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.messages.utils.SimpleRequestHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CallSignaturesTest {

    static CallVectorScenario s;

    @BeforeAll
    static void build() throws Exception {
        s = new CallVectorScenario();
    }

    @Test
    void everyTypeSignsAndVerifiesAndSurvivesTheWire() throws Exception {
        assertEquals(14, s.byType.size(), "13 types of draft 0.1 + records [0.2]");
        for (Map.Entry<String, CallSignedObject> e : s.byType.entrySet()) {
            CallSignedObject o = e.getValue();
            assertEquals(e.getKey(), o.getT());
            CallSignatures.verify(o, o.getT(), o.getAud());
            CallSignedObject parsed = CallJson.parse(CallJson.toWire(o));
            assertEquals(o.getClass(), parsed.getClass(), e.getKey());
            assertEquals(o, parsed, e.getKey());
            CallSignatures.verify(parsed, o.getT(), o.getAud(), o.getF());
            assertTrue(CallBytes.isLowerHex(o.getSg(), 64));
        }
    }

    @Test
    void signingMessageLayout() {
        String canonical = CallSignatures.canonical(s.goodbyeC);
        assertFalse(canonical.contains("\"sg\""));
        byte[] octets = CallSignatures.signingOctets(s.goodbyeC);
        byte[] expected = CallBytes.concat(CallBytes.ascii("tkm-call/v1/goodbye"), new byte[]{0}, CallBytes.ascii(canonical));
        assertArrayEquals(expected, octets);
        assertEquals('{', canonical.charAt(0));
    }

    @Test
    void canonicalSortsKeysAndOmitsNullsButKeepsEmptyLists() {
        CallCreateBean c = new CallCreateBean();
        c.setTs(5L);
        c.setMods(List.of());
        c.setAud("svc:x");
        String canon = CallSignatures.canonical(c);
        assertEquals("{\"aud\":\"svc:x\",\"mods\":[],\"t\":\"create\",\"ts\":5,\"v\":1}", canon);
    }

    @Test
    void walletKeystorePathEqualsRawKeyPath() throws Exception {
        AsymmetricCipherKeyPair kp = s.ids.get("dave");
        CallGoodbyeBean a = new CallGoodbyeBean();
        a.setAud("svc:x");
        a.setTs(1L);
        a.setCall("00");
        a.setLeg("01");
        a.setEpoch(0L);
        CallGoodbyeBean b = CallJson.parse(CallJson.toWire(a), CallGoodbyeBean.class);
        CallSignatures.sign(a, kp);
        CallSignatures.sign(b, new CallTestKeys.FixedKeystore("dave", kp), 0);
        assertEquals(a.getSg(), b.getSg());
        assertEquals(a.getF(), b.getF());
    }

    @Test
    void wrongTypeAudienceSignerAndTamperFail() throws Exception {
        CallAnnounceBean a = CallJson.parse(CallJson.toWire(s.annB), CallAnnounceBean.class);
        assertErr(() -> CallSignatures.verify(a, "goodbye", a.getAud()), "type context");
        assertErr(() -> CallSignatures.verify(a, "announce", "svc:other"), "audience");
        assertErr(() -> CallSignatures.verify(a, "announce", a.getAud(), s.id("owner")), "unexpected signer");
        a.setCap(CallConstants.CAP_BASIC);
        assertErr(() -> CallSignatures.verify(a, "announce", a.getAud()), "signature");
        CallAnnounceBean b = CallJson.parse(CallJson.toWire(s.annB), CallAnnounceBean.class);
        b.setF(s.id("carol")); // claim another signer
        assertErr(() -> CallSignatures.verify(b, "announce", b.getAud()), "signature");
        CallAnnounceBean c = CallJson.parse(CallJson.toWire(s.annB), CallAnnounceBean.class);
        c.setSg(c.getSg().toUpperCase());
        assertErr(() -> CallSignatures.verify(c, "announce", c.getAud()), "signature encoding");
        CallAnnounceBean d = CallJson.parse(CallJson.toWire(s.annB), CallAnnounceBean.class);
        d.setV(2);
        assertErr(() -> CallSignatures.verify(d, "announce", d.getAud()), "version");
    }

    @Test
    void typeConfusionMuteVersusUnmute() throws Exception {
        CallMuteBean m = CallJson.parse(CallJson.toWire(s.mute), CallMuteBean.class);
        m.setT(CallConstants.T_UNMUTE);
        assertErr(() -> CallSignatures.verify(m, "unmute", m.getAud()), "signature");
    }

    @Test
    void nonAsciiRefusedOnSignAndVerify() throws Exception {
        CallDeclineBean d = new CallDeclineBean();
        d.setAud("svc:x");
        d.setReason("busyé");
        assertThrows(IllegalArgumentException.class, () -> CallSignatures.sign(d, s.ids.get("dave")));
        CallDeclineBean v = CallJson.parse(CallJson.toWire(s.declineD), CallDeclineBean.class);
        v.setReason("busу"); // Cyrillic
        assertErr(() -> CallSignatures.verify(v, "decline", v.getAud()), "non-ASCII");
        // escaped on the wire is the same thing once parsed
        String wire = CallJson.toWire(s.declineD).replace("\"busy\"", "\"busy\\u00e9\"");
        CallDeclineBean w = CallJson.parse(wire, CallDeclineBean.class);
        assertErr(() -> CallSignatures.verify(w, "decline", w.getAud()), "non-ASCII");
    }

    @Test
    void reorderedArraysFailReorderedKeysDoNot() throws Exception {
        CallCreateBean c = CallJson.parse(CallJson.toWire(s.create), CallCreateBean.class);
        List<String> inv = new ArrayList<>(c.getInv());
        Collections.reverse(inv);
        c.setInv(inv);
        assertErr(() -> CallSignatures.verify(c, "create", c.getAud()), "signature");
        // keys in any order on the wire parse to the same bean: valid (JCS)
        String wire = CallJson.canonical(s.create);
        assertTrue(wire.startsWith("{\"aud\""));
        CallCreateBean again = CallJson.parse(wire, CallCreateBean.class);
        CallSignatures.verify(again, "create", again.getAud());
    }

    @Test
    void chatSignatureIsNotACallSignature() throws Exception {
        CallAnnounceBean a = CallJson.parse(CallJson.toWire(s.annB), CallAnnounceBean.class);
        String chatSig = SimpleRequestHelper.signChatMessage(CallSignatures.canonical(a),
                new CallTestKeys.FixedKeystore("bob", s.ids.get("bob")), 0);
        a.setSg(CallBytes.hex(CallSignatures.urlUnBase64(chatSig)));
        assertErr(() -> CallSignatures.verify(a, "announce", a.getAud()), "signature");
        // and the chat signature IS valid in the chat system over the same bytes (control)
        assertTrue(CallVectorScenario.chatVerify(a.getF(), a.getSg(), CallSignatures.canonical(a)));
    }

    @Test
    void callSignatureIsNotAChatSignature() {
        CallAnnounceBean a = s.annB;
        assertFalse(CallVectorScenario.chatVerify(a.getF(), a.getSg(), CallSignatures.canonical(a)));
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(CallJson.toWire(a)));
    }

    @Test
    void strictParser() {
        String ok = CallJson.canonical(s.goodbyeC);
        assertDoesNotThrow(() -> CallJson.parse(ok));
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(ok.replace("{\"aud\"", "{\"x\":1,\"aud\"")));
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(ok.replace("{\"aud\"", "{\"v\":1,\"aud\"")));
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(ok.replace("\"epoch\":2", "\"epoch\":2.0")));
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(ok.replace("\"epoch\":2", "\"epoch\":\"2\"")));
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(ok.replace("\"t\":\"goodbye\"", "\"t\":\"nope\"")));
    }

    interface Call {

        void run() throws Exception;
    }

    static void assertErr(Call c, String reason) {
        CallProtocolException ex = assertThrows(CallProtocolException.class, c::run);
        assertEquals(CallError.BAD_SIGNATURE, ex.getError());
        assertEquals(reason, ex.getReason());
    }
}
