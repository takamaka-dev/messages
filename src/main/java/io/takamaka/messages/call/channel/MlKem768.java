package io.takamaka.messages.call.channel;

import java.security.SecureRandom;

/**
 * ML-KEM-768 (FIPS 203) as the call protocol needs it (spec §5.1). Bouncy Castle 1.70 has no ML-KEM; the binding
 * is {@link BcMlKem768} (BC 1.86 relocated to {@code io.takamaka.shaded.bc}).
 *
 * <p>The deterministic {@code _internal} forms of FIPS 203 (KeyGen from {@code d ‖ z}, Encaps from {@code m}) are
 * part of the interface so that a real binding reproduces the FIPS 203 KATs and our vectors are regenerable.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public interface MlKem768 {

    int EK_LEN = 1184;
    int DK_LEN = 2400;
    int CT_LEN = 1088;
    int SS_LEN = 32;
    int SEED_LEN = 32;

    /** Implementation name, written into the vectors ({@code test-double} or {@code fips203}). */
    String name();

    /** FIPS 203 ML-KEM.KeyGen_internal(d, z); d and z 32 bytes each. */
    KeyPair keyGen(byte[] d, byte[] z);

    /** FIPS 203 ML-KEM.Encaps_internal(ek, m); m 32 bytes. */
    Encapsulation encaps(byte[] ek, byte[] m);

    /** FIPS 203 ML-KEM.Decaps(dk, c) (implicit rejection: never throws on a bad ciphertext of the right size). */
    byte[] decaps(byte[] dk, byte[] ct);

    /**
     * FIPS 203 §7.2 encapsulation-key check: the type check (1184 bytes) and the modulus check (every coefficient
     * of the encoded vector below q). Spec amendment §4.3: the service and every client MUST run it on an announced
     * {@code mlkem} key and refuse the announcement on failure.
     */
    boolean checkEncapsulationKey(byte[] ek);

    default KeyPair keyGen(SecureRandom random) {
        byte[] d = new byte[SEED_LEN];
        byte[] z = new byte[SEED_LEN];
        random.nextBytes(d);
        random.nextBytes(z);
        return keyGen(d, z);
    }

    default Encapsulation encaps(byte[] ek, SecureRandom random) {
        byte[] m = new byte[SEED_LEN];
        random.nextBytes(m);
        return encaps(ek, m);
    }

    /** Encapsulation key (public, 1184 B) and decapsulation key (private, 2400 B). */
    record KeyPair(byte[] ek, byte[] dk) {

    }

    /** Shared secret (32 B) and ciphertext (1088 B). */
    record Encapsulation(byte[] sharedSecret, byte[] ciphertext) {

    }
}
