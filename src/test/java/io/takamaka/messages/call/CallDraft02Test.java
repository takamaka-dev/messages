package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.takamaka.messages.call.beans.CallChannelBodyBean;
import io.takamaka.messages.call.beans.CallCreateBean;
import io.takamaka.messages.call.beans.CallEraBean;
import io.takamaka.messages.call.beans.CallManifestBean;
import io.takamaka.messages.call.beans.CallRecordsBean;
import io.takamaka.messages.call.beans.CallSignedObject;
import io.takamaka.messages.call.epoch.Commits;
import io.takamaka.messages.call.service.CallEventBean;
import io.takamaka.messages.call.service.CallReplyBean;
import io.takamaka.messages.call.service.CallTextBean;
import io.takamaka.messages.call.service.CallTextRequestBean;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The draft-0.2 rulings of C182 service build 1 on the reference side: the {@code records} type and its client check
 * (§7.3 step 0, O-1), the handover {@code since} (§6.4, O-11), {@code net} in the manifest and the
 * {@code wrong_service} result (§8.6/§11 amendment), the §10.2 text wrappers. (The nonce of §8.1 is in
 * {@link ServiceAndConvTest}.)
 */
class CallDraft02Test {

    static CallVectorScenario s;

    @BeforeAll
    static void build() throws Exception {
        s = new CallVectorScenario();
    }

    @Test
    void recordsRequestIsATypedSignedObjectWithANonce() throws Exception {
        CallRecordsBean r = s.recordsD;
        assertEquals("records", r.getT());
        assertEquals(CallVectorScenario.AUD_SVC, r.getAud());
        assertEquals(s.callId, r.getCall());
        assertNotNull(r.getN());
        CallSignedObject parsed = CallJson.parse(CallJson.toWire(r));
        assertInstanceOf(CallRecordsBean.class, parsed);
        CallSignatures.verify(parsed, CallConstants.T_RECORDS, CallVectorScenario.AUD_SVC, s.id("dave"));
        // type confusion: a records object is not an announce, and vice versa
        assertThrows(CallProtocolException.class, () -> CallSignatures.verify(parsed, CallConstants.T_ANNOUNCE,
                CallVectorScenario.AUD_SVC));
    }

    @Test
    void recordsAnswerVerifiesAndYieldsTheCurrentEra() throws Exception {
        CallReplyBean reply = CallReplyBean.ok();
        reply.setCreate(s.create);
        reply.setEras(List.of(s.era1));
        CallReplyBean back = CallJson.parse(CallJson.toWire(reply), CallReplyBean.class);
        assertEquals(s.eraHash1, CallRecords.verifyChain(back.getCreate(), back.getEras(), CallVectorScenario.AUD_SVC,
                CallVectorScenario.SVC, s.callId));
        assertEquals(s.eraHash0, CallRecords.verifyChain(s.create, List.of(), CallVectorScenario.AUD_SVC,
                CallVectorScenario.SVC, s.callId), "era 0: the creation record's own hash");

        // another call, another service, a tampered record, a broken chain
        assertThrows(CallProtocolException.class, () -> CallRecords.verifyChain(s.create, List.of(),
                CallVectorScenario.AUD_SVC, CallVectorScenario.SVC, CallBytes.hex(new byte[32])));
        assertThrows(CallProtocolException.class, () -> CallRecords.verifyChain(s.create, List.of(),
                CallVectorScenario.AUD_SVC, "other-svc", s.callId));
        CallCreateBean tampered = CallJson.parse(CallJson.toWire(s.create), CallCreateBean.class);
        tampered.setExp(tampered.getExp() - 1);
        assertThrows(CallProtocolException.class, () -> CallRecords.verifyChain(tampered, List.of(),
                CallVectorScenario.AUD_SVC, CallVectorScenario.SVC, s.callId));
        CallEraBean wrongPrev = CallJson.parse(CallJson.toWire(s.era1), CallEraBean.class);
        wrongPrev.setPrev(CallBytes.hex(new byte[32]));
        wrongPrev.setSg(null);
        CallSignatures.sign(wrongPrev, s.ids.get("owner"));
        CallProtocolException ex = assertThrows(CallProtocolException.class, () -> CallRecords.verifyChain(s.create,
                List.of(wrongPrev), CallVectorScenario.AUD_SVC, CallVectorScenario.SVC, s.callId));
        assertEquals(CallError.ERA_CONFLICT, ex.getError());
        CallEraBean byBob = CallJson.parse(CallJson.toWire(s.era1), CallEraBean.class);
        byBob.setSg(null);
        byBob.setF(s.id("bob"));
        CallSignatures.sign(byBob, s.ids.get("bob"));
        assertThrows(CallProtocolException.class, () -> CallRecords.verifyChain(s.create, List.of(byBob),
                CallVectorScenario.AUD_SVC, CallVectorScenario.SVC, s.callId), "an era not signed by the owner");
    }

    @Test
    void handoverCarriesSinceAndTheNewcomerMeasuresItsWindowFromIt() throws Exception {
        CallChannelBodyBean h = s.accOB.body();
        assertEquals(4_000L, h.getSince(), "O handed over 4 s after the initial commit");
        String hO = CallHashes.annHashHex(s.annO), hB = CallHashes.annHashHex(s.annB);
        Commits.acceptEpochHandover(h, s.eraHash0, Set.of(hO, hB), hB);
        assertEquals(96_000L, Commits.budgetWindowStart(100_000L, h), "window start = now - since");

        CallChannelBodyBean noSince = CallJson.parse(CallJson.toWire(h), CallChannelBodyBean.class);
        noSince.setSince(null);
        assertThrows(CallProtocolException.class, () -> Commits.acceptEpochHandover(noSince, s.eraHash0, Set.of(hO, hB), hB),
                "[0.2] a handover without since is refused");
        CallChannelBodyBean negative = CallJson.parse(CallJson.toWire(h), CallChannelBodyBean.class);
        negative.setSince(-1L);
        assertThrows(CallProtocolException.class, () -> Commits.acceptEpochHandover(negative, s.eraHash0, Set.of(hO, hB), hB));
        assertThrows(IllegalArgumentException.class, () -> Commits.epochHandover(1, s.eraHash0, List.of(hO, hB),
                new byte[32], 0, -5, CallVectorScenario.T0));
    }

    /**
     * [0.2] §6.4 {@code prev_ts} (RULED 2026-10-08): the handover carries the signed ts of the fresh commit that produced
     * the secret; the adopting leg's budget window is that ts, whatever the delay; a handover without it is refused
     * like one without {@code since} (B1-6); the committer anchor is the later of {@code arrival − since} and the instant
     * this wall clock reads {@code prev_ts}.
     */
    @Test
    void handoverCarriesPrevTsAndTheAdoptedWindowIsTheCommittersSignedTs() throws Exception {
        CallChannelBodyBean h = s.accOB.body();
        assertEquals(CallVectorScenario.T0 + 2_000, h.getPrevTs(), "the initial commit's signed ts");
        assertEquals(CallVectorScenario.T0 + 2_000, s.accOC.body().getPrevTs());
        assertEquals(CallVectorScenario.T0 + 2_000, s.cInitial.commit().getTs());
        assertTrue(CallJson.toWire(h).contains("\"prev_ts\":" + (CallVectorScenario.T0 + 2_000)), "wire name prev_ts");
        String hO = CallHashes.annHashHex(s.annO), hB = CallHashes.annHashHex(s.annB);
        Commits.acceptEpochHandover(h, s.eraHash0, Set.of(hO, hB), hB);
        // delivered 3 s late: arrival − since would be T0 + 5 000; the window is the committer's ts
        assertEquals(CallVectorScenario.T0 + 2_000, Commits.adoptedPrevFreshTs(h));
        long arrivalWall = CallVectorScenario.T0 + 6_000 + 3_000;
        // anchor: zero skew → arrival − since (the later bound); receiver clock 10 s behind → the prev_ts instant
        assertEquals(500_000L - 4_000, Commits.handoverAnchor(500_000L, arrivalWall, h));
        assertEquals(500_000L + 2_000 - 9_000 + 10_000, Commits.handoverAnchor(500_000L, arrivalWall - 10_000, h));

        CallChannelBodyBean noPrev = CallJson.parse(CallJson.toWire(h), CallChannelBodyBean.class);
        noPrev.setPrevTs(null);
        CallProtocolException ex = assertThrows(CallProtocolException.class,
                () -> Commits.acceptEpochHandover(noPrev, s.eraHash0, Set.of(hO, hB), hB),
                "[0.2] a handover without prev_ts is refused");
        assertEquals(CallError.KEY_REFUSED, ex.getError());
        CallChannelBodyBean negative = CallJson.parse(CallJson.toWire(h), CallChannelBodyBean.class);
        negative.setPrevTs(-1L);
        assertThrows(CallProtocolException.class, () -> Commits.acceptEpochHandover(negative, s.eraHash0, Set.of(hO, hB), hB));
        CallChannelBodyBean unsafe = CallJson.parse(CallJson.toWire(h), CallChannelBodyBean.class);
        unsafe.setPrevTs(1L << 53);
        assertThrows(CallProtocolException.class, () -> Commits.acceptEpochHandover(unsafe, s.eraHash0, Set.of(hO, hB), hB));
        assertThrows(IllegalArgumentException.class, () -> Commits.epochHandover(1, s.eraHash0, List.of(hO, hB),
                new byte[32], 0, 0, -1));
    }

    @Test
    void manifestCarriesTheSignedNetworkAndWrongServiceIsATypedCode() throws Exception {
        CallManifestBean m = s.manifest;
        assertEquals(CallVectorScenario.NET, m.getNet());
        CallManifestBean parsed = CallJson.parse(CallJson.toWire(m), CallManifestBean.class);
        CallSignatures.verify(parsed, CallConstants.T_MANIFEST, CallConstants.AUD_ANY, s.id("service"));
        parsed.setNet("prod");
        assertThrows(CallProtocolException.class, () -> CallSignatures.verify(parsed, CallConstants.T_MANIFEST,
                CallConstants.AUD_ANY, s.id("service")), "net is covered by the service signature");
        assertEquals("wrong_service", CallError.WRONG_SERVICE.code());
        assertEquals("{\"error\":{\"err\":\"wrong_service\"},\"ok\":false}",
                CallJson.canonical(CallReplyBean.refused(CallError.WRONG_SERVICE)));
    }

    @Test
    void textWrappersRoundTripAndAreStrict() throws Exception {
        CallTextBean t = new CallTextBean(CallTextBean.X_TEXT, "00112233aabbccdd", 4L, 7L, "abcd");
        CallTextRequestBean req = new CallTextRequestBean(s.callId, t);
        assertEquals(req, CallJson.parse(CallJson.toWire(req), CallTextRequestBean.class));
        CallEventBean ev = CallEventBean.of(CallEventBean.TEXT);
        ev.setText(t);
        assertEquals(t, CallJson.parse(CallJson.toWire(ev), CallEventBean.class).getText());
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(
                "{\"call\":\"ab\",\"text\":{\"x\":\"text\",\"leg\":\"ab\",\"epoch\":1,\"ctr\":1,\"ct\":\"00\",\"zz\":1}}",
                CallTextRequestBean.class));
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(
                "{\"call\":\"ab\",\"text\":{\"x\":\"text\",\"leg\":\"ab\",\"epoch\":1.5,\"ctr\":1,\"ct\":\"00\"}}",
                CallTextRequestBean.class));
    }
}
