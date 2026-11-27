package io.takamaka.messages.call.conv;

import io.takamaka.messages.call.CallBytes;
import io.takamaka.messages.call.CallConstants;
import io.takamaka.messages.call.CallCrypto;
import java.util.Arrays;

/**
 * Derivations of the optional conversation (spec §10.1, CS-5):
 *
 * <pre>
 * cseed = H(conv_seed)                                       (in the creation record)
 * title = HKDF(conv_seed, "tkm-call/v1/conv/title")  → rendered "call-" + hex(first 8 bytes)
 * salt  = HKDF(conv_seed, "tkm-call/v1/conv/salt")
 * key   = HKDF(conv_seed, "tkm-call/v1/conv/key")
 * </pre>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class ConvSeed {

    private ConvSeed() {
    }

    public static byte[] commitment(byte[] convSeed) {
        return CallCrypto.h(check(convSeed));
    }

    public static byte[] titleBytes(byte[] convSeed) {
        return CallCrypto.hkdf(check(convSeed), CallBytes.ascii(CallConstants.L_CONV_TITLE));
    }

    public static String title(byte[] convSeed) {
        return CallConstants.CONV_TITLE_PREFIX + CallBytes.hex(Arrays.copyOf(titleBytes(convSeed), 8));
    }

    public static byte[] salt(byte[] convSeed) {
        return CallCrypto.hkdf(check(convSeed), CallBytes.ascii(CallConstants.L_CONV_SALT));
    }

    public static byte[] key(byte[] convSeed) {
        return CallCrypto.hkdf(check(convSeed), CallBytes.ascii(CallConstants.L_CONV_KEY));
    }

    /** True if {@code convSeed} matches the commitment of the creation record (receivers ignore a mismatch). */
    public static boolean matches(byte[] convSeed, String cseedHex) {
        return CallCrypto.constantTimeEquals(commitment(convSeed), CallBytes.unhex(cseedHex, CallConstants.HASH_LEN));
    }

    private static byte[] check(byte[] s) {
        if (s == null || s.length != 32) {
            throw new IllegalArgumentException("conv_seed must be 32 bytes");
        }
        return s;
    }
}
