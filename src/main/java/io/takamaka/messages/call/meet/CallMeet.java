package io.takamaka.messages.call.meet;

import io.takamaka.messages.call.CallBytes;
import io.takamaka.messages.call.CallConstants;
import io.takamaka.messages.call.CallCrypto;
import io.takamaka.messages.call.CallError;
import io.takamaka.messages.call.CallJson;
import io.takamaka.messages.call.CallProtocolException;
import io.takamaka.messages.call.CallSignatures;
import io.takamaka.messages.call.beans.CallBkeyBean;
import io.takamaka.messages.call.beans.CallChannelBodyBean;
import io.takamaka.messages.call.beans.CallCommitBoxBean;
import io.takamaka.messages.call.channel.ChannelState;
import io.takamaka.messages.call.epoch.EpochSchedule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;

/**
 * [0.4] The meet (spec draft 0.4, rulings M1–M10 of 2026-11-03): the pure rules every client and the service share.
 * <ul>
 * <li>§4.4 the <b>acting owner</b> ({@link #actingOwner}): computed from the roster of the last applied fresh commit
 * and the committer's signed {@code ts}, never appointed;</li>
 * <li>§6.5 the <b>era gate</b> slot ({@link #gateSlot}) and which era reasons enlarge the roster
 * ({@link CallConstants#isEnlargingReason});</li>
 * <li>§4.3 the <b>{@code sameKeys}</b> role-change rule ({@link #roleChangeViolation});</li>
 * <li>§4.1 the conference list rules ({@link #checkMeetLists}: {@code cap}, {@code alist_max}, sorted, disjoint,
 * {@code mods ⊆ inv}, {@code apol:"open"} refused — M8);</li>
 * <li>§6.4 the listener's <b>broadcast handover</b> body with its roster ({@link #broadcastHandover});</li>
 * <li>§6.6 <b>{@code bkey}</b>: the committer's builder ({@link #buildBkey}) and the listener's acceptance
 * ({@link #acceptBkey}), mirroring {@link io.takamaka.messages.call.epoch.Commits}.</li>
 * </ul>
 * Timers (the tick, the gate's firing, the committer handover) are the client's state machine, not here.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class CallMeet {

    private CallMeet() {
    }

    /** One roster leg as the acting-owner rule sees it: its service-assigned {@code leg_index} and its identity. */
    public record RosterLeg(int legIndex, String identity) {
    }

    /**
     * Spec §4.4: the acting owner, or {@code null} when there is none. {@code roster} is the roster of the last fresh
     * commit the caller applied (every leg, speakers only — listeners are never in a roster), {@code commitTs} that
     * commit's SIGNED {@code ts}, {@code now} the caller's clock (the service: its own; a device: its wall clock with
     * the {@code ts_tolerance} slack already applied by the caller if it wants it). There is no acting owner while a
     * creator leg is in the roster, before {@code commitTs + ownerTimeout}, or when the roster is empty.
     */
    public static String actingOwner(String creator, Collection<RosterLeg> roster, long commitTs, long ownerTimeout, long now) {
        if (creator == null || roster == null || roster.isEmpty()) {
            return null;
        }
        RosterLeg lowest = null;
        for (RosterLeg l : roster) {
            if (creator.equals(l.identity())) {
                return null;
            }
            if (lowest == null || l.legIndex() < lowest.legIndex()) {
                lowest = l;
            }
        }
        if (now < commitTs + ownerTimeout) {
            return null;
        }
        return lowest.identity();
    }

    /**
     * Spec §6.5: the fixed slot of a gated era commit — the acceptance of the first enlarging era since the last fresh
     * commit plus {@code era_gate}, but no earlier than the latest era's acceptance plus {@code committer_timeout}, so
     * every leg has at least one timeout to re-announce under the latest era. S-2 / S-5 rank arithmetic counts from
     * this slot.
     */
    public static long gateSlot(long firstEraTs, long latestEraTs, long eraGate, long committerTimeout) {
        if (latestEraTs < firstEraTs) {
            throw new IllegalArgumentException("latest era before the first");
        }
        return Math.max(firstEraTs + eraGate, latestEraTs + committerTimeout);
    }

    /**
     * Spec §4.3 [0.4] the {@code sameKeys} rule: a re-announcement may change {@code role} only under a NEW era that
     * names the identity in the new role. Returns {@code null} when allowed (including no role change), else the reason
     * (refused {@link CallError#NOT_INVITED}).
     */
    public static String roleChangeViolation(String prevRole, String newRole, String prevEraHash, String newEraHash,
            String identity, Set<String> inv, Set<String> alist) {
        if (!CallConstants.ROLE_SPEAKER.equals(newRole) && !CallConstants.ROLE_LISTENER.equals(newRole)) {
            return "role";
        }
        if (newRole.equals(prevRole)) {
            return null;
        }
        if (prevEraHash != null && prevEraHash.equals(newEraHash)) {
            return "role change under an unchanged era";
        }
        if (CallConstants.ROLE_SPEAKER.equals(newRole) && !inv.contains(identity)) {
            return "promoted identity not in inv";
        }
        if (CallConstants.ROLE_LISTENER.equals(newRole) && !alist.contains(identity)) {
            return "demoted identity not in alist";
        }
        return null;
    }

    /**
     * Spec §4.1 [0.4] (M1, M2, M8) the list rules of a creation or era record. Standard mode: no {@code apol}, no
     * {@code alist}. Conference mode: {@code inv} ≤ {@code cap} ({@code rate_limited cap}), {@code apol} =
     * {@code invited} ({@code open} → {@link CallError#APOL_UNSUPPORTED}), {@code alist} ≤ {@code alistMax}
     * ({@code rate_limited alist_max}), sorted and without duplicates, disjoint from {@code inv}, {@code mods ⊆ inv}.
     * {@code inv}'s own rules (owner included, sorted, no duplicates) are the record checks of §4.1 and stay there.
     */
    public static void checkMeetLists(String mode, List<String> inv, String apol, List<String> alist, List<String> mods,
            int cap, int alistMax) throws CallProtocolException {
        if (inv == null) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "inv");
        }
        if (CallConstants.MODE_STANDARD.equals(mode)) {
            if (apol != null || (alist != null && !alist.isEmpty())) {
                throw new CallProtocolException(CallError.BAD_SIGNATURE, "audience fields on a standard call");
            }
            return;
        }
        if (!CallConstants.MODE_CONFERENCE.equals(mode)) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "mode");
        }
        if (CallConstants.APOL_OPEN.equals(apol)) {
            throw new CallProtocolException(CallError.APOL_UNSUPPORTED, "open (link) meets are release 2 (M8)");
        }
        if (!CallConstants.APOL_INVITED.equals(apol)) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "apol");
        }
        if (inv.size() > cap) {
            throw new CallProtocolException(CallError.RATE_LIMITED, CallConstants.LIMIT_CAP);
        }
        List<String> al = alist == null ? List.of() : alist;
        if (al.size() > alistMax) {
            throw new CallProtocolException(CallError.RATE_LIMITED, CallConstants.LIMIT_ALIST_MAX);
        }
        Set<String> seen = new HashSet<>();
        String prev = null;
        for (String a : al) {
            if (a == null || !seen.add(a) || (prev != null && prev.compareTo(a) >= 0)) {
                throw new CallProtocolException(CallError.BAD_SIGNATURE, "alist not sorted or has duplicates");
            }
            prev = a;
        }
        for (String a : al) {
            if (inv.contains(a)) {
                throw new CallProtocolException(CallError.BAD_SIGNATURE, "identity in both inv and alist");
            }
        }
        if (mods != null) {
            for (String m : mods) {
                if (!inv.contains(m)) {
                    throw new CallProtocolException(CallError.BAD_SIGNATURE, "moderator not in inv");
                }
            }
        }
    }

    /**
     * Spec §6.4 [0.4]: the listener's broadcast handover body — {@code h:"broadcast"}, the epoch, the era hash, the
     * SORTED roster of the epoch's speaker legs (verified by the listener against speaker announcements before it
     * adopts the key, S-7), {@code key} = {@code broadcast_e}, and {@code chain_idx} / {@code since} / {@code prev_ts}
     * exactly as a speaker's epoch handover.
     */
    public static CallChannelBodyBean broadcastHandover(long epoch, String eraHashHex, List<String> rosterHex,
            byte[] broadcastKey, long chainIdx, long sinceMs, long prevTsMs) {
        if (broadcastKey == null || broadcastKey.length != CallConstants.KEY_LEN) {
            throw new IllegalArgumentException("broadcast key must be 32 bytes");
        }
        if (sinceMs < 0 || prevTsMs < 0) {
            throw new IllegalArgumentException("since and prev_ts must be non-negative");
        }
        CallChannelBodyBean b = new CallChannelBodyBean();
        b.setH("broadcast");
        b.setEpoch(epoch);
        b.setEra(eraHashHex);
        b.setRoster(EpochSchedule.sortedHex(rosterHex));
        b.setKey(CallBytes.hex(broadcastKey));
        b.setChainIdx(chainIdx);
        b.setSince(sinceMs);
        b.setPrevTs(prevTsMs);
        return b;
    }

    /** The canonical {@code bkey} header: the object without {@code boxes} and {@code sg} — the AAD of every box. */
    public static String canonicalHeader(CallBkeyBean b) {
        return CallJson.canonicalWithout(b, "boxes", "sg");
    }

    /** What the committer built. */
    public record BuiltBkey(CallBkeyBean bkey, String canonicalHeader) {
    }

    /**
     * Spec §6.6: builds and signs the {@code bkey} of {@code epoch} — one box per present listener under that
     * listener's channel at its current {@code k}, ordered by recipient {@code leg_id}. Like a commit's builder it does
     * NOT advance the channels: the caller advances them when the fresh commit of the same epoch is accepted.
     *
     * @param listeners channel state per present listener leg_id (hex)
     */
    public static BuiltBkey buildBkey(String aud, long ts, String callIdHex, String eraHashHex, long epoch,
            byte[] broadcastKey, Map<String, ChannelState> listeners, AsymmetricCipherKeyPair committerIdentity) {
        if (broadcastKey == null || broadcastKey.length != CallConstants.KEY_LEN) {
            throw new IllegalArgumentException("broadcast key must be 32 bytes");
        }
        CallBkeyBean b = new CallBkeyBean();
        b.setAud(aud);
        b.setTs(ts);
        b.setF(CallSignatures.identityOf(committerIdentity));
        b.setCall(callIdHex);
        b.setEra(eraHashHex);
        b.setEpoch(epoch);
        String header = canonicalHeader(b);
        byte[] aad = CallBytes.ascii(header);
        List<CallCommitBoxBean> boxes = new ArrayList<>();
        for (Map.Entry<String, ChannelState> e : new TreeMap<>(listeners).entrySet()) {
            ChannelState st = e.getValue();
            boxes.add(new CallCommitBoxBean(e.getKey(), st.chainIndex(), CallBytes.hex(st.seal(aad, broadcastKey))));
        }
        b.setBoxes(boxes);
        CallSignatures.sign(b, committerIdentity);
        return new BuiltBkey(b, header);
    }

    /**
     * Spec §6.6: a listener's acceptance of a {@code bkey} — signature (type {@code bkey}, audience {@code svc:<id>}),
     * signer = the committer it computes (the caller passes the set, the owner included), the era it holds, epoch =
     * its current + 1 (else a catch-up handover), exactly one box for this leg at the channel's {@code k}, the box
     * opens under the header AAD, 32 bytes. Returns {@code broadcast_e}. Any failure is {@link CallError#KEY_REFUSED}
     * (S-1 applied to listeners: the caller alerts naming the sender), a bad signature stays
     * {@link CallError#BAD_SIGNATURE}.
     */
    public static byte[] acceptBkey(CallBkeyBean b, String expectedAud, Set<String> allowedSigners, String heldEraHashHex,
            long currentEpoch, String ownLegIdHex, ChannelState channelToCommitter) throws CallProtocolException {
        CallSignatures.verify(b, CallConstants.T_BKEY, expectedAud);
        if (!allowedSigners.contains(b.getF())) {
            throw refused("bkey signer is not the computed committer");
        }
        if (heldEraHashHex == null || !heldEraHashHex.equals(b.getEra())) {
            throw refused("era mismatch");
        }
        if (b.getEpoch() == null || b.getEpoch() != currentEpoch + 1) {
            throw refused("epoch is not current + 1 (catch-up by handover)");
        }
        CallCommitBoxBean mine = null;
        if (b.getBoxes() != null) {
            for (CallCommitBoxBean x : b.getBoxes()) {
                if (ownLegIdHex.equals(x.getTo())) {
                    if (mine != null) {
                        throw refused("two boxes for this leg");
                    }
                    mine = x;
                }
            }
        }
        if (mine == null) {
            throw refused("no box for this leg");
        }
        if (mine.getK() == null || mine.getK() != channelToCommitter.chainIndex()) {
            throw refused("chain index mismatch");
        }
        byte[] key;
        try {
            key = channelToCommitter.open(CallBytes.ascii(canonicalHeader(b)), CallBytes.unhex(mine.getBox()));
        } catch (CallCrypto.AeadException | IllegalArgumentException ex) {
            throw refused("box does not open");
        }
        if (key.length != CallConstants.KEY_LEN) {
            throw refused("broadcast key length");
        }
        return key;
    }

    private static CallProtocolException refused(String reason) {
        return new CallProtocolException(CallError.KEY_REFUSED, reason);
    }
}
