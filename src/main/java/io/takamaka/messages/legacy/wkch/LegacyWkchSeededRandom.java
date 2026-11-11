package io.takamaka.messages.legacy.wkch;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * LEGACY (F340, decrypt-only — see {@code package-info}). Port of {@code TkmSeededRandom} from
 * takamaka-sdk-wrap {@code 1a1b245} {@code lib/crypto/tkm_seeded_random.dart}.
 *
 * <p>Each {@link #nextBytes(int)} call returns PBKDF2-HMAC-SHA512(password = seed on the first call,
 * {@code seed + counter} (decimal) from the second call on, salt = scope, iterations = keyNumber,
 * {@code count} bytes), and increments the counter. Byte-identical to {@code wallet-core}
 * {@code SeededRandom.nextBytes} for ASCII seeds (the Takamaka seed is Base64URL text).</p>
 *
 * <p>{@link #nextBigInteger(int)} is the SDK's override (NOT {@code java.util.Random}'s): ONE
 * {@code nextBytes((bitLength + 7) / 8)} call read big-endian as an unsigned integer, shifted right by
 * {@code 8 - bitLength % 8} when the length is not a whole number of bytes. PointyCastle's RSA generator
 * draws every candidate through it.</p>
 *
 * <p>Deliberately NOT a {@link java.security.SecureRandom}: it must never be handed to anything else.</p>
 *
 * <p>Sunset: remove with the package when no invite wrapped to a WKCH key remains on any server.</p>
 */
final class LegacyWkchSeededRandom {

    private final String walletSeed;
    private final String scope;
    private final int keyNumber;
    private long rsaIterationsInSameInstance = 0L;

    LegacyWkchSeededRandom(String walletSeed, String scope, int keyNumber) {
        this.walletSeed = walletSeed;
        this.scope = scope;
        this.keyNumber = keyNumber;
    }

    /** How many {@link #nextBytes(int)} calls have been made (for tests). */
    long calls() {
        return rsaIterationsInSameInstance;
    }

    BigInteger nextBigInteger(int bitLength) {
        byte[] bytes = nextBytes((bitLength + 7) / 8);
        BigInteger result = new BigInteger(1, bytes);
        if (bitLength % 8 != 0) {
            result = result.shiftRight(8 - (bitLength % 8));
        }
        return result;
    }

    byte[] nextBytes(int count) {
        String password = walletSeed;
        if (rsaIterationsInSameInstance > 0) {
            password = walletSeed + rsaIterationsInSameInstance;
        }
        byte[] bytes = pbkdf2Sha512(password, scope, keyNumber, count);
        rsaIterationsInSameInstance++;
        return bytes;
    }

    private static byte[] pbkdf2Sha512(String password, String salt, int iterations, int count) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(),
                salt.getBytes(StandardCharsets.UTF_8), iterations, 8 * count);
        try {
            byte[] out = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).getEncoded();
            if (out.length != count) {
                throw new IllegalStateException("PBKDF2 returned " + out.length + " bytes, wanted " + count);
            }
            return out;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("legacy WKCH seeded random: PBKDF2WithHmacSHA512 unavailable", ex);
        } finally {
            spec.clearPassword();
        }
    }
}
