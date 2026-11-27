package io.takamaka.messages.call.sframe;

import java.io.ByteArrayOutputStream;

/**
 * SFrame header, RFC 9605 §4.3: config byte {@code X|KKK|Y|CCC}, then KID and CTR as compact big-endian unsigned
 * integers (values 0..7 inline in the config byte, otherwise the minimum number of bytes, length-1 in the 3-bit
 * field). KID and CTR are unsigned 64-bit values held in a Java {@code long}.
 *
 * <p>Our KID (spec §9.2) is {@code BE16(leg_index) ‖ BE16(e mod 2^16)} read as one integer; it is encoded here in
 * the RFC's compact form, so a small {@code leg_index} gives fewer than 4 KID bytes (ambiguity A-13 of the C182
 * status report: the design's "4 bytes" diagram vs the RFC's "MUST be encoded with the minimum number of bytes").
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class SframeHeader {

    private SframeHeader() {
    }

    /** A decoded header: KID, CTR and the header length in bytes. */
    public record Decoded(long kid, long ctr, int length) {

    }

    /** Our KID from the leg index (16 bit) and the epoch (taken mod 2^16). */
    public static long kid(int legIndex, long epoch) {
        if (legIndex < 0 || legIndex > 0xFFFF) {
            throw new IllegalArgumentException("leg_index is 16-bit");
        }
        if (epoch < 0) {
            throw new IllegalArgumentException("epoch");
        }
        return ((long) legIndex << 16) | (epoch & 0xFFFFL);
    }

    static int minLen(long v) {
        int n = 1;
        while (n < 8 && Long.compareUnsigned(v, 1L << (8 * n)) >= 0) {
            n++;
        }
        return n;
    }

    static void writeBe(ByteArrayOutputStream out, long v, int n) {
        for (int i = n - 1; i >= 0; i--) {
            out.write((int) (v >>> (8 * i)) & 0xff);
        }
    }

    public static byte[] encode(long kid, long ctr) {
        ByteArrayOutputStream ext = new ByteArrayOutputStream();
        int config = 0;
        if (Long.compareUnsigned(kid, 8) < 0) {
            config |= ((int) kid) << 4;
        } else {
            int n = minLen(kid);
            config |= 0x80 | ((n - 1) << 4);
            writeBe(ext, kid, n);
        }
        if (Long.compareUnsigned(ctr, 8) < 0) {
            config |= (int) ctr;
        } else {
            int n = minLen(ctr);
            config |= 0x08 | (n - 1);
            writeBe(ext, ctr, n);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(config);
        out.writeBytes(ext.toByteArray());
        return out.toByteArray();
    }

    /** Decodes the header at {@code off}; throws on truncation or a non-minimal encoding. */
    public static Decoded decode(byte[] b, int off) {
        if (b.length <= off) {
            throw new IllegalArgumentException("empty SFrame header");
        }
        int config = b[off] & 0xff;
        int pos = off + 1;
        long kid;
        long ctr;
        if ((config & 0x80) == 0) {
            kid = (config >> 4) & 0x7;
        } else {
            int n = ((config >> 4) & 0x7) + 1;
            kid = readBe(b, pos, n);
            pos += n;
            if (Long.compareUnsigned(kid, 8) < 0 || minLen(kid) != n) {
                throw new IllegalArgumentException("non-minimal KID encoding");
            }
        }
        if ((config & 0x08) == 0) {
            ctr = config & 0x7;
        } else {
            int n = (config & 0x7) + 1;
            ctr = readBe(b, pos, n);
            pos += n;
            if (Long.compareUnsigned(ctr, 8) < 0 || minLen(ctr) != n) {
                throw new IllegalArgumentException("non-minimal CTR encoding");
            }
        }
        return new Decoded(kid, ctr, pos - off);
    }

    static long readBe(byte[] b, int pos, int n) {
        if (pos + n > b.length) {
            throw new IllegalArgumentException("truncated SFrame header");
        }
        long v = 0;
        for (int i = 0; i < n; i++) {
            v = (v << 8) | (b[pos + i] & 0xffL);
        }
        return v;
    }
}
