package io.takamaka.messages.call;

import io.takamaka.messages.call.beans.CallAnnounceBean;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CallAnnouncementsTest {

    static CallVectorScenario s;

    @BeforeAll
    static void build() throws Exception {
        s = new CallVectorScenario();
    }

    Set<String> inv0() {
        return Set.copyOf(s.create.getInv());
    }

    @Test
    void validAnnouncementsPass() throws Exception {
        for (CallAnnounceBean a : new CallAnnounceBean[]{s.annO, s.annB, s.annC}) {
            assertEquals(CallHashes.annHashHex(a), CallAnnouncements.verify(a, CallVectorScenario.AUD_SVC, s.callId,
                    s.eraHash0, inv0(), Set.of(), Map.of(), s.kem));
        }
        CallAnnouncements.verify(s.annB1, CallVectorScenario.AUD_SVC, s.callId, s.eraHash1, Set.copyOf(s.era1.getInv()),
                Set.of(), Map.of(s.annB.getLeg(), s.annB.getF()), s.kem);
    }

    @Test
    void refusals() throws Exception {
        assertCode(() -> CallAnnouncements.verify(s.annB, CallVectorScenario.AUD_SVC, s.callId, s.eraHash1, inv0(), Set.of(), Map.of(), s.kem),
                CallError.ERA_MISMATCH);
        assertCode(() -> CallAnnouncements.verify(s.annB, CallVectorScenario.AUD_SVC, s.callId, s.eraHash0, Set.of(s.id("owner")), Set.of(), Map.of(), s.kem),
                CallError.NOT_INVITED);
        assertCode(() -> CallAnnouncements.verify(s.annB, CallVectorScenario.AUD_SVC, s.callId, s.eraHash0, inv0(), Set.of(),
                Map.of(s.annB.getLeg(), s.id("carol")), s.kem), CallError.BAD_SIGNATURE);
        assertCode(() -> CallAnnouncements.verify(s.annB, "svc:other", s.callId, s.eraHash0, inv0(), Set.of(), Map.of(), s.kem),
                CallError.BAD_SIGNATURE);
        // a validly signed announcement with an ek failing the FIPS 203 modulus check
        CallAnnounceBean bad = CallJson.parse(CallJson.toWire(s.annC), CallAnnounceBean.class);
        bad.setMlkem(CallVectorScenario.INVALID_EK_HEX);
        bad.setSg(null);
        CallSignatures.sign(bad, s.ids.get("carol"));
        CallProtocolException ex = assertThrows(CallProtocolException.class, () -> CallAnnouncements.verify(bad,
                CallVectorScenario.AUD_SVC, s.callId, s.eraHash0, inv0(), Set.of(), Map.of(), s.kem));
        assertEquals("ML-KEM encapsulation key check failed", ex.getReason());
    }

    interface Call {

        void run() throws Exception;
    }

    static void assertCode(Call c, CallError e) {
        CallProtocolException ex = assertThrows(CallProtocolException.class, c::run);
        assertEquals(e, ex.getError(), ex.getMessage());
    }
}
