package io.takamaka.messages.call.channel;

import io.takamaka.shaded.bc.crypto.AsymmetricCipherKeyPair;
import io.takamaka.shaded.bc.crypto.SecretWithEncapsulation;
import io.takamaka.shaded.bc.crypto.generators.MLKEMKeyPairGenerator;
import io.takamaka.shaded.bc.crypto.kems.MLKEMExtractor;
import io.takamaka.shaded.bc.crypto.kems.MLKEMGenerator;
import io.takamaka.shaded.bc.crypto.params.MLKEMKeyGenerationParameters;
import io.takamaka.shaded.bc.crypto.params.MLKEMParameters;
import io.takamaka.shaded.bc.crypto.params.MLKEMPrivateKeyParameters;
import io.takamaka.shaded.bc.crypto.params.MLKEMPublicKeyParameters;
import java.security.SecureRandom;

/**
 * ML-KEM-768 (FIPS 203) on Bouncy Castle 1.86, relocated to {@code io.takamaka.shaded.bc}
 * ({@code io.takamaka.crypto:tkm-mlkem-shaded}). Lightweight API only; the relocated JCA provider is never
 * registered.
 *
 * <ul>
 * <li>KeyGen_internal(d, z): {@code new MLKEMPrivateKeyParameters(ml_kem_768, d ‖ z)}; ek = its public key, dk =
 * the 2400-byte expanded encoding.</li>
 * <li>Encaps_internal(ek, m): {@code MLKEMGenerator.internalGenerateEncapsulated(pk, m)}.</li>
 * <li>Decaps(dk, c): {@code MLKEMExtractor} (implicit rejection).</li>
 * <li>Encapsulation-key check (FIPS 203 §7.2, type + modulus): BC runs the modulus check when an
 * {@code MLKEMPublicKeyParameters} is built from an encoding and throws on failure.</li>
 * </ul>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class BcMlKem768 implements MlKem768 {

    public static final String NAME = "fips203";
    private static final MLKEMParameters P = MLKEMParameters.ml_kem_768;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public KeyPair keyGen(byte[] d, byte[] z) {
        check(d, SEED_LEN, "d");
        check(z, SEED_LEN, "z");
        byte[] seed = new byte[64];
        System.arraycopy(d, 0, seed, 0, 32);
        System.arraycopy(z, 0, seed, 32, 32);
        return of(new MLKEMPrivateKeyParameters(P, seed));
    }

    @Override
    public KeyPair keyGen(SecureRandom random) {
        MLKEMKeyPairGenerator g = new MLKEMKeyPairGenerator();
        g.init(new MLKEMKeyGenerationParameters(random, P));
        AsymmetricCipherKeyPair kp = g.generateKeyPair();
        return of((MLKEMPrivateKeyParameters) kp.getPrivate());
    }

    private static KeyPair of(MLKEMPrivateKeyParameters priv) {
        byte[] ek = priv.getPublicKeyParameters().getEncoded();
        byte[] dk = priv.getParametersWithFormat(MLKEMPrivateKeyParameters.EXPANDED_KEY).getEncoded();
        check(ek, EK_LEN, "ek");
        check(dk, DK_LEN, "dk");
        return new KeyPair(ek, dk);
    }

    @Override
    public Encapsulation encaps(byte[] ek, byte[] m) {
        check(m, SEED_LEN, "m");
        SecretWithEncapsulation s = MLKEMGenerator.internalGenerateEncapsulated(publicKey(ek), m);
        return new Encapsulation(s.getSecret(), s.getEncapsulation());
    }

    @Override
    public Encapsulation encaps(byte[] ek, SecureRandom random) {
        SecretWithEncapsulation s = new MLKEMGenerator(random).generateEncapsulated(publicKey(ek));
        return new Encapsulation(s.getSecret(), s.getEncapsulation());
    }

    @Override
    public byte[] decaps(byte[] dk, byte[] ct) {
        check(dk, DK_LEN, "dk");
        check(ct, CT_LEN, "ct");
        return new MLKEMExtractor(new MLKEMPrivateKeyParameters(P, dk)).extractSecret(ct);
    }

    @Override
    public boolean checkEncapsulationKey(byte[] ek) {
        try {
            publicKey(ek);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static MLKEMPublicKeyParameters publicKey(byte[] ek) {
        check(ek, EK_LEN, "ek");
        return new MLKEMPublicKeyParameters(P, ek);
    }

    private static void check(byte[] b, int len, String what) {
        if (b == null || b.length != len) {
            throw new IllegalArgumentException(what + " must be " + len + " bytes");
        }
    }
}
