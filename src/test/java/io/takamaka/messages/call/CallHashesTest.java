package io.takamaka.messages.call;

import io.takamaka.messages.call.beans.CallCreateBean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CallHashesTest {

    static CallVectorScenario s;

    @BeforeAll
    static void build() throws Exception {
        s = new CallVectorScenario();
    }

    @Test
    void hashesFollowTheirLabels() {
        String canon = CallSignatures.canonical(s.create);
        assertEquals(CallBytes.hex(CallCrypto.h(CallBytes.ascii("tkm-call/v1/id" + canon))), s.callId);
        assertEquals(CallBytes.hex(CallCrypto.h(CallBytes.ascii("tkm-call/v1/era" + canon))), s.eraHash0);
        assertNotEquals(s.callId, s.eraHash0);
        assertEquals(CallBytes.hex(CallCrypto.h(CallBytes.ascii("tkm-call/v1/era" + CallSignatures.canonical(s.era1)))), s.eraHash1);
        assertEquals(CallBytes.hex(CallCrypto.h(CallBytes.ascii("tkm-call/v1/ann" + CallSignatures.canonical(s.annB)))),
                CallHashes.annHashHex(s.annB));
    }

    @Test
    void hashesIgnoreSgAndBindEverythingElse() throws Exception {
        CallCreateBean c = CallJson.parse(CallJson.toWire(s.create), CallCreateBean.class);
        c.setSg("00".repeat(64));
        assertEquals(s.callId, CallHashes.callIdHex(c));
        c.setRand("00".repeat(32));
        assertNotEquals(s.callId, CallHashes.callIdHex(c));
    }

    @Test
    void reAnnouncementChangesAnnHashButKeepsKeys() {
        assertEquals(s.annB.getLeg(), s.annB1.getLeg());
        assertEquals(s.annB.getX25519(), s.annB1.getX25519());
        assertEquals(s.annB.getMlkem(), s.annB1.getMlkem());
        assertNotEquals(CallHashes.annHashHex(s.annB), CallHashes.annHashHex(s.annB1));
        assertEquals(s.eraHash1, s.annB1.getEra());
    }

    @Test
    void createRecordRules() {
        assertTrue(s.create.getInv().contains(s.create.getF()));
        assertEquals(new java.util.ArrayList<>(new java.util.TreeSet<>(s.create.getInv())), s.create.getInv());
        assertTrue(s.create.getExp() - s.create.getTs() <= CallConstants.CREATE_MAX_LIFETIME_MS);
        assertEquals(s.eraHash0, s.era1.getPrev());
    }
}
