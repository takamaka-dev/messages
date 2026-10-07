package io.takamaka.messages.call;

import io.takamaka.messages.call.beans.CallAnnounceBean;
import io.takamaka.messages.call.channel.MlKem768;
import java.util.Map;
import java.util.Set;

/**
 * The checks every client (and the service) runs on an announcement (spec §4.3, Design §7.3 step 3): the
 * signature under {@code f} with type {@code announce} and audience {@code svc:<id>}; the call; the era it holds;
 * {@code f} eligible for the role; field encodings; the suite; no other identity announced the same {@code leg_id};
 * and — spec amendment of 2026-10-07 — the FIPS 203 §7.2 encapsulation-key check (type + modulus) on the
 * announced {@code mlkem} key. The roster is built only from announcements that pass.
 *
 * <p>Not covered here (state, not cryptography): {@code devices_per_identity}, {@code apol:"open"} audience
 * admission, the alert on an unexpected announcement under one's own identity.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class CallAnnouncements {

    private CallAnnouncements() {
    }

    /**
     * Verifies an announcement and returns its ann_hash (hex).
     *
     * @param speakers the held era's {@code inv}
     * @param listeners the held era's {@code alist} (may be empty; conference only)
     * @param knownLegs leg_id (hex) → identity of every announcement already accepted in this call
     */
    public static String verify(CallAnnounceBean a, String expectedAud, String callIdHex, String heldEraHashHex,
            Set<String> speakers, Set<String> listeners, Map<String, String> knownLegs, MlKem768 kem)
            throws CallProtocolException {
        CallSignatures.verify(a, CallConstants.T_ANNOUNCE, expectedAud);
        if (!callIdHex.equals(a.getCall())) {
            throw refused(CallError.BAD_SIGNATURE, "call mismatch");
        }
        if (!heldEraHashHex.equals(a.getEra())) {
            throw refused(CallError.ERA_MISMATCH, "not the era held");
        }
        if (CallConstants.ROLE_SPEAKER.equals(a.getRole())) {
            if (!speakers.contains(a.getF())) {
                throw refused(CallError.NOT_INVITED, "speaker not in inv");
            }
        } else if (CallConstants.ROLE_LISTENER.equals(a.getRole())) {
            if (!listeners.contains(a.getF())) {
                throw refused(CallError.NOT_INVITED, "listener not in alist");
            }
        } else {
            throw refused(CallError.BAD_SIGNATURE, "role");
        }
        if (!CallConstants.SUITE_HYBRID_1.equals(a.getSuite())) {
            throw refused(CallError.BAD_SIGNATURE, "suite");
        }
        if (!CallConstants.CAP_BASIC.equals(a.getCap()) && !CallConstants.CAP_BASIC_HQ.equals(a.getCap())) {
            throw refused(CallError.BAD_SIGNATURE, "cap");
        }
        if (!CallBytes.isLowerHex(a.getLeg(), CallConstants.LEG_ID_LEN)
                || !CallBytes.isLowerHex(a.getX25519(), 32)
                || !CallBytes.isLowerHex(a.getMlkem(), MlKem768.EK_LEN)) {
            throw refused(CallError.BAD_SIGNATURE, "encoding");
        }
        if (!kem.checkEncapsulationKey(CallBytes.unhex(a.getMlkem()))) {
            throw refused(CallError.BAD_SIGNATURE, "ML-KEM encapsulation key check failed");
        }
        String owner = knownLegs.get(a.getLeg());
        if (owner != null && !owner.equals(a.getF())) {
            throw refused(CallError.BAD_SIGNATURE, "leg_id announced by another identity");
        }
        return CallHashes.annHashHex(a);
    }

    private static CallProtocolException refused(CallError e, String reason) {
        return new CallProtocolException(e, reason);
    }
}
