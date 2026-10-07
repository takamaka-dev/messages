package io.takamaka.messages.call;

import io.takamaka.messages.call.beans.CallCommitBean;
import io.takamaka.messages.call.channel.ChannelState;
import io.takamaka.messages.call.channel.HybridChannel;
import io.takamaka.messages.call.epoch.Commits;
import io.takamaka.messages.call.epoch.EpochSchedule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EpochCommitTest {

    static CallVectorScenario s;

    @BeforeAll
    static void build() throws Exception {
        s = new CallVectorScenario();
    }

    @Test
    void receiverDerivesTheCommittersSecrets() {
        assertArrayEquals(s.es3, s.bAcceptLeave.epochSecret());
        assertArrayEquals(s.es4, s.bAcceptEra.epochSecret());
        assertArrayEquals(s.cLeave.commitSecret(), s.bAcceptLeave.commitSecret());
        assertArrayEquals(s.es0, CallTestKeys.seed("commit_secret/0"), "epoch_secret_0 = commit_secret_0");
        assertNull(s.cInitial.commit().getPrev());
        assertTrue(s.cInitial.commit().getBoxes().isEmpty());
        assertEquals(0L, s.cLeave.commit().getBoxes().get(0).getK());
        assertEquals(1L, s.cEra.commit().getBoxes().get(0).getK());
        assertFalse(Commits.canonicalHeader(s.cEra.commit()).contains("chain_idx"));
    }

    @Test
    void freshFormulaByHand() {
        String hO = CallHashes.annHashHex(s.annO), hB = CallHashes.annHashHex(s.annB);
        byte[] rh = EpochSchedule.rosterHashHex(List.of(hB, hO), s.eraHash0);
        assertArrayEquals(CallCrypto.h(CallBytes.unhex(EpochSchedule.sortedHex(List.of(hO, hB)).get(0)),
                CallBytes.unhex(EpochSchedule.sortedHex(List.of(hO, hB)).get(1)), CallBytes.unhex(s.eraHash0)), rh);
        byte[] salt = CallCrypto.h(rh, CallBytes.unhex(s.eraHash0), new byte[]{0, 0, 0, 3});
        byte[] prk = CallCrypto.hkdfExtract(salt, CallBytes.concat(s.es2, CallTestKeys.seed("commit_secret/3")));
        assertArrayEquals(CallCrypto.hkdfExpand(prk, CallBytes.ascii("tkm-call/v1/epoch"), 32), s.es3);
        byte[] step = CallCrypto.hkdf(s.es0, CallBytes.concat(CallBytes.ascii("tkm-call/v1/step"), CallBytes.unhex(hB), rh(List.of(hO, hB))));
        assertArrayEquals(step, s.es1);
    }

    private static byte[] rh(List<String> l) {
        return EpochSchedule.rosterHashHex(l, s.eraHash0);
    }

    @Test
    void codeIsTwelveDigits() {
        for (byte[] e : List.of(s.es0, s.es1, s.es2, s.es3, s.es4)) {
            String c = EpochSchedule.code(e);
            assertTrue(c.matches("\\d{12}"), c);
            byte[] b = EpochSchedule.codeBytes(e);
            long x = 0;
            for (int i = 0; i < 5; i++) {
                x = (x << 8) | (b[i] & 0xff);
            }
            assertEquals(x % 1_000_000_000_000L, Long.parseLong(c));
        }
    }

    @Test
    void confirmIsTruncatedHmacOverHeaderHash() {
        String header = Commits.canonicalHeader(s.cLeave.commit());
        assertFalse(header.contains("\"boxes\"") || header.contains("\"conf\"") || header.contains("\"sg\""));
        byte[] mac = CallCrypto.hmacSha512(s.es3, CallBytes.ascii("tkm-call/v1/confirm"), CallCrypto.h(CallBytes.ascii(header)));
        assertEquals(CallBytes.hex(java.util.Arrays.copyOf(mac, 16)), s.cLeave.commit().getConf());
    }

    @Test
    void derivedKeysAreDistinct() {
        byte[] leg = s.legs.get("bob").legId;
        byte[] era = CallBytes.unhex(s.eraHash1);
        byte[] sb = EpochSchedule.senderBase(s.es4, era, leg);
        byte[] tk = EpochSchedule.textKey(s.es4, era, leg);
        byte[] bc = EpochSchedule.broadcast(s.es4, era);
        assertFalse(java.util.Arrays.equals(sb, tk));
        assertFalse(java.util.Arrays.equals(sb, bc));
        assertFalse(java.util.Arrays.equals(sb, EpochSchedule.senderBase(s.es4, era, s.legs.get("owner").legId)));
        assertFalse(java.util.Arrays.equals(sb, EpochSchedule.senderBase(s.es3, era, leg)));
        // A-15 RULED: a listener holding only broadcast_e derives the same frame and text keys
        assertArrayEquals(sb, EpochSchedule.senderBaseFromBroadcast(bc, leg));
        assertArrayEquals(tk, EpochSchedule.textKeyFromBroadcast(bc, leg));
        assertArrayEquals(CallCrypto.hkdf(bc, CallBytes.concat(CallBytes.ascii("tkm-call/v1/sender"), leg)), sb);
    }

    /** Fresh channel pair owner→bob at k = 0 plus the epoch-2 state, for refusal tests. */
    private record Rig(ChannelState o, ChannelState b, Commits.Built built) {

    }

    private Rig rig() throws Exception {
        return rig("encaps/rig");
    }

    private Rig rig(String encapsSeed) throws Exception {
        HybridChannel.OpenResult open = HybridChannel.openChannel(s.callId, s.annO, s.legs.get("owner").xPriv, s.annB,
                s.kem, CallTestKeys.seed(encapsSeed), null, null, s.ids.get("owner"), 1L);
        HybridChannel.AcceptResult acc = HybridChannel.acceptChannel(open.channel(),
                CallVectorScenario.rosterOf(s.annO, s.annB), s.annB, s.legs.get("bob").xPriv, s.legs.get("bob").kemKeys.dk(), s.kem);
        Map<String, ChannelState> rec = new HashMap<>();
        rec.put(CallBytes.hex(s.legs.get("bob").legId), open.state());
        Commits.Built built = Commits.build(CallVectorScenario.AUD_SVC, 2L, s.callId, s.eraHash0, 3, CallConstants.KIND_LEAVE,
                List.of(CallHashes.annHashHex(s.annO), CallHashes.annHashHex(s.annB)), rec, CallTestKeys.seed("rig/cs"),
                s.es2, s.ids.get("owner"));
        return new Rig(open.state(), acc.state(), built);
    }

    private Commits.Accepted accept(CallCommitBean c, ChannelState b, Set<String> signers) throws CallProtocolException {
        String hO = CallHashes.annHashHex(s.annO), hB = CallHashes.annHashHex(s.annB), hC = CallHashes.annHashHex(s.annC);
        return Commits.accept(c, CallVectorScenario.AUD_SVC, signers, s.eraHash0, 2, s.es2, Set.of(hO, hB, hC), hB,
                CallBytes.hex(s.legs.get("bob").legId), b);
    }

    @Test
    void acceptanceRefusals() throws Exception {
        Set<String> owner = Set.of(s.id("owner"));
        // happy path on the matching channel
        Rig ok = rig();
        assertArrayEquals(ok.built().epochSecret(), accept(ok.built().commit(), ok.b(), owner).epochSecret());

        // signer not allowed
        Rig r1 = rig();
        assertRefused(() -> accept(r1.built().commit(), r1.b(), Set.of(s.id("bob"))), "signer is neither the computed committer nor the owner");

        // re-signed commits with a broken field (so the signature is valid and the key-plane check is reached)
        Rig r2 = rig();
        CallCommitBean badRhash = copy(r2.built().commit());
        badRhash.setRhash("00".repeat(32));
        resign(badRhash);
        assertRefused(() -> accept(badRhash, r2.b(), owner), "rhash does not recompute");

        Rig r3 = rig();
        CallCommitBean badConf = copy(r3.built().commit());
        badConf.setConf("00".repeat(16));
        resign(badConf);
        assertRefused(() -> accept(badConf, r3.b(), owner), "confirm does not recompute");

        Rig r4 = rig();
        CallCommitBean ghost = copy(r4.built().commit());
        java.util.List<String> roster = new java.util.ArrayList<>(ghost.getRoster());
        roster.add("ff".repeat(32));
        ghost.setRoster(roster);
        resign(ghost);
        assertRefused(() -> accept(ghost, r4.b(), owner), "roster entry not verified for this era");

        Rig r5 = rig();
        CallCommitBean gap = copy(r5.built().commit());
        gap.setEpoch(5L);
        gap.setPrev(4L);
        resign(gap);
        assertRefused(() -> accept(gap, r5.b(), owner), "epoch is not current + 1 (catch-up by handover)");

        // the box is under another channel's key (different encapsulation): the box does not open
        Rig r6 = rig();
        Rig other = rig("encaps/other");
        assertRefused(() -> accept(r6.built().commit(), other.b(), owner), "box does not open");

        // a header field changed after the boxes were sealed: AAD mismatch even though re-signed
        Rig r7 = rig();
        CallCommitBean ts = copy(r7.built().commit());
        ts.setTs(99L);
        resign(ts);
        assertRefused(() -> accept(ts, r7.b(), owner), "box does not open");
    }

    /** A-8 RULED: recipients at different k; each box carries its own k and opens on its own channel. */
    @Test
    void recipientsAtDifferentChainIndices() throws Exception {
        Rig a = rig("encaps/a");
        Rig b = rig("encaps/b");
        b.o().advance();
        b.b().advance();
        b.o().advance();
        b.b().advance();
        Map<String, ChannelState> rec = new HashMap<>();
        rec.put("0000000000000001", a.o());
        rec.put("0000000000000002", b.o());
        Commits.Built built = Commits.build(CallVectorScenario.AUD_SVC, 1L, s.callId, s.eraHash0, 3, CallConstants.KIND_LEAVE,
                List.of(CallHashes.annHashHex(s.annO), CallHashes.annHashHex(s.annB)), rec, CallTestKeys.seed("x"), s.es2, s.ids.get("owner"));
        assertEquals(0L, built.commit().getBoxes().get(0).getK());
        assertEquals(2L, built.commit().getBoxes().get(1).getK());
        String hO = CallHashes.annHashHex(s.annO), hB = CallHashes.annHashHex(s.annB);
        for (Object[] p : new Object[][]{{"0000000000000001", a.b()}, {"0000000000000002", b.b()}}) {
            Commits.Accepted acc = Commits.accept(built.commit(), CallVectorScenario.AUD_SVC, Set.of(s.id("owner")), s.eraHash0, 2,
                    s.es2, Set.of(hO, hB), hB, (String) p[0], (ChannelState) p[1]);
            assertArrayEquals(built.epochSecret(), acc.epochSecret());
        }
        // a box whose declared k disagrees with the receiver's channel is refused
        CallCommitBean lie = copy(built.commit());
        lie.getBoxes().get(1).setK(1L);
        resign(lie);
        Rig c = rig("encaps/b");
        c.b().advance();
        c.b().advance();
        assertRefused(() -> Commits.accept(lie, CallVectorScenario.AUD_SVC, Set.of(s.id("owner")), s.eraHash0, 2, s.es2,
                Set.of(hO, hB), hB, "0000000000000002", c.b()), "chain index mismatch");
    }

    @Test
    void handoverAcceptance() throws Exception {
        String hO = CallHashes.annHashHex(s.annO), hB = CallHashes.annHashHex(s.annB);
        byte[] rh = Commits.acceptEpochHandover(s.accOB.body(), s.eraHash0, Set.of(hO, hB), hB);
        assertArrayEquals(EpochSchedule.rosterHashHex(List.of(hO, hB), s.eraHash0), rh);
        assertEquals(CallBytes.hex(s.es1), s.accOB.body().getSecret());
        assertThrows(CallProtocolException.class, () -> Commits.acceptEpochHandover(s.accOB.body(), s.eraHash1, Set.of(hO, hB), hB));
        assertThrows(CallProtocolException.class, () -> Commits.acceptEpochHandover(s.accOB.body(), s.eraHash0, Set.of(hB), hB));
    }

    private void resign(CallCommitBean c) {
        c.setSg(null);
        CallSignatures.sign(c, s.ids.get("owner"));
    }

    private static CallCommitBean copy(CallCommitBean c) throws Exception {
        return CallJson.parse(CallJson.toWire(c), CallCommitBean.class);
    }

    interface Call {

        void run() throws Exception;
    }

    private static void assertRefused(Call c, String reason) {
        CallProtocolException ex = assertThrows(CallProtocolException.class, c::run);
        assertEquals(CallError.KEY_REFUSED, ex.getError(), ex.getMessage());
        assertEquals(reason, ex.getReason());
    }
}
