package io.takamaka.messages.call;

import java.security.MessageDigest;
import java.util.Arrays;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.digests.SHA3Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;

/**
 * The primitives of spec §2.1, on Bouncy Castle 1.70 lightweight classes.
 *
 * <ul>
 * <li>{@code H} = SHA3-256;</li>
 * <li>MAC = HMAC-SHA-512;</li>
 * <li>KDF = HKDF-SHA-512 (RFC 5869), with Extract and Expand exposed separately because the spec uses both forms;
 * {@code HKDF(k, info)} = salt of 32 zero bytes, L = 32;</li>
 * <li>AEAD = AES-256-GCM, 96-bit nonce, 128-bit tag (ciphertext ‖ tag);</li>
 * <li>X25519 (RFC 7748).</li>
 * </ul>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class CallCrypto {

    private CallCrypto() {
    }

    /** SHA3-256 over the concatenation of the parts. */
    public static byte[] h(byte[]... parts) {
        SHA3Digest d = new SHA3Digest(256);
        for (byte[] p : parts) {
            d.update(p, 0, p.length);
        }
        byte[] out = new byte[32];
        d.doFinal(out, 0);
        return out;
    }

    public static byte[] hmacSha512(byte[] key, byte[]... parts) {
        HMac mac = new HMac(new SHA512Digest());
        mac.init(new KeyParameter(key));
        for (byte[] p : parts) {
            mac.update(p, 0, p.length);
        }
        byte[] out = new byte[64];
        mac.doFinal(out, 0);
        return out;
    }

    /** RFC 5869 HKDF-Extract with SHA-512: {@code PRK = HMAC(salt, ikm)}. An empty salt is HashLen zeros. */
    public static byte[] hkdfExtract(byte[] salt, byte[] ikm) {
        byte[] s = (salt == null || salt.length == 0) ? new byte[64] : salt;
        return hmacSha512(s, ikm);
    }

    /** RFC 5869 HKDF-Expand with SHA-512. */
    public static byte[] hkdfExpand(byte[] prk, byte[] info, int len) {
        if (len < 0 || len > 255 * 64) {
            throw new IllegalArgumentException("hkdf length out of range");
        }
        byte[] out = new byte[len];
        byte[] t = new byte[0];
        int pos = 0;
        for (int i = 1; pos < len; i++) {
            t = hmacSha512(prk, t, info, new byte[]{(byte) i});
            int n = Math.min(t.length, len - pos);
            System.arraycopy(t, 0, out, pos, n);
            pos += n;
        }
        return out;
    }

    /** {@code HKDF(ikm, salt, info, L)} of spec §2.1. */
    public static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int len) {
        return hkdfExpand(hkdfExtract(salt, ikm), info, len);
    }

    /** {@code HKDF(k, info)} of spec §2.1: salt = 32 zero bytes, L = 32. */
    public static byte[] hkdf(byte[] k, byte[] info) {
        return hkdf(k, new byte[32], info, 32);
    }

    /** AES-256-GCM seal; returns ciphertext ‖ tag (16 bytes). */
    public static byte[] aesGcmSeal(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext) {
        checkAead(key, nonce);
        GCMBlockCipher gcm = new GCMBlockCipher(new AESEngine());
        gcm.init(true, new AEADParameters(new KeyParameter(key), 128, nonce, aad));
        byte[] out = new byte[gcm.getOutputSize(plaintext.length)];
        int n = gcm.processBytes(plaintext, 0, plaintext.length, out, 0);
        try {
            gcm.doFinal(out, n);
        } catch (InvalidCipherTextException ex) {
            throw new IllegalStateException(ex);
        }
        return out;
    }

    /** AES-256-GCM open of ciphertext ‖ tag; throws {@link AeadException} when the tag does not verify. */
    public static byte[] aesGcmOpen(byte[] key, byte[] nonce, byte[] aad, byte[] ciphertextAndTag) throws AeadException {
        checkAead(key, nonce);
        if (ciphertextAndTag.length < CallConstants.GCM_TAG_LEN) {
            throw new AeadException("ciphertext shorter than the tag");
        }
        GCMBlockCipher gcm = new GCMBlockCipher(new AESEngine());
        gcm.init(false, new AEADParameters(new KeyParameter(key), 128, nonce, aad));
        byte[] out = new byte[gcm.getOutputSize(ciphertextAndTag.length)];
        int n = gcm.processBytes(ciphertextAndTag, 0, ciphertextAndTag.length, out, 0);
        try {
            n += gcm.doFinal(out, n);
        } catch (InvalidCipherTextException ex) {
            throw new AeadException("tag mismatch");
        }
        return n == out.length ? out : Arrays.copyOf(out, n);
    }

    private static void checkAead(byte[] key, byte[] nonce) {
        if (key.length != 32) {
            throw new IllegalArgumentException("AES-256-GCM key must be 32 bytes");
        }
        if (nonce.length != CallConstants.GCM_NONCE_LEN) {
            throw new IllegalArgumentException("GCM nonce must be 12 bytes");
        }
    }

    /** X25519 public key of a 32-byte private key. */
    public static byte[] x25519Public(byte[] priv) {
        return new X25519PrivateKeyParameters(priv, 0).generatePublicKey().getEncoded();
    }

    /** X25519(priv, pub); throws if the result is all zero (RFC 7748 §6.1 check, done by BC). */
    public static byte[] x25519(byte[] priv, byte[] pub) {
        byte[] out = new byte[32];
        new X25519PrivateKeyParameters(priv, 0).generateSecret(new X25519PublicKeyParameters(pub, 0), out, 0);
        return out;
    }

    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }

    /** Thrown when an AEAD tag does not verify. */
    public static final class AeadException extends Exception {

        public AeadException(String m) {
            super(m);
        }
    }
}
