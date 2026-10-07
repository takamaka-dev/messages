package io.takamaka.messages.call;

import io.takamaka.messages.call.beans.CallAnnounceBean;
import io.takamaka.messages.call.beans.CallChannelBean;
import io.takamaka.messages.call.channel.ChannelState;
import io.takamaka.messages.call.channel.HybridChannel;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HybridChannelTest {

    static CallVectorScenario s;

    @BeforeAll
    static void build() throws Exception {
        s = new CallVectorScenario();
    }

    @Test
    void bothEndsDeriveTheSameChains() {
        HybridChannel.Combined c = s.chOB.combined();
        // bob's receive chain is owner's A>B chain; both advanced twice since (leave and era commits)
        byte[] ab2 = HybridChannel.nextChain(HybridChannel.nextChain(c.chainAB()));
        byte[] ba2 = HybridChannel.nextChain(HybridChannel.nextChain(c.chainBA()));
        assertArrayEquals(ab2, s.accOB.state().recvChain());
        assertArrayEquals(ba2, s.accOB.state().sendChain());
        assertArrayEquals(ab2, s.chOB.state().sendChain());
        assertEquals(2, s.chOB.state().chainIndex());
        assertFalse(java.util.Arrays.equals(c.chainAB(), c.chainBA()));
        assertArrayEquals(CallBytes.unhex(CallVectorScenario.RFC7748_K), c.ssX(), "owner/bob use the RFC 7748 keys");
        assertEquals(CallBytes.hex(s.convSeed), s.accOB.body().getConvSeed());
        assertEquals("epoch", s.accOB.body().getH());
        assertEquals(s.annO.getF(), s.accOB.opener().getF());
    }

    @Test
    void chainRatchetAndMessageKeys() {
        byte[] c0 = s.chOB.combined().chainAB();
        byte[] c1 = HybridChannel.nextChain(c0);
        assertArrayEquals(CallCrypto.hkdf(c0, CallBytes.ascii("tkm-call/v1/chain")), c1);
        assertArrayEquals(CallCrypto.hkdf(c0, CallBytes.ascii("tkm-call/v1/msg")), HybridChannel.msgKey(c0));
        assertEquals("000000010000000000000002", CallBytes.hex(HybridChannel.nonce(1, 2)));
    }

    @Test
    void messagesFlowBothWaysAndAdvanceTogether() throws Exception {
        HybridChannel.OpenResult open = HybridChannel.openChannel(s.callId, s.annO, s.legs.get("owner").xPriv, s.annB,
                s.kem, CallTestKeys.seed("encaps/test"), null, null, s.ids.get("owner"), 1L);
        HybridChannel.AcceptResult acc = HybridChannel.acceptChannel(open.channel(),
                CallVectorScenario.rosterOf(s.annO, s.annB), s.annB, s.legs.get("bob").xPriv, s.legs.get("bob").kemKeys.dk(), s.kem);
        assertEquals("{}", new String(acc.bodyBytes(), java.nio.charset.StandardCharsets.US_ASCII));
        ChannelState a = open.state(), b = acc.state();
        byte[] aad = CallBytes.ascii("{\"x\":1}");
        assertArrayEquals(CallBytes.ascii("ack"), a.open(aad, b.seal(aad, CallBytes.ascii("ack"))));
        assertArrayEquals(CallBytes.ascii("m1"), b.open(aad, a.seal(aad, CallBytes.ascii("m1"))));
        byte[] old = a.seal(aad, CallBytes.ascii("old"));
        a.advance();
        b.advance();
        assertEquals(1, a.chainIndex());
        assertThrows(CallCrypto.AeadException.class, () -> b.open(aad, old), "k=0 box after advance");
        byte[] m2 = a.seal(aad, CallBytes.ascii("m2"));
        assertArrayEquals(CallBytes.ascii("m2"), b.open(aad, m2));
        assertThrows(CallCrypto.AeadException.class, () -> b.open(aad, m2), "replay");
        assertThrows(CallCrypto.AeadException.class, () -> b.open(CallBytes.ascii("{}"), a.seal(aad, CallBytes.ascii("x"))), "aad");
    }

    @Test
    void recipientRefusals() throws Exception {
        Map<String, CallAnnounceBean> roster = CallVectorScenario.rosterOf(s.annO, s.annB, s.annC);
        CallVectorScenario.Leg b = s.legs.get("bob"), c = s.legs.get("carol");
        CallChannelBean ch = s.chOB.channel();
        // carol is not the addressee
        CallProtocolException e1 = assertThrows(CallProtocolException.class,
                () -> HybridChannel.acceptChannel(copy(ch), roster, s.annC, c.xPriv, c.kemKeys.dk(), s.kem));
        assertEquals(CallError.BAD_SIGNATURE, e1.getError());
        // opener not in bob's verified roster
        CallProtocolException e2 = assertThrows(CallProtocolException.class,
                () -> HybridChannel.acceptChannel(copy(ch), CallVectorScenario.rosterOf(s.annB), s.annB, b.xPriv, b.kemKeys.dk(), s.kem));
        assertEquals(CallError.KEY_REFUSED, e2.getError());
        // tampered box: signature catches it first
        CallChannelBean t = copy(ch);
        t.setBox(t.getBox().substring(0, t.getBox().length() - 2) + "00");
        assertThrows(CallProtocolException.class, () -> HybridChannel.acceptChannel(t, roster, s.annB, b.xPriv, b.kemKeys.dk(), s.kem));
        // a channel re-signed by carol claiming the owner's announcement: identity mismatch
        CallChannelBean r = copy(ch);
        r.setF(null);
        r.setSg(null);
        CallSignatures.sign(r, s.ids.get("carol"));
        CallProtocolException e3 = assertThrows(CallProtocolException.class,
                () -> HybridChannel.acceptChannel(r, roster, s.annB, b.xPriv, b.kemKeys.dk(), s.kem));
        assertEquals("opener identity or leg mismatch", e3.getReason());
        // wrong ML-KEM private key: the box does not open
        CallProtocolException e4 = assertThrows(CallProtocolException.class,
                () -> HybridChannel.acceptChannel(copy(ch), roster, s.annB, b.xPriv, c.kemKeys.dk(), s.kem));
        assertEquals("box does not open", e4.getReason());
    }

    private static CallChannelBean copy(CallChannelBean c) throws Exception {
        return CallJson.parse(CallJson.toWire(c), CallChannelBean.class);
    }
}
