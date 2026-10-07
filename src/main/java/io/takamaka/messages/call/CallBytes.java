package io.takamaka.messages.call;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Byte helpers of the call protocol: lowercase hex (spec §2.2), big-endian integers, ASCII labels, concatenation.
 *
 * <p>Every concatenation ‖ of the specification operates on <b>raw octets</b>: a hex field is decoded first.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class CallBytes {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private CallBytes() {
    }

    /** Lowercase hex, as every binary field of a call object (spec §2.2). */
    public static String hex(byte[] b) {
        char[] out = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            out[2 * i] = HEX[(b[i] >> 4) & 0xf];
            out[2 * i + 1] = HEX[b[i] & 0xf];
        }
        return new String(out);
    }

    /**
     * Strict decoder: lowercase hex only, even length. Uppercase is refused, because a signed field has exactly
     * one encoding.
     */
    public static byte[] unhex(String s) {
        if (s == null || (s.length() & 1) != 0) {
            throw new IllegalArgumentException("hex: null or odd length");
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) ((nibble(s.charAt(2 * i)) << 4) | nibble(s.charAt(2 * i + 1)));
        }
        return out;
    }

    /** Strict decoder that also checks the decoded length. */
    public static byte[] unhex(String s, int expectedLen) {
        byte[] b = unhex(s);
        if (b.length != expectedLen) {
            throw new IllegalArgumentException("hex: expected " + expectedLen + " bytes, got " + b.length);
        }
        return b;
    }

    private static int nibble(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        throw new IllegalArgumentException("hex: not a lowercase hex digit: " + (int) c);
    }

    public static boolean isLowerHex(String s, int expectedLen) {
        try {
            unhex(s, expectedLen);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public static byte[] be16(int v) {
        if (v < 0 || v > 0xFFFF) {
            throw new IllegalArgumentException("be16 out of range: " + v);
        }
        return new byte[]{(byte) (v >>> 8), (byte) v};
    }

    /** BE32 of an unsigned 32-bit value given as long (0 .. 2^32-1). */
    public static byte[] be32(long v) {
        if (v < 0 || v > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("be32 out of range: " + v);
        }
        return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }

    /** BE64 of a non-negative long. */
    public static byte[] be64(long v) {
        if (v < 0) {
            throw new IllegalArgumentException("be64 of a negative value: " + v);
        }
        byte[] out = new byte[8];
        for (int i = 7; i >= 0; i--) {
            out[i] = (byte) v;
            v >>>= 8;
        }
        return out;
    }

    public static long readBe64(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (b[off + i] & 0xffL);
        }
        return v;
    }

    public static long readBe32(byte[] b, int off) {
        long v = 0;
        for (int i = 0; i < 4; i++) {
            v = (v << 8) | (b[off + i] & 0xffL);
        }
        return v;
    }

    /**
     * ASCII octets of a label. Throws on any char above 0x7F, so a label can never diverge between the Java
     * byte conversion and UTF-8 (DR-027).
     */
    public static byte[] ascii(String s) {
        requireAscii(s);
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    /** True if every char of {@code s} is in 0x00..0x7F. */
    public static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    public static void requireAscii(String s) {
        if (!isAscii(s)) {
            throw new IllegalArgumentException("non-ASCII character in a call-protocol string");
        }
    }

    public static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }

    /** Lexicographic comparison of unsigned bytes. */
    public static int compareUnsigned(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int d = (a[i] & 0xff) - (b[i] & 0xff);
            if (d != 0) {
                return d;
            }
        }
        return a.length - b.length;
    }
}
