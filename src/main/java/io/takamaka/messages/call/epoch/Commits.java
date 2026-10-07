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
 * receiving device. Committer selection, the budget window, acknowledgements and switching are state/timing
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

    /** Builds the epoch handover body of spec §6.4 (sent by the committer in a channel open). */
    public static CallChannelBodyBean epochHandover(long epoch, String eraHashHex, List<String> rosterHex,
            byte[] epochSecret, long chainIdx) {
        CallChannelBodyBean b = new CallChannelBodyBean();
        b.setH("epoch");
        b.setEpoch(epoch);
        b.setEra(eraHashHex);
        b.setRoster(EpochSchedule.sortedHex(rosterHex));
        b.setSecret(CallBytes.hex(epochSecret));
        b.setChainIdx(chainIdx);
        return b;
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
     * announcement in it; returns roster_hash_e.
     */
    public static byte[] acceptEpochHandover(CallChannelBodyBean h, String heldEraHashHex,
            Set<String> verifiedAnnHashesHex, String ownAnnHashHex) throws CallProtocolException {
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
        return EpochSchedule.rosterHashHex(h.getRoster(), heldEraHashHex);
    }

    private static CallProtocolException refused(String reason) {
        return new CallProtocolException(CallError.KEY_REFUSED, reason);
    }

}
