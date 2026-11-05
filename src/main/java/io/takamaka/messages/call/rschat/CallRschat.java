package io.takamaka.messages.call.rschat;

import io.takamaka.messages.call.CallBytes;
import io.takamaka.messages.call.CallConstants;
import io.takamaka.messages.call.CallError;
import io.takamaka.messages.call.CallProtocolException;
import io.takamaka.messages.call.beans.CallLookupBean;
import io.takamaka.messages.call.beans.CallMeetNoticeBean;
import io.takamaka.messages.call.beans.CallRingBean;
import io.takamaka.messages.chat.notification.UserNotificationJsonBean;
import io.takamaka.messages.utils.NOTIFICATION_TYPES;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The rschat side of tkm-call/v1 (C182 build 3, DR-055): building and checking the two call objects rschat accepts —
 * {@code ring} (spec §7.1, caller → rschat) and {@code lookup} (§8.3, call service → rschat) — and reading a ring
 * back from a {@code CALL_RING} notification. Routes: {@code ChatServerEndpoints.CALL_NONCE}, {@code CALL_RING},
 * {@code CALL_LOOKUP}.
 *
 * <p>Both objects carry rschat's nonce ({@code n}, the §8.1 form bound to {@code aud = rschat:<net>}, issued by the
 * {@code callnonce} route) and are signed with the call signature octets (§3.2, {@code tkm-call/v1/ring} and
 * {@code tkm-call/v1/lookup}): a chat signature never verifies here and vice versa (N13).
 *
 * <p>The structural checks here run AFTER the signature verified ({@link io.takamaka.messages.call.CallSignatures#verify})
 * and refuse with {@code bad_signature}, as the call service does for a malformed field: §11 has no "malformed"
 * code, and a malformed field of a signed object is a signer error.
 *
 * <p>[0.2] (C-16 answered elsewhere, 2026-10-09): a {@code ring} with {@code k = "answered"}, signed by the CALLEE with
 * {@code to = [f]}, is the answered notice (§7.1): rschat checks it as a ring and delivers {@link #NOTIFICATION_TYPE_ANSWERED}
 * to the callee's own devices; a device still ringing for that call declines {@code elsewhere} to the service (§7.6).
 *
 * <p>P2 (caller cancel, design CALL_RING_ANY_STATE_DESIGN_2026-11-05 §5, operator Q5, Iris §7.123 I2/RA-3/RA-4/RA-5/RA-8,
 * 2026-11-05): a {@code ring} with {@code k = "cancelled"}, signed by the CALLER with the ring's own {@code call},
 * {@code svc}, {@code to} and {@code mode}, is the caller's cancel: the outgoing call ended before anyone answered.
 * rschat relays {@link #NOTIFICATION_TYPE_CANCELLED} only to the callees of a ring it admitted with the same caller and
 * call id, at most once each, live AND as a silent push, both carrying the signed cancel ({@link #pushRing}); a device
 * acts on it only through {@link #checkCancel} (one verifier for both paths) and only to stop a ring of that call id
 * from that verified caller that is not newer than the cancel ({@link #isNotOlderThanRing}).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class CallRschat {

    private CallRschat() {
    }

    /** The notification type of a ring ({@link NOTIFICATION_TYPES#CALL_RING}). */
    public static final String NOTIFICATION_TYPE = NOTIFICATION_TYPES.CALL_RING.name();

    /** [0.2] §7.1: the notification type of an answered notice ({@link NOTIFICATION_TYPES#CALL_ANSWERED}). */
    public static final String NOTIFICATION_TYPE_ANSWERED = NOTIFICATION_TYPES.CALL_ANSWERED.name();

    /** P2: the notification type of the caller's cancel ({@link NOTIFICATION_TYPES#CALL_CANCELLED}). */
    public static final String NOTIFICATION_TYPE_CANCELLED = NOTIFICATION_TYPES.CALL_CANCELLED.name();

    /**
     * Initial limit on {@code to} per ring. S-12 (Q13, 2026-10-29): 25 (interim step, operator 10-29 17:3x; was 100),
     * below the invitation-list size ({@code inv_max} 100, spec §8.2) — a longer list needs several rings, each charged
     * {@code to.size()} units. rschat's {@code rschat.calls.ring-to-max} defaults to this value.
     */
    public static final int RING_TO_MAX = 25;

    /** The {@code limit} of the {@code rate_limited} refusal of a ring whose {@code to} is longer than the cap. */
    public static final String LIMIT_RING_TO_MAX = "ring_to_max";

    /** An identity in registration-string form: Base64URL of 32 bytes with {@code .} padding. */
    private static final Pattern IDENTITY = Pattern.compile("[A-Za-z0-9_-]{43}\\.");
    /** A service id: short, ASCII, no separators that could alter an audience string. */
    private static final Pattern SERVICE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    /** A network name. */
    private static final Pattern NETWORK = Pattern.compile("[a-z0-9-]{1,32}");

    /** {@code rschat:<net>} (spec §3.1). */
    public static String audience(String network) {
        if (network == null || !NETWORK.matcher(network).matches()) {
            throw new IllegalArgumentException("invalid network name");
        }
        return CallConstants.AUD_RSCHAT + network;
    }

    public static boolean isIdentity(String s) {
        return s != null && IDENTITY.matcher(s).matches();
    }

    public static boolean isServiceId(String s) {
        return s != null && SERVICE_ID.matcher(s).matches();
    }

    /** An unsigned ring (sign it with {@link io.takamaka.messages.call.CallSignatures#sign}). */
    public static CallRingBean ring(String callId, String svc, List<String> to, String mode, String nonce, String aud, long ts) {
        CallRingBean r = new CallRingBean();
        r.setAud(aud);
        r.setN(nonce);
        r.setTs(ts);
        r.setCall(callId);
        r.setSvc(svc);
        r.setTo(List.copyOf(to));
        r.setMode(mode);
        return r;
    }

    /**
     * [0.2] §7.1: an unsigned answered notice for the ring {@code callId} — {@code to = [self]}, {@code k = answered},
     * {@code svc} and {@code mode} copied from the ring; sign it with the CALLEE's key ({@code f} must be {@code self}).
     */
    public static CallRingBean answered(String callId, String svc, String mode, String self, String nonce, String aud, long ts) {
        CallRingBean r = ring(callId, svc, List.of(self), mode, nonce, aud, ts);
        r.setK(CallConstants.RING_K_ANSWERED);
        return r;
    }

    /** [0.2] §7.1: true for an answered notice ({@code k = answered}), false for a ring. */
    public static boolean isAnswered(CallRingBean r) {
        return r != null && CallConstants.RING_K_ANSWERED.equals(r.getK());
    }

    /**
     * P2: the caller's unsigned cancel of its ring {@code callId} — {@code k = cancelled}, with the ring's own
     * {@code svc}, {@code to} (the callees, never the caller) and {@code mode}; sign it with the CALLER's key (the
     * ring's {@code f}). Send it only when the outgoing call ended before anyone answered.
     */
    public static CallRingBean cancelled(String callId, String svc, List<String> to, String mode, String nonce, String aud,
            long ts) {
        CallRingBean r = ring(callId, svc, to, mode, nonce, aud, ts);
        r.setK(CallConstants.RING_K_CANCELLED);
        return r;
    }

    /** P2: true for the caller's cancel ({@code k = cancelled}). */
    public static boolean isCancelled(CallRingBean r) {
        return r != null && CallConstants.RING_K_CANCELLED.equals(r.getK());
    }

    /** An unsigned lookup of ONE identity (sign it with the service key). */
    public static CallLookupBean lookup(String identity, String nonce, String aud, long ts) {
        CallLookupBean l = new CallLookupBean();
        l.setAud(aud);
        l.setN(nonce);
        l.setTs(ts);
        l.setId(identity);
        return l;
    }

    /**
     * §7.1 fields of a VERIFIED ring: {@code call} 32-byte hex, {@code svc} a service id, {@code mode} an enumeration,
     * {@code to} 1..{@code toMax} distinct identities none of which is {@code f}, {@code f} an identity, {@code n}
     * present. [0.2] {@code k} absent (a ring) or {@code answered} (the answered notice, whose {@code to} is exactly
     * {@code [f]}); anything else is refused. A {@code to} longer than {@code toMax} is a LIMIT, not a malformed object:
     * {@link CallError#RATE_LIMITED} with reason {@link #LIMIT_RING_TO_MAX} (2026-10-29: it used to come back as
     * {@code bad_signature}, which told the user nothing); every other violation stays {@link CallError#BAD_SIGNATURE}
     * (a malformed field of a signed object, §7.1 — §11 has no other structural code).
     *
     * <p>Iris §7.117 item 8m (2026-10-30): the identity and duplicate checks on {@code to} run BEFORE the length check,
     * so a junk list of any length answers {@code bad_signature} and only a well-formed over-long list is the limit; and
     * a ring whose {@code to} names its own signer is refused — the answered notice is the one object addressed to self.
     *
     * <p>P2 (2026-11-05): {@code k = cancelled} (the caller's cancel) has exactly a ring's shape — {@code to}
     * 1..{@code toMax} distinct identities, never {@code f}. Any other {@code k} is still refused.
     */
    public static void checkRing(CallRingBean r, int toMax) throws CallProtocolException {
        if (r == null || !isIdentity(r.getF()) || r.getN() == null || r.getN().isEmpty()
                || !CallBytes.isLowerHex(r.getCall(), CallConstants.HASH_LEN) || !isServiceId(r.getSvc())
                || !(CallConstants.MODE_STANDARD.equals(r.getMode()) || CallConstants.MODE_CONFERENCE.equals(r.getMode()))) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "ring fields");
        }
        List<String> to = r.getTo();
        if (to == null || to.isEmpty()) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "ring to");
        }
        // the answered notice is the one object addressed to its own signer; a ring and a cancel never name f
        boolean answered = isAnswered(r);
        Set<String> seen = new HashSet<>();
        for (String id : to) {
            if (!isIdentity(id) || !seen.add(id)) {
                throw new CallProtocolException(CallError.BAD_SIGNATURE, "ring to");
            }
            if (!answered && id.equals(r.getF())) {
                throw new CallProtocolException(CallError.BAD_SIGNATURE, "ring self");
            }
        }
        if (to.size() > toMax) {
            throw new CallProtocolException(CallError.RATE_LIMITED, LIMIT_RING_TO_MAX);
        }
        if (r.getK() != null && !(isCancelled(r) || (answered && to.size() == 1 && r.getF().equals(to.get(0))))) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "ring k");
        }
    }

    /** §8.3 fields of a VERIFIED lookup: one identity in {@code id}, {@code n} present. */
    public static void checkLookup(CallLookupBean l) throws CallProtocolException {
        if (l == null || !isIdentity(l.getId()) || l.getN() == null || l.getN().isEmpty()) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "lookup fields");
        }
    }

    // ---- G-6 (2026-11-03): the caller's signed ring inside the CALL_RING push ---------------------------------------

    /**
     * G-6 (ruling O-1 b, 2026-11-03): the FCM data field of a {@code CALL_RING} push that carries the caller's SIGNED
     * ring, so a device whose app process is cold (no unlocked identity, no call service reachable from the push
     * handler) can verify the caller's identity before naming it (N7). Value: {@link #pushRing}. Absent when the
     * ring does not fit the push ({@link #FCM_DATA_MAX_BYTES}): the push then carries today's four fields only and
     * the device shows a generic banner.
     */
    public static final String PUSH_RING_FIELD = "ring";

    /** FCM refuses a data message whose payload exceeds this many bytes. */
    public static final int FCM_DATA_MAX_BYTES = 4096;

    /**
     * A pushed ring is fresh while {@code ts} is at most this old (the caller gives up at 90 s; the push itself
     * expires after 60 s) …
     */
    public static final long PUSH_RING_MAX_AGE_MS = 90_000L;

    /** … and at most this far in the future (the caller's clock against this device's). */
    public static final long PUSH_RING_MAX_SKEW_MS = 60_000L;

    /**
     * The canonical push form of a verified ring: RFC 8785 JCS of the WHOLE signed bean, {@code sg} included — the
     * same text the signer's {@code callring} request carried, re-serialised canonically (ASCII by construction:
     * every field of a verified ring is ASCII). The reader parses it as a call object and verifies it exactly as
     * rschat did ({@link io.takamaka.messages.call.CallSignatures#verify}: {@code sg} removed, {@code tkm-call/v1/ring}
     * context, audience {@code rschat:<net>}). No bean is added or changed: the field carries a {@link CallRingBean}.
     */
    public static String pushRing(CallRingBean r) {
        return io.takamaka.messages.call.CallJson.canonical(r);
    }

    /**
     * The reader's check of a pushed ring ({@link #PUSH_RING_FIELD}), the way a cold device does it: parsed strictly,
     * verified under the ring context for {@code aud} (the audience of the network this device's PINNED call service
     * serves — never taken from the push), shape-checked as rschat does ({@link #checkRing}), a ring and not an
     * answered notice, and bound to the push's own four fields ({@code notice}: call_id, svc, f, mode — a signed
     * ring of one call must not dress a push of another). {@code me} (the device's push identity) must be among
     * {@code to} when known. Freshness is time-dependent and separate: {@link #isFreshPushRing}.
     *
     * @return the verified ring; the caller is {@code getF()}
     * @throws CallProtocolException {@code bad_signature} on any failure (the reader shows the generic banner)
     */
    public static CallRingBean checkPushRing(String ringJson, String aud, CallRingNoticeBean notice, String me, int toMax)
            throws CallProtocolException {
        if (ringJson == null || notice == null || aud == null) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "push ring absent");
        }
        final CallRingBean r;
        try {
            r = io.takamaka.messages.call.CallJson.parse(ringJson, CallRingBean.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException ex) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "push ring parse");
        }
        io.takamaka.messages.call.CallSignatures.verify(r, CallConstants.T_RING, aud);
        checkRing(r, toMax);
        if (isAnswered(r)) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "push ring k");
        }
        if (!r.getCall().equals(notice.getCall()) || !r.getSvc().equals(notice.getSvc())
                || !r.getF().equals(notice.getF()) || !r.getMode().equals(notice.getMode())) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "push ring mismatch");
        }
        if (me != null && !r.getTo().contains(me)) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "push ring not for me");
        }
        return r;
    }

    /** True while {@code r.ts} is within {@link #PUSH_RING_MAX_AGE_MS} behind / {@link #PUSH_RING_MAX_SKEW_MS} ahead of {@code nowMillis}. */
    public static boolean isFreshPushRing(CallRingBean r, long nowMillis) {
        if (r == null || r.getTs() == null) {
            return false;
        }
        long ts = r.getTs();
        return nowMillis - ts <= PUSH_RING_MAX_AGE_MS && ts - nowMillis <= PUSH_RING_MAX_SKEW_MS;
    }

    /** The notice delivered for a verified ring: call, svc, f, mode — nothing else. */
    public static CallRingNoticeBean notice(CallRingBean r) {
        return new CallRingNoticeBean(r.getCall(), r.getSvc(), r.getF(), r.getMode());
    }

    /**
     * The ring a notification carries, if it is a well-formed {@code CALL_RING} (the client side of §7.1). The notice
     * is a hint: the caller is shown as verified only after the creation record has been fetched from the PINNED
     * service and verified (N7, N14).
     */
    public static Optional<CallRingNoticeBean> ringOf(UserNotificationJsonBean n) {
        return noticeOf(n, NOTIFICATION_TYPE);
    }

    /**
     * [0.2] §7.1: the answered notice a notification carries, if it is a well-formed {@code CALL_ANSWERED} — a device of
     * this identity accepted {@code call}. A hint written by rschat: it only stops a ring (a false one costs a ring).
     */
    public static Optional<CallRingNoticeBean> answeredOf(UserNotificationJsonBean n) {
        return noticeOf(n, NOTIFICATION_TYPE_ANSWERED);
    }

    private static Optional<CallRingNoticeBean> noticeOf(UserNotificationJsonBean n, String type) {
        if (n == null || !type.equals(n.getNotificationType())) {
            return Optional.empty();
        }
        CallRingNoticeBean r = n.getRing();
        if (r == null || !CallBytes.isLowerHex(r.getCall(), CallConstants.HASH_LEN) || !isServiceId(r.getSvc())
                || !isIdentity(r.getF())
                || !(CallConstants.MODE_STANDARD.equals(r.getMode()) || CallConstants.MODE_CONFERENCE.equals(r.getMode()))) {
            return Optional.empty();
        }
        return Optional.of(r);
    }
    // ---- P2 (2026-11-05): the caller's cancel, read by a callee device ----------------------------------------------

    /**
     * P2 (Iris §7.123 I2): the cancel a {@code CALL_CANCELLED} notification names, if it is well formed — the notice
     * fields only ({@code call}, {@code svc}, {@code f}, {@code mode}); a device acts on it only through
     * {@link #checkCancel} on {@link UserNotificationJsonBean#getSignedRing()} (a notice without it is ignored).
     */
    public static Optional<CallRingNoticeBean> cancelledOf(UserNotificationJsonBean n) {
        return noticeOf(n, NOTIFICATION_TYPE_CANCELLED);
    }

    /**
     * P2: the device's check of the caller's signed cancel, the SAME for the live notice ({@code signed_ring},
     * {@code ring.call}, {@code ring.f}) and the silent push ({@code data.ring}, {@code data.call_id}, {@code data.f}):
     * parsed strictly, verified under the ring context for {@code aud} (the audience of this device's PINNED network,
     * never taken from the message), shape-checked as rschat does ({@link #checkRing}), {@code k = cancelled}, bound to
     * the message's own {@code callId} and {@code f}, and addressed to {@code me} — which MUST be known: a device that
     * cannot say who it is never acts on a cancel (Iris F-9). Freshness is separate ({@link #isFreshPushRing}, the same
     * window as a pushed ring), and so is the device rule: the cancel stops only a ring of {@code callId} whose
     * VERIFIED caller is {@code r.getF()} and which is not newer than it ({@link #isNotOlderThanRing}); anything else is
     * discarded (operator Q5: a cancel never starts anything).
     *
     * @return the verified cancel; its caller is {@code getF()}
     * @throws CallProtocolException {@code bad_signature} on any failure (the device discards the cancel)
     */
    public static CallRingBean checkCancel(String signedJson, String aud, String callId, String f, String me, int toMax)
            throws CallProtocolException {
        if (signedJson == null || aud == null || callId == null || f == null) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "cancel absent");
        }
        if (me == null) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "cancel me unknown");
        }
        final CallRingBean r;
        try {
            r = io.takamaka.messages.call.CallJson.parse(signedJson, CallRingBean.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException ex) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "cancel parse");
        }
        io.takamaka.messages.call.CallSignatures.verify(r, CallConstants.T_RING, aud);
        try {
            checkRing(r, toMax);
        } catch (CallProtocolException ex) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "cancel shape");
        }
        if (!isCancelled(r)) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "cancel k");
        }
        if (!r.getCall().equals(callId) || !r.getF().equals(f)) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "cancel mismatch");
        }
        if (!r.getTo().contains(me)) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "cancel not for me");
        }
        return r;
    }

    /**
     * P2 (Iris §7.123 RA-5): whether a verified cancel may stop (or tombstone) a ring signed at {@code ringTs} — the
     * cancel's {@code ts} is not before it (both are the CALLER's clock). A ring whose signed {@code ts} the device
     * does not know ({@code null}: an older rschat's live ring) is matched. A cancel older than the ring is a replay or
     * a late copy of an earlier attempt and must not end a re-ring.
     */
    public static boolean isNotOlderThanRing(CallRingBean cancel, Long ringTs) {
        return cancel != null && cancel.getTs() != null && (ringTs == null || cancel.getTs() >= ringTs);
    }

    // ---- [0.4] the meet notice (spec §7.11, ruling M9 of 2026-11-03) ----
    /** [0.4] §7.11: the notification type of a meet notice ({@link NOTIFICATION_TYPES#CALL_MEET}). */
    public static final String NOTIFICATION_TYPE_MEET = NOTIFICATION_TYPES.CALL_MEET.name();
    /** [0.4] §7.11: {@code to} of a meet notice, 100 — split by rschat into per-recipient deliveries. */
    public static final int MEETNOTICE_TO_MAX = CallConstants.MEETNOTICE_TO_MAX;
    /** The {@code limit} of the {@code rate_limited} refusal of a meet notice whose {@code to} is longer than the cap. */
    public static final String LIMIT_MEETNOTICE_TO_MAX = "meetnotice_to_max";

    /** [0.4] An unsigned meet notice ({@code mode} is always {@code conference}); sign it with the owner's key. */
    public static CallMeetNoticeBean meetNotice(String callId, String svc, List<String> to, String nonce, String aud, long ts) {
        CallMeetNoticeBean n = new CallMeetNoticeBean();
        n.setAud(aud);
        n.setN(nonce);
        n.setTs(ts);
        n.setCall(callId);
        n.setSvc(svc);
        n.setTo(List.copyOf(to));
        n.setMode(CallConstants.MODE_CONFERENCE);
        return n;
    }

    /**
     * [0.4] §7.11 fields of a VERIFIED meet notice, the same checks as {@link #checkRing} with {@code mode} fixed to
     * {@code conference} (any other value is a malformed signed field, {@code bad_signature}) and the cap
     * {@code toMax} ({@link CallError#RATE_LIMITED} {@link #LIMIT_MEETNOTICE_TO_MAX}).
     */
    public static void checkMeetNotice(CallMeetNoticeBean n, int toMax) throws CallProtocolException {
        if (n == null || !isIdentity(n.getF()) || n.getN() == null || n.getN().isEmpty()
                || !CallBytes.isLowerHex(n.getCall(), CallConstants.HASH_LEN) || !isServiceId(n.getSvc())
                || !CallConstants.MODE_CONFERENCE.equals(n.getMode())) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "meetnotice fields");
        }
        List<String> to = n.getTo();
        if (to == null || to.isEmpty()) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "meetnotice to");
        }
        if (to.size() > toMax) {
            throw new CallProtocolException(CallError.RATE_LIMITED, LIMIT_MEETNOTICE_TO_MAX);
        }
        Set<String> seen = new HashSet<>();
        for (String id : to) {
            if (!isIdentity(id) || !seen.add(id)) {
                throw new CallProtocolException(CallError.BAD_SIGNATURE, "meetnotice to");
            }
        }
    }
}
