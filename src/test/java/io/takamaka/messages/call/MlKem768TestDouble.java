package io.takamaka.messages.call;

import io.takamaka.messages.call.channel.MlKem768;
import java.util.Arrays;
import org.bouncycastle.crypto.digests.SHAKEDigest;

/**
 * A DETERMINISTIC, INSECURE stand-in for ML-KEM-768 with the real sizes (ek 1184, dk 2400, ct 1088, ss 32), so
 * for unit tests of the KEM plumbing. Anyone holding {@code ek} can recover {@code m} from {@code ct}: never use it
 * outside tests. The published vectors use the real binding ({@code BcMlKem768}) since 2026-10-07.
 *
 * <p>Definition (to be reproduced by the Dart port byte for byte; L(x) = ASCII("tkm-call/v1/test-double/mlkem/" + x),
 * H = SHA3-256, XOF = SHAKE256):
 * <pre>
 * KeyGen(d, z):  s  = H(L("s") ‖ d ‖ z)
 *                ek = XOF(L("ek") ‖ s, 1184)
 *                dk = s ‖ 0^1120 ‖ ek ‖ H(ek) ‖ z                        (32+1120+1184+32+32 = 2400)
 * Encaps(ek, m): hek = H(ek)
 *                ct  = (m XOR H(L("pad") ‖ hek)) ‖ XOF(L("ct") ‖ m ‖ hek, 1056)
 *                ss  = H(L("ss") ‖ m ‖ H(ct))
 * Decaps(dk, ct): ek = dk[1152..2336), hek = dk[2336..2368), z = dk[2368..2400)
 *                m  = ct[0..32) XOR H(L("pad") ‖ hek)
 *                if Encaps(ek, m).ct == ct: ss  else: H(L("reject") ‖ z ‖ ct)
 * </pre>
 */
public final class MlKem768TestDouble implements MlKem768 {

    public static final String NAME = "test-double";
    static final String L = "tkm-call/v1/test-double/mlkem/";

    @Override
    public String name() {
        return NAME;
    }

    static byte[] l(String x) {
        return CallBytes.ascii(L + x);
    }

    static byte[] xof(int len, byte[]... parts) {
        SHAKEDigest d = new SHAKEDigest(256);
        for (byte[] p : parts) {
            d.update(p, 0, p.length);
        }
        byte[] out = new byte[len];
        d.doFinal(out, 0, len);
        return out;
    }

    @Override
    public KeyPair keyGen(byte[] d, byte[] z) {
        check(d, SEED_LEN);
        check(z, SEED_LEN);
        byte[] s = CallCrypto.h(l("s"), d, z);
        byte[] ek = xof(EK_LEN, l("ek"), s);
        byte[] dk = CallBytes.concat(s, new byte[1120], ek, CallCrypto.h(ek), z);
        return new KeyPair(ek, dk);
    }

    @Override
    public Encapsulation encaps(byte[] ek, byte[] m) {
        check(ek, EK_LEN);
        check(m, SEED_LEN);
        byte[] hek = CallCrypto.h(ek);
        byte[] pad = CallCrypto.h(l("pad"), hek);
        byte[] c0 = new byte[32];
        for (int i = 0; i < 32; i++) {
            c0[i] = (byte) (m[i] ^ pad[i]);
        }
        byte[] ct = CallBytes.concat(c0, xof(CT_LEN - 32, l("ct"), m, hek));
        byte[] ss = CallCrypto.h(l("ss"), m, CallCrypto.h(ct));
        return new Encapsulation(ss, ct);
    }

    /** Type check only: the double's keys are not ML-KEM encodings, so no modulus check applies. */
    @Override
    public boolean checkEncapsulationKey(byte[] ek) {
        return ek != null && ek.length == EK_LEN;
    }

    @Override
    public byte[] decaps(byte[] dk, byte[] ct) {
        check(dk, DK_LEN);
        check(ct, CT_LEN);
        byte[] ek = Arrays.copyOfRange(dk, 1152, 2336);
        byte[] hek = Arrays.copyOfRange(dk, 2336, 2368);
        byte[] z = Arrays.copyOfRange(dk, 2368, 2400);
        byte[] pad = CallCrypto.h(l("pad"), hek);
        byte[] m = new byte[32];
        for (int i = 0; i < 32; i++) {
            m[i] = (byte) (ct[i] ^ pad[i]);
        }
        Encapsulation again = encaps(ek, m);
        if (CallCrypto.constantTimeEquals(again.ciphertext(), ct)) {
            return again.sharedSecret();
        }
        return CallCrypto.h(l("reject"), z, ct);
    }

    private static void check(byte[] b, int len) {
        if (b == null || b.length != len) {
            throw new IllegalArgumentException("length " + (b == null ? -1 : b.length) + " != " + len);
        }
    }
}
