package io.takamaka.messages.call.epoch;

import io.takamaka.messages.call.CallBytes;
import io.takamaka.messages.call.CallConstants;
import io.takamaka.messages.call.CallCrypto;
import io.takamaka.messages.call.CallError;
import io.takamaka.messages.call.CallJson;
import io.takamaka.messages.call.CallProtocolException;
import io.takamaka.messages.call.CallSignatures;
import io.takamaka.messages.call.beans.CallChannelBodyBean;
import io.takamaka.messages.call.beans.CallCommitBean;
import io.takamaka.messages.call.beans.CallCommitBoxBean;
import io.takamaka.messages.call.channel.ChannelState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;

/**
 * Fresh commits (spec §6.2): the committer's builder and the purely cryptographic acceptance checks of a
 * receiving device, and [0.2] the budget window on signed {@code ts} ({@link #checkBudget}, X-1/X-2). Committer
 * selection, the catch-up timers, acknowledgements and switching are state/timing
 * rules of the client, not here.
 *
 * <p>Resolutions recorded in the C182 status report:
 * <ul>
 * <li>A-8 (RULED 2026-10-07): there is no top-level {@code chain_idx}; k is per channel (fresh commits applied since
 * that channel opened, §5.3) and every box carries its own {@code k}: {@code {"to", "k", "box"}}. Recipients may sit
 * at different k.</li>
 * <li>A-10: boxes are sealed under each channel's CURRENT k; the caller advances the
 * channels ({@link ChannelState#advance()}) once the commit is accepted by the service (committer) or applied
 * (receiver). The builder does not advance, because a commit refused by the service ({@code epoch_taken}) must not
 * move the chains.</li>
 * <li>A-12: the initial commit (epoch 0) omits {@code prev}; boxes are ordered by recipient {@code leg_id}.</li>
 * </ul>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class Commits {

    /** Spec §2.2 [0.2]: every integer is a safe integer, |n| &le; 2^53 − 1. */
    static final long MAX_SAFE_INTEGER = (1L << 53) - 1;

    private Commits() {
    }

    /** The canonical commit header: the commit without {@code boxes}, {@code conf}, {@code sg}. */
    public static String canonicalHeader(CallCommitBean c) {
        return CallJson.canonicalWithout(c, "boxes", "conf", "sg");
    }

    /** Everything the committer computed (vector group 5). */
    public record Built(CallCommitBean commit, byte[] commitSecret, byte[] epochSecret, byte[] rosterHash,
            byte[] saltE, String canonicalHeader, byte[] confirm) {

    }

    /**
     * Builds and signs a fresh commit.
     *
     * @param recipients channel state per recipient leg_id (hex), every roster leg but the committer's own
     * @param prevEpochSecret null for {@code kind:"initial"}
     */
    public static Built build(String aud, long ts, String callIdHex, String eraHashHex, long epoch, String kind,
            List<String> rosterAnnHashesHex, Map<String, ChannelState> recipients, byte[] commitSecret,
            byte[] prevEpochSecret, AsymmetricCipherKeyPair committerIdentity) {
        boolean initial = CallConstants.KIND_INITIAL.equals(kind);
        if (initial != (prevEpochSecret == null)) {
            throw new IllegalArgumentException("initial commit iff no previous epoch secret");
        }
        if (initial && (epoch != 0 || !recipients.isEmpty())) {
            throw new IllegalArgumentException("initial commit is epoch 0 to the owner's own leg");
        }
        if (commitSecret.length != 32) {
            throw new IllegalArgumentException("commit secret must be 32 bytes");
        }
        List<String> roster = EpochSchedule.sortedHex(rosterAnnHashesHex);
        byte[] eraHash = CallBytes.unhex(eraHashHex, CallConstants.HASH_LEN);
        byte[] rh = EpochSchedule.rosterHashHex(roster, eraHashHex);

        CallCommitBean c = new CallCommitBean();
        c.setAud(aud);
        c.setTs(ts);
        c.setF(CallSignatures.identityOf(committerIdentity));
        c.setCall(callIdHex);
        c.setEra(eraHashHex);
        c.setEpoch(epoch);
        c.setPrev(initial ? null : epoch - 1);
        c.setKind(kind);
        c.setRoster(roster);
        c.setRhash(CallBytes.hex(rh));
        String header = canonicalHeader(c);
        byte[] aad = CallBytes.ascii(header);

        List<CallCommitBoxBean> boxes = new ArrayList<>();
        for (Map.Entry<String, ChannelState> e : new TreeMap<>(recipients).entrySet()) {
            ChannelState st = e.getValue();
            long k = st.chainIndex();
            boxes.add(new CallCommitBoxBean(e.getKey(), k, CallBytes.hex(st.seal(aad, commitSecret))));
        }
        c.setBoxes(boxes);

        byte[] saltE = null;
        byte[] secret;
        if (initial) {
            secret = commitSecret.clone();
        } else {
            saltE = EpochSchedule.saltE(rh, eraHash, epoch);
            secret = EpochSchedule.fresh(prevEpochSecret, commitSecret, saltE);
        }
        byte[] conf = EpochSchedule.confirm(secret, header);
        c.setConf(CallBytes.hex(conf));
        CallSignatures.sign(c, committerIdentity);
        return new Built(c, commitSecret.clone(), secret, rh, saltE, header, conf);
    }

    /** What a receiving device derives from an accepted commit. */
    public record Accepted(byte[] commitSecret, byte[] epochSecret, byte[] rosterHash) {

    }

    /**
     * The acceptance checks of spec §6.2 that a receiving device can make from the commit, its own state and its
     * channel to the committer: signature (type {@code commit}, audience {@code svc:<id>}), signer allowed (the
     * computed committer or the owner — the caller passes the set), era held, epoch = current + 1 and prev, roster
     * sorted, every entry verified for this era, own announcement in it, rhash recomputes, the box opens at the
     * channel's k, conf recomputes. Any failure is {@link CallError#KEY_REFUSED} ("key rotation refused"), except
     * a bad signature which stays {@link CallError#BAD_SIGNATURE}.
     */
    public static Accepted accept(CallCommitBean c, String expectedAud, Set<String> allowedSigners,
            String heldEraHashHex, long currentEpoch, byte[] currentEpochSecret, Set<String> verifiedAnnHashesHex,
            String ownAnnHashHex, String ownLegIdHex, ChannelState channelToCommitter) throws CallProtocolException {
        return accept(c, expectedAud, allowedSigners, heldEraHashHex, currentEpoch, currentEpochSecret, verifiedAnnHashesHex,
                ownAnnHashHex, ownLegIdHex, channelToCommitter, null);
    }

    /**
     * [0.2] §6.2 the receiver's budget inputs (X-1/X-2, 2026-10-08): the signed {@code ts} of the previous fresh commit
     * it applied ({@code null} = none yet; after a handover: the handover's {@code prev_ts}, 2026-10-08), its own wall
     * clock now, and {@code refuse_below}.
     */
    public record Budget(Long prevFreshTs, long nowWall, long refuseBelow) {

    }

    /**
     * {@link #accept(CallCommitBean, String, Set, String, long, byte[], Set, String, String, ChannelState)} plus the
     * [0.2] §6.2 budget on SIGNED timestamps ({@link #checkBudget}), checked after the signature, signer, era and epoch
     * checks and before the box is opened. {@code budget == null} skips it.
     */
    public static Accepted accept(CallCommitBean c, String expectedAud, Set<String> allowedSigners,
            String heldEraHashHex, long currentEpoch, byte[] currentEpochSecret, Set<String> verifiedAnnHashesHex,
            String ownAnnHashHex, String ownLegIdHex, ChannelState channelToCommitter, Budget budget)
            throws CallProtocolException {
        CallSignatures.verify(c, CallConstants.T_COMMIT, expectedAud);
        if (!allowedSigners.contains(c.getF())) {
            throw refused("signer is neither the computed committer nor the owner");
        }
        if (!heldEraHashHex.equals(c.getEra())) {
            throw refused("era mismatch");
        }
        if (c.getEpoch() == null || c.getEpoch() != currentEpoch + 1 || c.getPrev() == null || c.getPrev() != currentEpoch) {
            throw refused("epoch is not current + 1 (catch-up by handover)");
        }
        if (CallConstants.KIND_INITIAL.equals(c.getKind())) {
            throw refused("initial commit is never received live");
        }
        if (budget != null) {
            checkBudget(c, budget.prevFreshTs(), budget.nowWall(), budget.refuseBelow());
        }
        List<String> roster = c.getRoster();
        if (roster == null || !roster.equals(EpochSchedule.sortedHex(roster)) || new HashSet<>(roster).size() != roster.size()) {
            throw refused("roster not sorted or has duplicates");
        }
        for (String a : roster) {
            if (!verifiedAnnHashesHex.contains(a)) {
                throw refused("roster entry not verified for this era");
            }
        }
        if (!roster.contains(ownAnnHashHex)) {
            throw refused("own announcement not in roster");
        }
        byte[] rh = EpochSchedule.rosterHashHex(roster, heldEraHashHex);
        if (!CallBytes.hex(rh).equals(c.getRhash())) {
            throw refused("rhash does not recompute");
        }
        CallCommitBoxBean mine = null;
        for (CallCommitBoxBean b : c.getBoxes()) {
            if (ownLegIdHex.equals(b.getTo())) {
                if (mine != null) {
                    throw refused("two boxes for this leg");
                }
                mine = b;
            }
        }
        if (mine == null) {
            throw refused("no box for this leg");
        }
        if (mine.getK() == null || mine.getK() != channelToCommitter.chainIndex()) {
            throw refused("chain index mismatch");
        }
        String header = canonicalHeader(c);
        byte[] commitSecret;
        try {
            commitSecret = channelToCommitter.open(CallBytes.ascii(header), CallBytes.unhex(mine.getBox()));
        } catch (CallCrypto.AeadException | IllegalArgumentException ex) {
            throw refused("box does not open");
        }
        if (commitSecret.length != 32) {
            throw refused("commit secret length");
        }
        byte[] secret = EpochSchedule.fresh(currentEpochSecret, commitSecret,
                EpochSchedule.saltE(rh, CallBytes.unhex(heldEraHashHex, 32), c.getEpoch()));
        byte[] conf;
        try {
            conf = CallBytes.unhex(c.getConf(), CallConstants.CONFIRM_LEN);
        } catch (IllegalArgumentException ex) {
            throw refused("conf encoding");
        }
        if (!CallCrypto.constantTimeEquals(conf, EpochSchedule.confirm(secret, header))) {
            throw refused("confirm does not recompute");
        }
        return new Accepted(commitSecret, secret, rh);
    }

    /**
     * [0.2] §6.2 budget (R11) on SIGNED timestamps — X-1/X-2, RULED 2026-10-08. Draft 0.1 measured the window on the
     * receiver's ARRIVAL clock: a commit delivered later than {@code commit_delay − refuse_below} made the receiver
     * refuse the next one and forked the call for good (X-2), and a leg frozen with its socket open measured from the
     * moment of resume and was left behind forever (X-1). Now, for a live commit {@code c}:
     * <ul>
     * <li>its {@code ts} more than {@link CallConstants#TS_TOLERANCE_MS} ahead of {@code nowWall} is refused (any kind:
     * an exempt commit RESETS the window to its ts, so a future ts would poison the next window);</li>
     * <li>a budgeted kind ({@link CallConstants#isBudgetedKind}) with {@code ts − prevFreshTs < refuseBelow} is refused
     * ("budget: …"); {@code prevFreshTs == null} (no fresh commit applied yet) never refuses.</li>
     * </ul>
     * {@code era}, {@code restart} and {@code initial} are exempt from the window and reset it (the caller stores the
     * applied commit's {@code ts} as the next {@code prevFreshTs}, whatever its kind). Failures are
     * {@link CallError#KEY_REFUSED}.
     */
    public static void checkBudget(CallCommitBean c, Long prevFreshTs, long nowWall, long refuseBelow)
            throws CallProtocolException {
        Long ts = c.getTs();
        if (ts == null) {
            throw refused("ts missing");
        }
        if (ts - nowWall > CallConstants.TS_TOLERANCE_MS) {
            throw refused("ts " + (ts - nowWall) + " ms ahead of this wall clock (> ts_tolerance "
                    + CallConstants.TS_TOLERANCE_MS + ")");
        }
        if (CallConstants.isBudgetedKind(c.getKind()) && prevFreshTs != null && ts - prevFreshTs < refuseBelow) {
            throw refused("budget: " + c.getKind() + " commit " + (ts - prevFreshTs)
                    + " ms after the previous fresh commit (signed ts; < refuse_below " + refuseBelow + ")");
        }
    }

    /**
     * [0.2] §6.2 the committer's budget anchor on its MONOTONIC clock after it applied (or made) a fresh commit with
     * signed {@code ts}, stamped {@code arrivedMono} / {@code nowWall}: the arrival, pushed later by however much the
     * commit's {@code ts} is ahead of this wall clock (at most {@code ts_tolerance}, else it was refused). A budgeted
     * commit made at this anchor + {@code commit_delay} therefore carries a {@code ts} at least {@code commit_delay}
     * after the previous one on every clock that accepted it — whatever the skew between the two committers.
     */
    public static long committerAnchor(long arrivedMono, long nowWall, long ts) {
        return arrivedMono + Math.max(0, ts - nowWall);
    }

    /**
     * [0.2] §6.2 catch-up (X-1), resolution C-1: the body a leg that is behind seals on its channel to the committer to
     * ask for a catch-up handover (§6.4 step 4): {@code {"h":"catchup","epoch":e}}, e = the epoch it holds. A committer
     * that cannot open it (the requester's {@code k} is older than its own: it missed fresh commits) treats the signed
     * channel message under a stale {@code k} as the same request.
     */
    public static CallChannelBodyBean catchUpRequest(long heldEpoch) {
        CallChannelBodyBean b = new CallChannelBodyBean();
        b.setH(CallConstants.H_CATCHUP);
        b.setEpoch(heldEpoch);
        return b;
    }

    /**
     * Builds the epoch handover body of spec §6.4 (sent by the committer in a channel open, or as a catch-up message on
     * an existing channel). {@code chainIdx} = the {@code k} of THAT channel; <b>[0.2]</b> {@code sinceMs} = the
     * committer's milliseconds elapsed since the last fresh commit it applied (O-11), never negative; <b>[0.2]
     * (2026-10-08)</b> {@code prevTsMs} = the SIGNED {@code ts} of the fresh commit that produced the handed-over
     * secret, the reference of the adopting leg's budget window (§6.2).
     */
    public static CallChannelBodyBean epochHandover(long epoch, String eraHashHex, List<String> rosterHex,
            byte[] epochSecret, long chainIdx, long sinceMs, long prevTsMs) {
        if (sinceMs < 0 || sinceMs > MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException("since must be a non-negative safe integer");
        }
        if (prevTsMs < 0 || prevTsMs > MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException("prev_ts must be a non-negative safe integer");
        }
        CallChannelBodyBean b = new CallChannelBodyBean();
        b.setH("epoch");
        b.setEpoch(epoch);
        b.setEra(eraHashHex);
        b.setRoster(EpochSchedule.sortedHex(rosterHex));
        b.setSecret(CallBytes.hex(epochSecret));
        b.setChainIdx(chainIdx);
        b.setSince(sinceMs);
        b.setPrevTs(prevTsMs);
        return b;
    }

    /**
     * [0.2] {@code now − since} (§6.4, O-11) on the clock {@code nowMono} is read from. Since the {@code prev_ts}
     * ruling (2026-10-08) it is NO LONGER the receiver's budget window — that is {@link #adoptedPrevFreshTs}, the
     * committer's signed ts — only one of the two bounds of the adopting leg's committer anchor
     * ({@link #handoverAnchor}). The committer computes {@code since} as its wall clock now − the signed ts of the
     * previous fresh commit it applied.
     */
    public static long budgetWindowStart(long nowMono, CallChannelBodyBean h) {
        return nowMono - h.getSince();
    }

    /**
     * [0.2] §6.4 {@code prev_ts} (RULED 2026-10-08): the {@code prevFreshTs} of {@link #checkBudget} for a leg that
     * adopted its epoch by handover = the handover's {@code prev_ts}, the signed ts of the fresh commit that produced the
     * secret — never its own wall clock minus {@code since}, which ran late by the handover's delay plus the clock skew
     * and made it refuse the next commit once when that exceeded {@code commit_delay − refuse_below}.
     */
    public static long adoptedPrevFreshTs(CallChannelBodyBean h) {
        return h.getPrevTs();
    }

    /**
     * [0.2] The adopting leg's committer anchor on its MONOTONIC clock (where its own next budgeted slot is measured
     * from): the later of {@code arrival − since} (O-11) and the instant this wall clock reads {@code prev_ts}
     * ({@code arrival + prev_ts − wallAtArrival}) — so, like {@link #committerAnchor}, its next budgeted commit carries a
     * {@code ts} at least {@code commit_delay} after {@code prev_ts} even when this clock is behind the committer's.
     */
    public static long handoverAnchor(long arrivedMono, long wallAtArrival, CallChannelBodyBean h) {
        return Math.max(budgetWindowStart(arrivedMono, h), arrivedMono + h.getPrevTs() - wallAtArrival);
    }

    /** Builds the listener (broadcast) handover body of spec §6.4. */
    public static CallChannelBodyBean broadcastHandover(long epoch, String eraHashHex, byte[] broadcast) {
        CallChannelBodyBean b = new CallChannelBodyBean();
        b.setH("broadcast");
        b.setEpoch(epoch);
        b.setEra(eraHashHex);
        b.setKey(CallBytes.hex(broadcast));
        return b;
    }

    /**
     * Newcomer's checks of an epoch handover (spec §6.4): era held, roster sorted and every entry verified, own
     * announcement in it; returns roster_hash_e. <b>[0.2] §6.4 prev_ts far-future (C-4, 2026-10-09)</b>: a
     * {@code prev_ts} more than {@link CallConstants#TS_TOLERANCE_MS} ahead of {@code nowWall} (the adopter's wall
     * clock) is refused, the same rule as a commit's {@code ts} ({@link #checkBudget}): it becomes the adopted budget
     * window, so a future one would make the newcomer refuse every on-time commit.
     */
    public static byte[] acceptEpochHandover(CallChannelBodyBean h, String heldEraHashHex,
            Set<String> verifiedAnnHashesHex, String ownAnnHashHex, long nowWall) throws CallProtocolException {
        if (!"epoch".equals(h.getH()) || h.getSecret() == null || h.getEpoch() == null || h.getRoster() == null) {
            throw refused("not an epoch handover");
        }
        if (!heldEraHashHex.equals(h.getEra())) {
            throw refused("era mismatch");
        }
        if (!h.getRoster().equals(EpochSchedule.sortedHex(h.getRoster()))) {
            throw refused("roster not sorted");
        }
        for (String a : h.getRoster()) {
            if (!verifiedAnnHashesHex.contains(a)) {
                throw refused("roster entry not verified");
            }
        }
        if (!h.getRoster().contains(ownAnnHashHex)) {
            throw refused("own announcement not in roster");
        }
        if (!CallBytes.isLowerHex(h.getSecret(), 32)) {
            throw refused("secret encoding");
        }
        if (h.getSince() == null || h.getSince() < 0 || h.getSince() > MAX_SAFE_INTEGER) {
            throw refused("since missing or not a non-negative safe integer"); // [0.2] O-11
        }
        if (h.getPrevTs() == null || h.getPrevTs() < 0 || h.getPrevTs() > MAX_SAFE_INTEGER) {
            throw refused("prev_ts missing or not a non-negative safe integer"); // [0.2] 2026-10-08, as since (B1-6)
        }
        if (h.getPrevTs() - nowWall > CallConstants.TS_TOLERANCE_MS) { // [0.2] §6.4 prev_ts far-future (C-4, 2026-10-09)
            throw refused("prev_ts " + (h.getPrevTs() - nowWall) + " ms ahead of this wall clock (> ts_tolerance "
                    + CallConstants.TS_TOLERANCE_MS + ")");
        }
        return EpochSchedule.rosterHashHex(h.getRoster(), heldEraHashHex);
    }

    private static CallProtocolException refused(String reason) {
        return new CallProtocolException(CallError.KEY_REFUSED, reason);
    }

}
