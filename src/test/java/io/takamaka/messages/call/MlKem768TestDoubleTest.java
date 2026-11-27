package io.takamaka.messages.call;

import io.takamaka.messages.call.channel.MlKem768;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MlKem768TestDoubleTest {

    private final MlKem768 kem = new MlKem768TestDouble();

    @Test
    void sizesAndRoundTrip() {
        MlKem768.KeyPair kp = kem.keyGen(CallTestKeys.seed("d"), CallTestKeys.seed("z"));
        assertEquals(MlKem768.EK_LEN, kp.ek().length);
        assertEquals(MlKem768.DK_LEN, kp.dk().length);
        MlKem768.Encapsulation e = kem.encaps(kp.ek(), CallTestKeys.seed("m"));
        assertEquals(MlKem768.CT_LEN, e.ciphertext().length);
        assertEquals(MlKem768.SS_LEN, e.sharedSecret().length);
        assertArrayEquals(e.sharedSecret(), kem.decaps(kp.dk(), e.ciphertext()));
        assertEquals("test-double", kem.name());
    }

    @Test
    void deterministicAndImplicitReject() {
        MlKem768.KeyPair kp = kem.keyGen(CallTestKeys.seed("d"), CallTestKeys.seed("z"));
        assertArrayEquals(kp.ek(), kem.keyGen(CallTestKeys.seed("d"), CallTestKeys.seed("z")).ek());
        MlKem768.Encapsulation e = kem.encaps(kp.ek(), CallTestKeys.seed("m"));
        byte[] bad = e.ciphertext().clone();
        bad[500] ^= 1;
        byte[] ss = kem.decaps(kp.dk(), bad);
        assertEquals(32, ss.length);
        assertFalse(java.util.Arrays.equals(e.sharedSecret(), ss));
        MlKem768.KeyPair other = kem.keyGen(CallTestKeys.seed("d2"), CallTestKeys.seed("z"));
        assertFalse(java.util.Arrays.equals(e.sharedSecret(), kem.decaps(other.dk(), e.ciphertext())));
    }
}
