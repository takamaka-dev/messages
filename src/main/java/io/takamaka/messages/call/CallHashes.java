package io.takamaka.messages.call;

import io.takamaka.messages.call.beans.CallAnnounceBean;
import io.takamaka.messages.call.beans.CallCreateBean;
import io.takamaka.messages.call.beans.CallEraBean;
import io.takamaka.messages.call.beans.CallSignedObject;

/**
 * The record hashes of spec §4:
 *
 * <pre>
 * call_id     = H(ASCII("tkm-call/v1/id")  ‖ UTF-8(canonical(create without sg)))
 * era_hash(0) = H(ASCII("tkm-call/v1/era") ‖ UTF-8(canonical(create without sg)))
 * era_hash(n) = H(ASCII("tkm-call/v1/era") ‖ UTF-8(canonical(era record n without sg)))
 * ann_hash    = H(ASCII("tkm-call/v1/ann") ‖ UTF-8(canonical(announcement without sg)))
 * </pre>
 *
 * No separator byte between the label and the canonical JSON (as written in the spec). All results are 32 bytes;
 * the {@code ...Hex} forms are lowercase hex as on the wire.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class CallHashes {

    private CallHashes() {
    }

    static byte[] labelled(String label, CallSignedObject obj) {
        return CallCrypto.h(CallBytes.ascii(label), CallBytes.ascii(CallSignatures.canonical(obj)));
    }

    public static byte[] callId(CallCreateBean create) {
        return labelled(CallConstants.H_ID, create);
    }

    public static String callIdHex(CallCreateBean create) {
        return CallBytes.hex(callId(create));
    }

    /** era_hash(0), of the creation record. */
    public static byte[] eraHash(CallCreateBean create) {
        return labelled(CallConstants.H_ERA, create);
    }

    /** era_hash(n), n &ge; 1. */
    public static byte[] eraHash(CallEraBean era) {
        return labelled(CallConstants.H_ERA, era);
    }

    public static String eraHashHex(CallSignedObject createOrEra) {
        if (createOrEra instanceof CallCreateBean c) {
            return CallBytes.hex(eraHash(c));
        }
        if (createOrEra instanceof CallEraBean e) {
            return CallBytes.hex(eraHash(e));
        }
        throw new IllegalArgumentException("not a create or era record");
    }

    public static byte[] annHash(CallAnnounceBean ann) {
        return labelled(CallConstants.H_ANN, ann);
    }

    public static String annHashHex(CallAnnounceBean ann) {
        return CallBytes.hex(annHash(ann));
    }
}
