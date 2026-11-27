package io.takamaka.messages.call;

import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CallPrimitivesTest {

    @Test
    void hexIsStrictLowercase() {
        assertEquals("00ff10", CallBytes.hex(new byte[]{0, (byte) 0xff, 0x10}));
        assertArrayEquals(new byte[]{0, (byte) 0xff}, CallBytes.unhex("00ff"));
        assertThrows(IllegalArgumentException.class, () -> CallBytes.unhex("00FF"));
        assertThrows(IllegalArgumentException.class, () -> CallBytes.unhex("abc"));
        assertThrows(IllegalArgumentException.class, () -> CallBytes.unhex("zz"));
        assertThrows(IllegalArgumentException.class, () -> CallBytes.unhex("00", 2));
    }

    @Test
    void bigEndianEncodings() {
        assertEquals("00000001", CallBytes.hex(CallBytes.be32(1)));
        assertEquals("ffffffff", CallBytes.hex(CallBytes.be32(0xFFFFFFFFL)));
        assertEquals("0000000000000102", CallBytes.hex(CallBytes.be64(258)));
        assertThrows(IllegalArgumentException.class, () -> CallBytes.be32(-1));
        assertThrows(IllegalArgumentException.class, () -> CallBytes.be16(0x10000));
    }

    @Test
    void asciiGuard() {
        assertTrue(CallBytes.isAscii("tkm-call/v1/\u0000{}"));
        assertFalse(CallBytes.isAscii("café"));
        assertThrows(IllegalArgumentException.class, () -> CallBytes.ascii("Ā"));
    }

    @Test
    void sha3EmptyKnownAnswer() {
        assertEquals("a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a", CallBytes.hex(CallCrypto.h()));
    }

    @Test
    void hmacSha512Rfc4231Case1() {
        byte[] key = new byte[20];
        java.util.Arrays.fill(key, (byte) 0x0b);
        assertEquals("87aa7cdea5ef619d4ff0b4241a1d6cb02379f4e2ce4ec2787ad0b30545e17cde"
                + "daa833b7d6b8a702038b274eaea3f4e4be9d914eeb61f1702e696c203a126854",
                CallBytes.hex(CallCrypto.hmacSha512(key, CallBytes.ascii("Hi There"))));
    }

    @Test
    void hkdfMatchesBouncyCastleGenerator() {
        byte[] ikm = CallTestKeys.seed("hkdf/ikm");
        byte[] salt = CallTestKeys.seed("hkdf/salt");
        byte[] info = CallBytes.ascii("tkm-call/v1/test");
        for (int len : new int[]{12, 32, 64, 100}) {
            HKDFBytesGenerator g = new HKDFBytesGenerator(new SHA512Digest());
            g.init(new HKDFParameters(ikm, salt, info));
            byte[] bc = new byte[len];
            g.generateBytes(bc, 0, len);
            assertArrayEquals(bc, CallCrypto.hkdf(ikm, salt, info, len), "L=" + len);
        }
        // HKDF(k, info): salt = 32 zero bytes, L = 32
        HKDFBytesGenerator g = new HKDFBytesGenerator(new SHA512Digest());
        g.init(new HKDFParameters(ikm, new byte[32], info));
        byte[] bc = new byte[32];
        g.generateBytes(bc, 0, 32);
        assertArrayEquals(bc, CallCrypto.hkdf(ikm, info));
    }

    @Test
    void aesGcmRoundTripAndTamper() throws Exception {
        byte[] key = CallTestKeys.seed("gcm/key");
        byte[] nonce = new byte[12];
        byte[] aad = CallBytes.ascii("{\"a\":1}");
        byte[] pt = CallBytes.ascii("hello");
        byte[] ct = CallCrypto.aesGcmSeal(key, nonce, aad, pt);
        assertEquals(pt.length + 16, ct.length);
        assertArrayEquals(pt, CallCrypto.aesGcmOpen(key, nonce, aad, ct));
        ct[0] ^= 1;
        assertThrows(CallCrypto.AeadException.class, () -> CallCrypto.aesGcmOpen(key, nonce, aad, ct));
        ct[0] ^= 1;
        assertThrows(CallCrypto.AeadException.class, () -> CallCrypto.aesGcmOpen(key, nonce, CallBytes.ascii("{}"), ct));
    }

    @Test
    void x25519Rfc7748() {
        byte[] a = CallBytes.unhex(CallVectorScenario.RFC7748_ALICE_PRIV);
        byte[] b = CallBytes.unhex(CallVectorScenario.RFC7748_BOB_PRIV);
        assertEquals(CallVectorScenario.RFC7748_ALICE_PUB, CallBytes.hex(CallCrypto.x25519Public(a)));
        assertEquals(CallVectorScenario.RFC7748_BOB_PUB, CallBytes.hex(CallCrypto.x25519Public(b)));
        assertEquals(CallVectorScenario.RFC7748_K, CallBytes.hex(CallCrypto.x25519(a, CallCrypto.x25519Public(b))));
        assertEquals(CallVectorScenario.RFC7748_K, CallBytes.hex(CallCrypto.x25519(b, CallCrypto.x25519Public(a))));
        // all-zero output (low-order point) is refused
        assertThrows(IllegalStateException.class, () -> CallCrypto.x25519(a, new byte[32]));
    }
}
