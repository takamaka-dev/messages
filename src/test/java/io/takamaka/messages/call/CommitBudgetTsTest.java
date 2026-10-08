package io.takamaka.messages.call;

import io.takamaka.messages.call.beans.CallChannelBodyBean;
import io.takamaka.messages.call.beans.CallCommitBean;
import io.takamaka.messages.call.epoch.Commits;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [0.2] §6.2 budget on SIGNED timestamps (X-1/X-2, RULED 2026-10-08): the window is {@code ts_new − ts_prev_fresh},
 * never the receiver's arrival clock; a {@code ts} more than {@code ts_tolerance} ahead of the receiver's wall clock is
 * refused for every kind; era/restart/initial are exempt from the window.
 */
class CommitBudgetTsTest {

    static final long REFUSE_BELOW = 5_400;
    static final long PREV = 1_800_000_000_000L;
    static CallVectorScenario s;

    @BeforeAll
    static void build() throws Exception {
        s = new CallVectorScenario();
    }

    private static CallCommitBean commit(String kind, Long ts) {
        CallCommitBean c = new CallCommitBean();
        c.setKind(kind);
        c.setTs(ts);
        return c;
    }

    private static String refusal(CallCommitBean c, Long prev, long nowWall) {
        try {
            Commits.checkBudget(c, prev, nowWall, REFUSE_BELOW);
            return null;
        } catch (CallProtocolException ex) {
            assertEquals(CallError.KEY_REFUSED, ex.getError());
            return ex.getReason();
        }
    }

    @Test
    void theWindowIsOnSignedTsNotOnTheWallClockOfArrival() {
        for (String k : new String[]{"leave", "backstop", "rotation"}) {
            // exactly refuse_below after the previous fresh commit's ts: accepted ("less than" refuses)
            assertNull(refusal(commit(k, PREV + REFUSE_BELOW), PREV, PREV + REFUSE_BELOW), k);
            // 1 ms short: refused, with the measured gap in the reason
            String r = refusal(commit(k, PREV + REFUSE_BELOW - 1), PREV, PREV + REFUSE_BELOW);
            assertNotNull(r, k);
            assertTrue(r.startsWith("budget: " + k + " commit 5399 ms"), r);
            // X-2: the receiver's clock says 1.8 s since it APPLIED the previous one (that one arrived 4.8 s late) —
            // irrelevant: the signed timestamps are 6.6 s apart
            assertNull(refusal(commit(k, PREV + 6_600), PREV, PREV + 6_600 + 50), k);
            // X-1: a leg resumed after a freeze sees both commits at once, its wall clock far past both — still accepted
            assertNull(refusal(commit(k, PREV + 6_600), PREV, PREV + 45_000), k);
        }
    }

    @Test
    void exemptKindsAreNeverBudgetRefused() {
        for (String k : new String[]{"era", "restart"}) {
            assertNull(refusal(commit(k, PREV + 1), PREV, PREV + 1), k);
            assertNull(refusal(commit(k, PREV - 10_000), PREV, PREV), k + ": even a ts before the previous one");
        }
    }

    @Test
    void noPreviousFreshCommitNeverRefuses() {
        assertNull(refusal(commit("leave", PREV), null, PREV));
    }

    @Test
    void aTsAheadOfTheWallClockBeyondTheToleranceIsRefusedForEveryKind() {
        assertEquals(60_000L, CallConstants.TS_TOLERANCE_MS, "the §8.1 tolerance (nonce validity)");
        long now = PREV + 10_000;
        for (String k : new String[]{"leave", "backstop", "rotation", "era", "restart"}) {
            assertNull(refusal(commit(k, now + CallConstants.TS_TOLERANCE_MS), PREV - 100_000, now), k + " at the tolerance");
            String r = refusal(commit(k, now + CallConstants.TS_TOLERANCE_MS + 1), PREV - 100_000, now);
            assertNotNull(r, k);
            assertTrue(r.contains("ahead of this wall clock"), r);
        }
        assertEquals("ts missing", refusal(commit("era", null), PREV, now));
    }

    @Test
    void acceptRunsTheBudgetAfterTheSignatureAndBeforeTheBox() {
        CallCommitBean leave = s.cLeave.commit(); // a genuine, signed leave (ts = T0 + 70 000)
        long ts = leave.getTs();
        Set<String> verified = Set.of(CallHashes.annHashHex(s.annO), CallHashes.annHashHex(s.annB));
        CallProtocolException ex = assertThrows(CallProtocolException.class, () -> Commits.accept(leave, CallVectorScenario.AUD_SVC,
                Set.of(leave.getF()), leave.getEra(), leave.getEpoch() - 1, s.es2, verified, CallHashes.annHashHex(s.annB),
                "00", null, new Commits.Budget(ts - 1_000, ts, REFUSE_BELOW)));
        assertTrue(ex.getReason().startsWith("budget: leave commit 1000 ms"), ex.getReason());
        // a forged copy fails on its signature first, whatever its ts
        leave.setTs(ts + 1);
        CallProtocolException bad = assertThrows(CallProtocolException.class, () -> Commits.accept(leave, CallVectorScenario.AUD_SVC,
                Set.of(leave.getF()), leave.getEra(), leave.getEpoch() - 1, s.es2, verified, CallHashes.annHashHex(s.annB),
                "00", null, new Commits.Budget(ts - 1_000, ts, REFUSE_BELOW)));
        assertEquals(CallError.BAD_SIGNATURE, bad.getError());
        leave.setTs(ts);
    }

    @Test
    void theCommitterAnchorAbsorbsAFutureTs() {
        assertEquals(1_000L, Commits.committerAnchor(1_000, PREV, PREV - 5));
        assertEquals(1_000L + 3_000, Commits.committerAnchor(1_000, PREV, PREV + 3_000));
    }

    @Test
    void theCatchUpRequestBody() throws Exception {
        CallChannelBodyBean b = Commits.catchUpRequest(17);
        assertEquals("{\"epoch\":17,\"h\":\"catchup\"}", CallJson.canonical(b));
    }
}
