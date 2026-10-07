package io.takamaka.messages.call.sframe;

import io.takamaka.messages.call.CallBytes;
import io.takamaka.messages.call.CallCrypto;
import java.nio.charset.StandardCharsets;

/**
 * SFrame key derivation, RFC 9605 §4.4.2, for cipher suite 5 {@code AES_256_GCM_SHA512} only (spec §9.2):
 *
 * <pre>
 * sframe_secret = HKDF-Extract("", base_key)                                  (SHA-512)
 * sframe_key    = HKDF-Expand(sframe_secret, "SFrame 1.0 Secret key "  ‖ BE64(KID) ‖ BE16(suite), 32)
 * sframe_salt   = HKDF-Expand(sframe_secret, "SFrame 1.0 Secret salt " ‖ BE64(KID) ‖ BE16(suite), 12)
 * </pre>
 *
 * In our protocol {@code base_key = sender_base_e} of the sending leg (spec §6.1).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class SframeKeys {

    /** RFC 9605 §8.1 code point of AES_256_GCM_SHA512. */
    public static final int SUITE_AES_256_GCM_SHA512 = 0x0005;
    public static final int NK = 32;
    public static final int NN = 12;
    public static final int NT = 16;

    private final long kid;
    private final byte[] secret;
    private final byte[] keyLabel;
    private final byte[] saltLabel;
    private final byte[] key;
    private final byte[] salt;

    private SframeKeys(long kid, byte[] baseKey) {
        this.kid = kid;
        this.secret = CallCrypto.hkdfExtract(new byte[0], baseKey);
        this.keyLabel = label("SFrame 1.0 Secret key ", kid);
        this.saltLabel = label("SFrame 1.0 Secret salt ", kid);
        this.key = CallCrypto.hkdfExpand(secret, keyLabel, NK);
        this.salt = CallCrypto.hkdfExpand(secret, saltLabel, NN);
    }

    /** Derives the key and salt of {@code kid} from {@code baseKey}. */
    public static SframeKeys derive(long kid, byte[] baseKey) {
        return new SframeKeys(kid, baseKey);
    }

    static byte[] label(String prefix, long kid) {
        byte[] k = new byte[8];
        for (int i = 0; i < 8; i++) {
            k[i] = (byte) (kid >>> (56 - 8 * i));
        }
        return CallBytes.concat(prefix.getBytes(StandardCharsets.US_ASCII), k, CallBytes.be16(SUITE_AES_256_GCM_SHA512));
    }

    /** nonce = sframe_salt XOR BE96(CTR) (RFC 9605 §4.4.3). */
    public byte[] nonce(long ctr) {
        byte[] n = salt.clone();
        for (int i = 0; i < 8; i++) {
            n[NN - 1 - i] ^= (byte) (ctr >>> (8 * i));
        }
        return n;
    }

    public long kid() {
        return kid;
    }

    public byte[] secret() {
        return secret.clone();
    }

    public byte[] keyLabel() {
        return keyLabel.clone();
    }

    public byte[] saltLabel() {
        return saltLabel.clone();
    }

    public byte[] key() {
        return key.clone();
    }

    public byte[] salt() {
        return salt.clone();
    }
}
