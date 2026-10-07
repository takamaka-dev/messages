package io.takamaka.messages.call;

import io.takamaka.messages.call.sframe.SframeCipher;
import io.takamaka.messages.call.sframe.SframeHeader;
import io.takamaka.messages.call.sframe.SframeKeys;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** RFC 9605 Appendix C vectors first, then our units. */
class SframeTest {

    @Test
    void rfc9605AppendixC1HeaderVectors() throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/call/rfc9605_c1_headers.txt"), StandardCharsets.US_ASCII))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("#") && !line.isBlank()) {
                    rows.add(line.trim().split(" "));
                }
            }
        }
        assertEquals(289, rows.size(), "all C.1 cases extracted");
        for (String[] row : rows) {
            long kid = Long.parseUnsignedLong(row[0], 16);
            long ctr = Long.parseUnsignedLong(row[1], 16);
            assertEquals(row[2], CallBytes.hex(SframeHeader.encode(kid, ctr)), "encode " + row[0] + " " + row[1]);
            SframeHeader.Decoded d = SframeHeader.decode(CallBytes.unhex(row[2]), 0);
            assertEquals(kid, d.kid());
            assertEquals(ctr, d.ctr());
            assertEquals(row[2].length() / 2, d.length());
        }
    }

    @Test
    void rfc9605AppendixC3Suite5() throws Exception {
        SframeKeys k = SframeKeys.derive(0x123L, CallBytes.unhex("000102030405060708090a0b0c0d0e0f"));
        assertEquals("534672616d6520312e302053656372657420" + "6b65792000000000000001230005", CallBytes.hex(k.keyLabel()));
        assertEquals("534672616d6520312e30205365637265742073616c7420000000000000012300" + "05", CallBytes.hex(k.saltLabel()));
        assertEquals("0fc3ea6de6aac97a35f194cf9bed94d4b5230f1cb45a785c9fe5dce9c188938a"
                + "b6ba005bc4c0a19181599e9d1bcf7b74aca48b60bf5e254e546d809313e083a3", CallBytes.hex(k.secret()));
        assertEquals("d3e27b0d4a5ae9e55df01a70e6d4d28d969b246e2936f4b7a5d9b494da6b9633", CallBytes.hex(k.key()));
        assertEquals("84991c167b8cd23c93708ec7", CallBytes.hex(k.salt()));
        assertEquals("84991c167b8cd23c9370cba0", CallBytes.hex(k.nonce(0x4567)));
        byte[] meta = CallBytes.unhex("4945544620534672616d65205747");
        byte[] pt = CallBytes.unhex("64726166742d696574662d736672616d652d656e63");
        String ct = "990123456794f509d36e9beacb0e261d99c7d1e972f1fed787d4049f17ca21353c1cc24d56ceabced279";
        assertEquals(ct, CallBytes.hex(SframeCipher.encrypt(k, 0x4567, meta, pt)));
        assertArrayEquals(pt, SframeCipher.decrypt(k, meta, CallBytes.unhex(ct)));
        assertThrows(CallCrypto.AeadException.class, () -> SframeCipher.decrypt(k, new byte[0], CallBytes.unhex(ct)));
    }

    @Test
    void nonMinimalHeadersRejected() {
        assertThrows(IllegalArgumentException.class, () -> SframeHeader.decode(CallBytes.unhex("8005"), 0)); // KID 5 extended
        assertThrows(IllegalArgumentException.class, () -> SframeHeader.decode(CallBytes.unhex("090001"), 0)); // CTR 1 in 2 bytes
        assertThrows(IllegalArgumentException.class, () -> SframeHeader.decode(CallBytes.unhex("90000a"), 0)); // KID 10 in 2 bytes
        assertThrows(IllegalArgumentException.class, () -> SframeHeader.decode(CallBytes.unhex("a001"), 0)); // truncated
    }

    @Test
    void ourKidMapping() {
        assertEquals(0x00010004L, SframeHeader.kid(1, 4));
        assertEquals(0x00010000L, SframeHeader.kid(1, 65536));
        assertEquals(3L, SframeHeader.kid(0, 3));
        assertEquals("30", CallBytes.hex(SframeHeader.encode(SframeHeader.kid(0, 3), 0))); // leg 0, epoch 3: inline
        assertEquals("a0010004", CallBytes.hex(SframeHeader.encode(SframeHeader.kid(1, 4), 0)));
        assertEquals("b0ffffffff", CallBytes.hex(SframeHeader.encode(SframeHeader.kid(0xffff, 0xffff), 0)));
        assertThrows(IllegalArgumentException.class, () -> SframeHeader.kid(0x10000, 0));
    }

    @Test
    void unitsWithClearPrefix() throws Exception {
        SframeKeys k = SframeKeys.derive(SframeHeader.kid(2, 7), CallTestKeys.seed("sframe/base"));
        byte[] opus = CallBytes.concat(new byte[]{(byte) 0x78}, CallTestKeys.seed("opus"));
        byte[] unit = SframeCipher.seal(k, 9, opus, SframeCipher.OPUS_PREFIX);
        assertEquals((byte) 0x78, unit[0], "TOC in clear");
        assertEquals(opus.length + SframeHeader.encode(k.kid(), 9).length + 16, unit.length);
        assertArrayEquals(opus, SframeCipher.open(k, unit, 1));
        SframeHeader.Decoded h = SframeCipher.header(unit, 1);
        assertEquals(k.kid(), h.kid());
        assertEquals(9, h.ctr());
        // the clear prefix is authenticated
        byte[] t = unit.clone();
        t[0] ^= 0x04;
        assertThrows(CallCrypto.AeadException.class, () -> SframeCipher.open(k, t, 1));
        // the header is authenticated (CTR changed -> other nonce and AAD)
        byte[] t2 = unit.clone();
        t2[t2.length - 1] ^= 1;
        assertThrows(CallCrypto.AeadException.class, () -> SframeCipher.open(k, t2, 1));
        // a key of another epoch (other KID) never opens it: nothing passes in plaintext
        SframeKeys other = SframeKeys.derive(SframeHeader.kid(2, 8), CallTestKeys.seed("sframe/base"));
        assertThrows(CallCrypto.AeadException.class, () -> SframeCipher.open(other, unit, 1));
        // same KID, different base key
        SframeKeys wrong = SframeKeys.derive(SframeHeader.kid(2, 7), CallTestKeys.seed("sframe/other"));
        assertThrows(CallCrypto.AeadException.class, () -> SframeCipher.open(wrong, unit, 1));
    }
}
