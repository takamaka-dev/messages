package io.takamaka.messages.call.rschat;

import io.takamaka.messages.call.CallBytes;
import io.takamaka.messages.call.CallConstants;
import io.takamaka.messages.call.CallError;
import io.takamaka.messages.call.CallProtocolException;
import io.takamaka.messages.call.beans.CallLookupBean;
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
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class CallRschat {

    private CallRschat() {
    }

    /** The notification type of a ring ({@link NOTIFICATION_TYPES#CALL_RING}). */
    public static final String NOTIFICATION_TYPE = NOTIFICATION_TYPES.CALL_RING.name();

    /** Initial limit on {@code to} (the invitation-list size, spec §8.2 / {@code inv_max}). */
    public static final int RING_TO_MAX = 100;

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
     * {@code to} 1..{@code toMax} distinct identities, {@code f} an identity, {@code n} present.
     */
    public static void checkRing(CallRingBean r, int toMax) throws CallProtocolException {
        if (r == null || !isIdentity(r.getF()) || r.getN() == null || r.getN().isEmpty()
                || !CallBytes.isLowerHex(r.getCall(), CallConstants.HASH_LEN) || !isServiceId(r.getSvc())
                || !(CallConstants.MODE_STANDARD.equals(r.getMode()) || CallConstants.MODE_CONFERENCE.equals(r.getMode()))) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "ring fields");
        }
        List<String> to = r.getTo();
        if (to == null || to.isEmpty() || to.size() > toMax) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "ring to");
        }
        Set<String> seen = new HashSet<>();
        for (String id : to) {
            if (!isIdentity(id) || !seen.add(id)) {
                throw new CallProtocolException(CallError.BAD_SIGNATURE, "ring to");
            }
        }
    }

    /** §8.3 fields of a VERIFIED lookup: one identity in {@code id}, {@code n} present. */
    public static void checkLookup(CallLookupBean l) throws CallProtocolException {
        if (l == null || !isIdentity(l.getId()) || l.getN() == null || l.getN().isEmpty()) {
            throw new CallProtocolException(CallError.BAD_SIGNATURE, "lookup fields");
        }
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
        if (n == null || !NOTIFICATION_TYPE.equals(n.getNotificationType())) {
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
}
