package io.takamaka.messages.call.sframe;

import io.takamaka.messages.call.CallBytes;
import io.takamaka.messages.call.CallCrypto;
import java.util.Arrays;

/**
 * SFrame encryption of one unit (RFC 9605 §4.4.3/§4.4.4) with the codec clear prefix of spec §9.2 (R31):
 *
 * <pre>
 * unit = clear prefix ‖ SFrame header ‖ ciphertext ‖ tag
 * AAD  = SFrame header ‖ clear prefix                         (the prefix is the RFC's "metadata")
 * </pre>
 *
 * Clear prefix lengths: Opus 1 (TOC), VP8 1 (byte 0). H.264 (per-NAL units behind the one-byte NAL header,
 * emulation-prevention escaping of the ciphertext) is NOT implemented here yet — see the C182 status report.
 *
 * <p>Key schedule and header for the cross-platform vectors only: no replay window, no key ring, no media path.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class SframeCipher {

    public static final int OPUS_PREFIX = 1;
    public static final int VP8_PREFIX = 1;

    private SframeCipher() {
    }

    /** RFC form: header ‖ AEAD(key, nonce, header ‖ metadata, plaintext). */
    public static byte[] encrypt(SframeKeys keys, long ctr, byte[] metadata, byte[] plaintext) {
        byte[] header = SframeHeader.encode(keys.kid(), ctr);
        byte[] aad = CallBytes.concat(header, metadata);
        return CallBytes.concat(header, CallCrypto.aesGcmSeal(keys.key(), keys.nonce(ctr), aad, plaintext));
    }

    /** RFC form decryption; the caller has selected {@code keys} by the header's KID. */
    public static byte[] decrypt(SframeKeys keys, byte[] metadata, byte[] sframeCiphertext) throws CallCrypto.AeadException {
        SframeHeader.Decoded h = SframeHeader.decode(sframeCiphertext, 0);
        if (h.kid() != keys.kid()) {
            throw new CallCrypto.AeadException("KID does not match the key");
        }
        byte[] header = Arrays.copyOf(sframeCiphertext, h.length());
        byte[] aad = CallBytes.concat(header, metadata);
        return CallCrypto.aesGcmOpen(keys.key(), keys.nonce(h.ctr()), aad,
                Arrays.copyOfRange(sframeCiphertext, h.length(), sframeCiphertext.length));
    }

    /** Seals an encoded frame whose first {@code prefixLen} bytes stay clear: returns the unit. */
    public static byte[] seal(SframeKeys keys, long ctr, byte[] frame, int prefixLen) {
        if (prefixLen < 0 || prefixLen > frame.length) {
            throw new IllegalArgumentException("prefix longer than the frame");
        }
        byte[] prefix = Arrays.copyOf(frame, prefixLen);
        byte[] payload = Arrays.copyOfRange(frame, prefixLen, frame.length);
        return CallBytes.concat(prefix, encrypt(keys, ctr, prefix, payload));
    }

    /** Opens a unit with a clear prefix of {@code prefixLen}: returns the encoded frame. Never passes plaintext on failure. */
    public static byte[] open(SframeKeys keys, byte[] unit, int prefixLen) throws CallCrypto.AeadException {
        if (prefixLen < 0 || prefixLen >= unit.length) {
            throw new CallCrypto.AeadException("unit shorter than its prefix");
        }
        byte[] prefix = Arrays.copyOf(unit, prefixLen);
        byte[] body;
        try {
            body = decrypt(keys, prefix, Arrays.copyOfRange(unit, prefixLen, unit.length));
        } catch (IllegalArgumentException ex) {
            throw new CallCrypto.AeadException("bad SFrame header");
        }
        return CallBytes.concat(prefix, body);
    }

    /** KID and CTR of a unit, read from the header after the clear prefix. */
    public static SframeHeader.Decoded header(byte[] unit, int prefixLen) {
        return SframeHeader.decode(unit, prefixLen);
    }
}
