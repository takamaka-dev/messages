package io.takamaka.messages.call;

import io.takamaka.messages.call.epoch.EpochSchedule;
import io.takamaka.messages.call.service.CallTextBean;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [0.2] §10.2 in-call text AEAD (J-3).
 *
 * <p>The fixed vector is the one the Dart port published (rschat-docs/analysis/C182_DART_FIXES_J5_J3_2026-10-08.md
 * §3.4, computed independently in Python and Dart): the Java reference reproduces it BYTE FOR BYTE. Every byte here is
 * a public test constant.
 */
class CallTextsTest {

    static byte[] range(int from, int to) {
        byte[] b = new byte[to - from];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (from + i);
        }
        return b;
    }

    static final byte[] EPOCH_SECRET = range(0x00, 0x20);
    static final byte[] ERA_HASH = range(0x20, 0x40);
    static final byte[] CALL_ID = range(0x40, 0x60);
    static final byte[] LEG_ID = CallBytes.unhex("0102030405060708", 8);
    static final long E = 7;
    static final long CTR = 3;
    static final byte[] PLAINTEXT = "hello, call ☎".getBytes(StandardCharsets.UTF_8);

    static final String BROADCAST = "65857ef7b42700f76cf2b286a11ae2076fee4736faf59de3abc79859af9c4053";
    static final String TEXT_KEY = "91fa7ad5dad002e9d2a01f66fc470999c31d0e3ec3f8f9a48cdd1a6f4c4911cd";
    static final String AAD = "746b6d2d63616c6c2f76312f7465787400"
            + "404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f" + "0102030405060708" + "00000007";
    static final String NONCE = "000000070000000000000003";
    static final String CT = "ec875a3eadca0722ff7dc80cf20169e1a91b966060bcbd513d3c096187513c";
    /** The same text sealed with the draft-0.1 EMPTY AAD: must no longer open. */
    static final String CT_EMPTY_AAD = "ec875a3eadca0722ff7dc80cf20169a21d41de8de61e3800f73a23e530b837";

    @Test
    void dartVectorIsReproducedByteForByte() throws Exception {
        assertEquals("68656c6c6f2c2063616c6c20e2988e", CallBytes.hex(PLAINTEXT));
        byte[] bc = EpochSchedule.broadcast(EPOCH_SECRET, ERA_HASH);
        assertEquals(BROADCAST, CallBytes.hex(bc));
        byte[] key = EpochSchedule.textKeyFromBroadcast(bc, LEG_ID);
        assertEquals(TEXT_KEY, CallBytes.hex(key));
        assertEquals(TEXT_KEY, CallBytes.hex(EpochSchedule.textKey(EPOCH_SECRET, ERA_HASH, LEG_ID)), "speaker == listener path");
        byte[] aad = CallTexts.aad(CALL_ID, LEG_ID, E);
        assertEquals(61, aad.length);
        assertEquals(AAD, CallBytes.hex(aad));
        assertEquals(NONCE, CallBytes.hex(CallTexts.nonce(E, CTR)));
        byte[] ct = CallTexts.seal(key, CALL_ID, LEG_ID, E, CTR, PLAINTEXT);
        assertEquals(CT, CallBytes.hex(ct));
        assertArrayEquals(PLAINTEXT, CallTexts.open(key, CALL_ID, LEG_ID, E, CTR, ct));
        // the empty-AAD form is what the draft-0.1 Dart port produced: same keystream, another tag
        assertEquals(CT_EMPTY_AAD, CallBytes.hex(CallCrypto.aesGcmSeal(key, CallTexts.nonce(E, CTR), new byte[0], PLAINTEXT)));
        // the wire object of the vector, through the bean helpers
        CallTextBean t = CallTexts.build(bc, CallBytes.hex(CALL_ID), "0102030405060708", E, CTR, PLAINTEXT);
        assertEquals("{\"x\":\"text\",\"leg\":\"0102030405060708\",\"epoch\":7,\"ctr\":3,\"ct\":\"" + CT + "\"}", CallJson.toWire(t));
        assertTrue(CallTexts.wellFormed(t));
        assertArrayEquals(PLAINTEXT, CallTexts.open(bc, CallBytes.hex(CALL_ID), t));
    }

    @Test
    void theAadBindsCallLegAndEpochAndTheEmptyAadFormNoLongerOpens() {
        byte[] key = CallBytes.unhex(TEXT_KEY, 32);
        byte[] ct = CallBytes.unhex(CT);
        byte[] otherCall = CALL_ID.clone();
        otherCall[31] ^= 1;
        byte[] otherLeg = LEG_ID.clone();
        otherLeg[7] ^= 1;
        byte[] flipped = ct.clone();
        flipped[0] ^= 1;
        assertThrows(CallCrypto.AeadException.class, () -> CallTexts.open(key, otherCall, LEG_ID, E, CTR, ct), "another call");
        assertThrows(CallCrypto.AeadException.class, () -> CallTexts.open(key, CALL_ID, otherLeg, E, CTR, ct), "another leg");
        assertThrows(CallCrypto.AeadException.class, () -> CallTexts.open(key, CALL_ID, LEG_ID, E + 1, CTR, ct), "another epoch");
        assertThrows(CallCrypto.AeadException.class, () -> CallTexts.open(key, CALL_ID, LEG_ID, E, CTR + 1, ct), "another ctr");
        assertThrows(CallCrypto.AeadException.class, () -> CallTexts.open(key, CALL_ID, LEG_ID, E, CTR,
                CallBytes.unhex(CT_EMPTY_AAD)), "sealed with the draft-0.1 empty AAD");
        assertThrows(CallCrypto.AeadException.class, () -> CallTexts.open(key, CALL_ID, LEG_ID, E, CTR, flipped), "tampered");
    }

    @Test
    void inputsAndWireShapeAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> CallTexts.aad(Arrays.copyOf(CALL_ID, 31), LEG_ID, E));
        assertThrows(IllegalArgumentException.class, () -> CallTexts.aad(CALL_ID, Arrays.copyOf(LEG_ID, 7), E));
        assertThrows(IllegalArgumentException.class, () -> CallTexts.aad(CALL_ID, LEG_ID, -1));
        assertThrows(IllegalArgumentException.class, () -> CallTexts.aad(CALL_ID, LEG_ID, 0x1_0000_0000L));
        assertThrows(IllegalArgumentException.class, () -> CallTexts.nonce(E, -1));
        assertFalse(CallTexts.wellFormed(new CallTextBean("txt", "0102030405060708", 1L, 0L, CT)));
        assertFalse(CallTexts.wellFormed(new CallTextBean("text", "01020304050607", 1L, 0L, CT)));
        assertFalse(CallTexts.wellFormed(new CallTextBean("text", "0102030405060708", 0x1_0000_0000L, 0L, CT)));
        assertFalse(CallTexts.wellFormed(new CallTextBean("text", "0102030405060708", 1L, -1L, CT)));
        assertFalse(CallTexts.wellFormed(new CallTextBean("text", "0102030405060708", 1L, 0L, "00".repeat(15))), "shorter than a tag");
        assertFalse(CallTexts.wellFormed(new CallTextBean("text", "0102030405060708", 1L, 0L, CT.toUpperCase())));
        assertFalse(CallTexts.wellFormed(new CallTextBean("text", "0102030405060708", 1L, 0L, "00".repeat(2100))), "over 4 kB");
        byte[] bc = EpochSchedule.broadcast(EPOCH_SECRET, ERA_HASH);
        assertThrows(IllegalArgumentException.class, () -> CallTexts.build(bc, CallBytes.hex(CALL_ID), "0102030405060708", E, 0,
                new byte[2100]), "over 4 kB");
    }
}
