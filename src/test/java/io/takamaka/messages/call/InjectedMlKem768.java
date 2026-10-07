package io.takamaka.messages.call;

import io.takamaka.messages.call.channel.MlKem768;
import java.util.Arrays;

/**
 * A KEM that returns INJECTED values: {@code encaps} answers the given (ss, ct) for the given ek, {@code decaps}
 * answers ss for exactly that ct (and a different value otherwise). Lets the Java combiner reproduce a case made
 * with a real ML-KEM-768 elsewhere (the Dart spike) before the Java binding exists. Tests only.
 */
public final class InjectedMlKem768 implements MlKem768 {

    private final byte[] ek;
    private final byte[] dk;
    private final byte[] ss;
    private final byte[] ct;

    public InjectedMlKem768(byte[] ek, byte[] dk, byte[] ss, byte[] ct) {
        this.ek = ek.clone();
        this.dk = dk.clone();
        this.ss = ss.clone();
        this.ct = ct.clone();
    }

    @Override
    public String name() {
        return "injected";
    }

    @Override
    public KeyPair keyGen(byte[] d, byte[] z) {
        return new KeyPair(ek.clone(), dk.clone());
    }

    @Override
    public Encapsulation encaps(byte[] ek, byte[] m) {
        if (!Arrays.equals(ek, this.ek)) {
            throw new IllegalArgumentException("injected KEM: unknown ek");
        }
        return new Encapsulation(ss.clone(), ct.clone());
    }

    @Override
    public byte[] decaps(byte[] dk, byte[] ct) {
        if (Arrays.equals(dk, this.dk) && Arrays.equals(ct, this.ct)) {
            return ss.clone();
        }
        return CallCrypto.h(CallBytes.ascii("injected/reject"), ct);
    }
}
