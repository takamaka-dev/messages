package io.takamaka.messages.call;

import io.takamaka.messages.call.epoch.EpochSchedule;
import io.takamaka.messages.call.service.CallTextBean;
import java.util.Arrays;

/**
 * The in-call text AEAD of spec §10.2 ([0.2], J-3 ruled 2026-10-08) — the Java reference.
 *
 * <pre>
 * text_key_e = HKDF(broadcast_e, "tkm-call/v1/text" ‖ leg_id)                          (§6.1, the SENDING leg)
 * nonce      = BE32(e) ‖ BE64(ctr)                                                      (12 bytes)
 * AAD        = ASCII("tkm-call/v1/text") ‖ 0x00 ‖ call_id ‖ leg_id ‖ BE32(e)           (raw bytes: 16+1+32+8+4 = 61)
 * ct         = AES-256-GCM(text_key_e, nonce, AAD, UTF-8(text)) = ciphertext ‖ tag     (wire: lowercase hex)
 * </pre>
 *
 * {@code ctr} is unique per (leg, epoch) and strictly increasing (the sender starts at 0 in every epoch). The receiver
 * rules (current + retained epochs, a repeated {@code ctr} refused, a {@code ctr} counted as used only AFTER it opened,
 * the AAD rebuilt from the call the stream belongs to and the leg and epoch the text was relayed under) are state and
 * live in the client state machine; this class is the pure cryptography and the wire-shape check.
 *
 * <p>The plaintext is free text (any UTF-8): the text object is NOT a signed object, so §2.2's ASCII rule does not
 * apply to it. Nothing here logs.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class CallTexts {

    private CallTexts() {
    }

    /** {@code ASCII("tkm-call/v1/text") ‖ 0x00 ‖ call_id ‖ leg_id ‖ BE32(e)}; call_id 32 bytes, leg_id 8 bytes. */
    public static byte[] aad(byte[] callId, byte[] legId, long epoch) {
        if (callId == null || callId.length != CallConstants.HASH_LEN) {
            throw new IllegalArgumentException("call_id must be 32 bytes");
        }
        if (legId == null || legId.length != CallConstants.LEG_ID_LEN) {
            throw new IllegalArgumentException("leg_id must be 8 bytes");
        }
        return CallBytes.concat(CallBytes.ascii(CallConstants.AAD_TEXT), new byte[]{0}, callId, legId,
                CallBytes.be32(epoch));
    }

    /** {@code BE32(e) ‖ BE64(ctr)}. */
    public static byte[] nonce(long epoch, long ctr) {
        return CallBytes.concat(CallBytes.be32(epoch), CallBytes.be64(ctr));
    }

    /** AES-256-GCM seal under the sending leg's {@code text_key_e}; returns ciphertext ‖ tag. */
    public static byte[] seal(byte[] textKey, byte[] callId, byte[] legId, long epoch, long ctr, byte[] plaintext) {
        return CallCrypto.aesGcmSeal(textKey, nonce(epoch, ctr), aad(callId, legId, epoch), plaintext);
    }

    /** AES-256-GCM open; throws when the tag — and so the call, leg, epoch or ctr — does not match. */
    public static byte[] open(byte[] textKey, byte[] callId, byte[] legId, long epoch, long ctr, byte[] ct)
            throws CallCrypto.AeadException {
        return CallCrypto.aesGcmOpen(textKey, nonce(epoch, ctr), aad(callId, legId, epoch), ct);
    }

    /**
     * Seals a text for the wire: derives {@code text_key_e} of {@code legIdHex} from {@code broadcast_e}, seals, zeroises
     * the key and returns the §10.2 object.
     */
    public static CallTextBean build(byte[] broadcast, String callIdHex, String legIdHex, long epoch, long ctr,
            byte[] plaintext) {
        byte[] legId = CallBytes.unhex(legIdHex, CallConstants.LEG_ID_LEN);
        byte[] key = EpochSchedule.textKeyFromBroadcast(broadcast, legId);
        try {
            byte[] ct = seal(key, CallBytes.unhex(callIdHex, CallConstants.HASH_LEN), legId, epoch, ctr, plaintext);
            CallTextBean t = new CallTextBean(CallTextBean.X_TEXT, legIdHex, epoch, ctr, CallBytes.hex(ct));
            if (CallJson.toWire(t).length() > CallConstants.TEXT_MAX_BYTES) {
                throw new IllegalArgumentException("text object over " + CallConstants.TEXT_MAX_BYTES + " bytes");
            }
            return t;
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * Opens a relayed text under {@code broadcast_e} of the epoch it names, with the AAD rebuilt from
     * {@code callIdHex} (the call the receiving stream belongs to) and the object's own leg and epoch.
     */
    public static byte[] open(byte[] broadcast, String callIdHex, CallTextBean t) throws CallCrypto.AeadException {
        byte[] legId = CallBytes.unhex(t.getLeg(), CallConstants.LEG_ID_LEN);
        byte[] key = EpochSchedule.textKeyFromBroadcast(broadcast, legId);
        try {
            return open(key, CallBytes.unhex(callIdHex, CallConstants.HASH_LEN), legId, t.getEpoch(), t.getCtr(),
                    CallBytes.unhex(t.getCt()));
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /**
     * The wire shape of a §10.2 object: {@code x = "text"}, an 8-byte hex leg, {@code 0 ≤ epoch ≤ 2^32−1},
     * {@code 0 ≤ ctr ≤ 2^53−1}, a lowercase-hex {@code ct} at least one tag long, at most 4 kB on the wire.
     */
    public static boolean wellFormed(CallTextBean t) {
        return t != null && CallTextBean.X_TEXT.equals(t.getX()) && CallBytes.isLowerHex(t.getLeg(), CallConstants.LEG_ID_LEN)
                && t.getEpoch() != null && t.getEpoch() >= 0 && t.getEpoch() <= 0xFFFFFFFFL
                && t.getCtr() != null && t.getCtr() >= 0 && t.getCtr() <= 9_007_199_254_740_991L
                && t.getCt() != null && t.getCt().length() >= 2 * CallConstants.GCM_TAG_LEN && t.getCt().length() % 2 == 0
                && t.getCt().matches("[0-9a-f]+") && CallJson.toWire(t).length() <= CallConstants.TEXT_MAX_BYTES;
    }
}
