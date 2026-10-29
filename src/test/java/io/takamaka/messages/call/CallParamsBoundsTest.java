package io.takamaka.messages.call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.takamaka.messages.call.beans.CallParamsBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** [0.3] S-4: the ruled bounds pass every real profile and refuse each hostile value. */
@DisplayName("S-4: served-params bounds")
class CallParamsBoundsTest {

    static CallParamsBean dflt() {
        CallParamsBean p = CallParamsBean.initialValues();
        p.setRotationMax(300_000L);
        return p;
    }

    static CallParamsBean loop(long rotationMax, long backstop) {
        CallParamsBean p = new CallParamsBean(6_000L, 6_000L, 6_600L, 5_400L, 500L, 2_000L, 10_000L, backstop, 50, 3);
        p.setRotationMax(rotationMax);
        return p;
    }

    @Test
    @DisplayName("the default (London TEST), loop and loopadaptive profiles pass; the 10-field initial values too")
    void realProfilesPass() {
        assertNull(CallParamsBounds.violation(dflt()));
        assertNull(CallParamsBounds.violation(CallParamsBean.initialValues()), "no rotation_max = optional");
        assertNull(CallParamsBounds.violation(loop(6_000L, 30_000L)), "loop");
        assertNull(CallParamsBounds.violation(loop(60_000L, 60_000L)), "loopadaptive");
        CallParamsBean adaptive = dflt();
        adaptive.setRotation(0L);
        assertNull(CallParamsBounds.violation(adaptive), "rotation 0 = adaptive");
    }

    @Test
    @DisplayName("each hostile value is refused with its rule named; check() throws bad_params")
    void hostileValuesRefused() {
        CallParamsBean p = dflt();
        p.setCommitDelay(3L * 3_600_000L); // hours → leavers keep the keys
        assertTrue(CallParamsBounds.violation(p).startsWith("commit_delay"));
        p = dflt(); p.setRotation(0L); p.setBackstop(3_600_000L);
        assertTrue(CallParamsBounds.violation(p).startsWith("backstop"));
        p = dflt(); p.setRefuseBelow(0L);
        assertTrue(CallParamsBounds.violation(p).startsWith("refuse_below"), "R11 budget removed");
        p = dflt(); p.setRefuseBelow(66_001L);
        assertTrue(CallParamsBounds.violation(p).startsWith("refuse_below"));
        p = dflt(); p.setRetention(600_000L);
        assertTrue(CallParamsBounds.violation(p).startsWith("retention"));
        p = dflt(); p.setRotationMax(400_000L);
        assertTrue(CallParamsBounds.violation(p).startsWith("rotation_max"), "rotation_max above backstop");
        p = dflt(); p.setRotation(500L);
        assertTrue(CallParamsBounds.violation(p).startsWith("rotation"));
        p = dflt(); p.setRotation(90_000L); // > commit_delay 66 s
        assertTrue(CallParamsBounds.violation(p).startsWith("commit_delay"));
        p = dflt(); p.setCommitterTimeout(100L);
        assertTrue(CallParamsBounds.violation(p).startsWith("committer_timeout"));
        p = dflt(); p.setGrace(0L);
        assertTrue(CallParamsBounds.violation(p).startsWith("grace"));
        p = dflt(); p.setAbsenceWindow(61_000L);
        assertTrue(CallParamsBounds.violation(p).startsWith("absence_window"));
        p = dflt(); p.setCap(1000);
        assertTrue(CallParamsBounds.violation(p).startsWith("cap"));
        p = dflt(); p.setDevicesPerIdentity(0);
        assertTrue(CallParamsBounds.violation(p).startsWith("devices_per_identity"));
        p = dflt(); p.setGrace(null);
        assertNotNull(CallParamsBounds.violation(p), "a missing field is a violation");
        assertNotNull(CallParamsBounds.violation(null));
        CallParamsBean bad = dflt(); bad.setBackstop(10L * 60_000L);
        CallProtocolException e = assertThrows(CallProtocolException.class, () -> CallParamsBounds.check(bad, "manifest"));
        assertEquals(CallError.BAD_PARAMS, e.getError());
        assertTrue(e.getMessage().contains("manifest: backstop"), e.getMessage());
    }
}
