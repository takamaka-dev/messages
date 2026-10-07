package io.takamaka.messages.call.epoch;

import io.takamaka.messages.call.CallBytes;
import io.takamaka.messages.call.CallConstants;
import io.takamaka.messages.call.CallCrypto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The epoch schedule of spec §6.1.
 *
 * <pre>
 * epoch_secret_0 = commit_secret_0                                                    (initial commit)
 * epoch_secret_e = HKDF-Expand(HKDF-Extract(salt_e, epoch_secret_e-1 ‖ commit_secret_e), "tkm-call/v1/epoch", 32)
 * salt_e         = H(roster_hash_e ‖ era_hash ‖ BE32(e))
 * step (join):     epoch_secret_e = HKDF(epoch_secret_e-1, "tkm-call/v1/step" ‖ ann_hash(joiner) ‖ roster_hash_e)
 * roster_hash_e  = H(sorted ann_hash of every roster leg ‖ era_hash)
 * broadcast_e    = HKDF(epoch_secret_e, "tkm-call/v1/broadcast" ‖ era_hash)
 * sender_base_e  = HKDF(broadcast_e, "tkm-call/v1/sender" ‖ leg_id)          (A-15 RULED 2026-10-07)
 * text_key_e     = HKDF(broadcast_e, "tkm-call/v1/text" ‖ leg_id)            (A-15 RULED 2026-10-07)
 * code_e         = first 40 bits of HKDF(epoch_secret_e, "tkm-call/v1/code") → 12 decimal digits
 * confirm_e      = HMAC-SHA-512(epoch_secret_e, "tkm-call/v1/confirm" ‖ H(canonical(commit header)))[0..16)
 * </pre>
 *
 * All inputs are raw octets (hex decoded); ann_hash sorting is unsigned-lexicographic on the 32 raw bytes, which
 * equals the order of their lowercase hex strings.
 *
 * <p>A-15 (RULED): speakers derive {@code broadcast_e} first and every leg's frame and text keys from it, so a
 * listener holding only {@code broadcast_e} derives every speaker's keys but never the epoch secret (Design §7.8.4).
 * The listener text key of §10.2 is simply the sending leg's {@code text_key_e}.
 *
 * <p><b>code_e</b> (A-9 RULED): the first 5 bytes of {@code HKDF(epoch_secret_e, "tkm-call/v1/code")} as a
 * big-endian unsigned integer, {@code mod 10^12}, rendered as 12 zero-padded decimal digits.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class EpochSchedule {

    private EpochSchedule() {
    }

    /** Sorts the ann_hashes (unsigned byte order) and returns a new list. */
    public static List<byte[]> sorted(List<byte[]> annHashes) {
        List<byte[]> s = new ArrayList<>(annHashes);
        s.sort(CallBytes::compareUnsigned);
        return s;
    }

    /** Sorts hex ann_hashes the same way (lowercase hex order equals byte order). */
    public static List<String> sortedHex(List<String> annHashesHex) {
        List<String> s = new ArrayList<>(annHashesHex);
        s.sort(String::compareTo);
        return s;
    }

    public static byte[] rosterHash(List<byte[]> annHashes, byte[] eraHash) {
        List<byte[]> s = sorted(annHashes);
        byte[][] parts = new byte[s.size() + 1][];
        for (int i = 0; i < s.size(); i++) {
            if (s.get(i).length != CallConstants.HASH_LEN) {
                throw new IllegalArgumentException("ann_hash must be 32 bytes");
            }
            if (i > 0 && Arrays.equals(s.get(i), s.get(i - 1))) {
                throw new IllegalArgumentException("duplicate ann_hash in roster");
            }
            parts[i] = s.get(i);
        }
        parts[s.size()] = eraHash;
        return CallCrypto.h(parts);
    }

    public static byte[] rosterHashHex(List<String> annHashesHex, String eraHashHex) {
        List<byte[]> l = new ArrayList<>();
        for (String h : annHashesHex) {
            l.add(CallBytes.unhex(h, CallConstants.HASH_LEN));
        }
        return rosterHash(l, CallBytes.unhex(eraHashHex, CallConstants.HASH_LEN));
    }

    public static byte[] saltE(byte[] rosterHash, byte[] eraHash, long e) {
        return CallCrypto.h(rosterHash, eraHash, CallBytes.be32(e));
    }

    /** Fresh commit: epoch_secret_e from the previous secret and the commit secret. */
    public static byte[] fresh(byte[] prevEpochSecret, byte[] commitSecret, byte[] saltE) {
        return CallCrypto.hkdfExpand(CallCrypto.hkdfExtract(saltE, CallBytes.concat(prevEpochSecret, commitSecret)),
                CallBytes.ascii(CallConstants.L_EPOCH), 32);
    }

    /** Step on a sequenced join. */
    public static byte[] step(byte[] prevEpochSecret, byte[] joinerAnnHash, byte[] rosterHash) {
        return CallCrypto.hkdf(prevEpochSecret,
                CallBytes.concat(CallBytes.ascii(CallConstants.L_STEP), joinerAnnHash, rosterHash));
    }

    public static byte[] broadcast(byte[] epochSecret, byte[] eraHash) {
        return CallCrypto.hkdf(epochSecret, CallBytes.concat(CallBytes.ascii(CallConstants.L_BROADCAST), eraHash));
    }

    /** sender_base_e of a leg from broadcast_e (what a listener can compute). */
    public static byte[] senderBaseFromBroadcast(byte[] broadcast, byte[] legId) {
        requireLeg(legId);
        return CallCrypto.hkdf(broadcast, CallBytes.concat(CallBytes.ascii(CallConstants.L_SENDER), legId));
    }

    /** text_key_e of a leg from broadcast_e. */
    public static byte[] textKeyFromBroadcast(byte[] broadcast, byte[] legId) {
        requireLeg(legId);
        return CallCrypto.hkdf(broadcast, CallBytes.concat(CallBytes.ascii(CallConstants.L_TEXT), legId));
    }

    /** sender_base_e of a leg, for a speaker holding the epoch secret (broadcast_e derived first). */
    public static byte[] senderBase(byte[] epochSecret, byte[] eraHash, byte[] legId) {
        return senderBaseFromBroadcast(broadcast(epochSecret, eraHash), legId);
    }

    /** text_key_e of a leg, for a speaker holding the epoch secret (broadcast_e derived first). */
    public static byte[] textKey(byte[] epochSecret, byte[] eraHash, byte[] legId) {
        return textKeyFromBroadcast(broadcast(epochSecret, eraHash), legId);
    }

    /** The full 32-byte HKDF output the code is taken from. */
    public static byte[] codeBytes(byte[] epochSecret) {
        return CallCrypto.hkdf(epochSecret, CallBytes.ascii(CallConstants.L_CODE));
    }

    /** The 12-digit comparison code (see the class note on A-9). */
    public static String code(byte[] epochSecret) {
        byte[] b = codeBytes(epochSecret);
        long x = 0;
        for (int i = 0; i < 5; i++) {
            x = (x << 8) | (b[i] & 0xffL);
        }
        return String.format("%012d", x % 1_000_000_000_000L);
    }

    /** confirm_e over the canonical commit header (the ASCII JSON string). */
    public static byte[] confirm(byte[] epochSecret, String canonicalCommitHeader) {
        byte[] mac = CallCrypto.hmacSha512(epochSecret, CallBytes.ascii(CallConstants.L_CONFIRM),
                CallCrypto.h(CallBytes.ascii(canonicalCommitHeader)));
        return Arrays.copyOf(mac, CallConstants.CONFIRM_LEN);
    }

    private static void requireLeg(byte[] legId) {
        if (legId.length != CallConstants.LEG_ID_LEN) {
            throw new IllegalArgumentException("leg_id must be 8 bytes");
        }
    }
}
