package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import io.takamaka.messages.utils.ChatSignatureVerifier;
import io.takamaka.messages.utils.SimpleRequestHelper;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@code tkm-push/v1}: the sealed call push of DR-063 (design {@code F2_SEALED_CALL_PUSH_DESIGN_2026-11-04} §1.2,
 * frozen; Iris §7.123 item 4 and §7.194). rschat seals the clear FCM data map of a call push to the X25519 push key
 * the device registered at {@code registerfcmtoken}; Google and Apple then see only
 * {@code {"type": <clear type>, "v": "2", "sp": <sealed>}}.
 *
 * <h2>Construction (design §1.2, DR-063 §1; one PAD only, the 2560 bucket is dropped)</h2>
 * <pre>
 * d    device scalar (32 bytes), pkD = X25519(d, 9)
 * kid  = SHA3-256(ASCII("tkm-push/v1/kid") ‖ pkD)[0..8]                   8 bytes
 * e    32 fresh random bytes per seal, epk = X25519(e, 9)
 * hdr  = 0x01 ‖ kid ‖ epk                                                41 bytes
 * dh   = X25519(e, pkD) = X25519(d, epk)                                 all-zero refused (LOW_ORDER)
 * okm  = HKDF-SHA-512(ikm = dh, salt = epk ‖ pkD, info = ASCII("tkm-push/v1/seal"), L = 44)
 *        key = okm[0..32], nonce = okm[32..44]
 * aad  = ASCII("tkm-push/v1/aad") ‖ 0x00 ‖ UTF-8(type) ‖ 0x00 ‖ hdr        type = the CLEAR type
 * J    = UTF-8(JCS(m))                                                   m = the clear data map (String → String)
 * pt   = u16be(len J) ‖ J ‖ 0x00 × (PAD − 2 − len J)                     PAD = 2048
 * ct   = AES-256-GCM(key, nonce, aad, pt)                                ciphertext ‖ 16-byte tag
 * sp   = Base64URL(hdr ‖ ct), '.' padding                                2105 bytes = 2808 chars
 * </pre>
 * SHA3-256 is FIPS 202 ({@link CallCrypto#h}, Bouncy Castle {@code SHA3Digest}), not Keccak-256. Scalars are used
 * raw: X25519 clamps them per RFC 7748 §5 on both sides. JCS is RFC 8785 through
 * {@link SimpleRequestHelper#getCanonicalJson} ({@code org.erdtman.jcs.JsonCanonicalizer}), the canonicalizer of the
 * signed chat path and of {@code CallRschat.pushRing} ({@code CallJson.canonical}). {@code push_key_id} (the
 * registration echo, DB column) is {@link #kidHex}: the lower hex of {@code kid}, 16 characters.
 *
 * <h2>Frame rules (DR-063 §2): the suite's business, the same for every type</h2>
 * Every inner value is a string; no duplicate key; {@code J} is the JCS of the parsed object; the inner {@code type}
 * equals the clear {@code type}; the inner {@code v} is {@code "1"}; the padding is all zero; {@code len(J) ≤ PAD − 2}.
 * The per-type key set is NOT checked here: a handler ignores keys it does not know (SO-2, PS-12).
 *
 * <h2>The opener's refusal order (part of the contract: every refusal vector carries its code)</h2>
 * <ol>
 * <li>{@link Reason#BAD_ENCODING}: {@code sp} is not the canonical Base64URL spelling ({@code +}/{@code /}, {@code =},
 * a missing or misplaced {@code .}, any whitespace or newline, non-zero unused bits) —
 * {@link ChatSignatureVerifier#decodeStrictOrNull}, the identity decoder;</li>
 * <li>{@link Reason#BAD_LENGTH}: the decoded length is not {@value #BLOB_LEN} (trailing bytes are refused here);</li>
 * <li>{@link Reason#BAD_VERSION}: the first byte is not {@code 0x01};</li>
 * <li>{@link Reason#KID_MISMATCH}: {@code kid} is not the kid of the opening key;</li>
 * <li>{@link Reason#LOW_ORDER}: {@code X25519(d, epk)} is all zero (a low-order {@code epk});</li>
 * <li>{@link Reason#AEAD}: the tag does not verify (wrong clear {@code type}: a relabelled push; tampering);</li>
 * <li>{@link Reason#TOO_LONG}: the u16 length prefix is greater than {@code PAD − 2};</li>
 * <li>{@link Reason#BAD_PADDING}: a byte after {@code J} is not zero;</li>
 * <li>{@link Reason#BAD_JSON}: {@code J} is not strict UTF-8, or not one JSON object (syntax error, trailing
 * content, a top-level value other than an object, an empty {@code J});</li>
 * <li>{@link Reason#DUPLICATE_KEY}: a top-level key repeats — detected WHILE parsing, left to right, so it competes
 * with {@code BAD_JSON} by position: whichever fault the parse meets first is reported;</li>
 * <li>{@link Reason#NON_STRING}: a top-level value is not a JSON string — reported only after a complete,
 * duplicate-free parse;</li>
 * <li>{@link Reason#NOT_JCS}: the bytes of {@code J} differ from the JCS bytes re-derived from the PARSED object
 * (key order, whitespace, a {@code \\u} escape of a character JCS emits raw, such as e-acute);</li>
 * <li>{@link Reason#TYPE_MISMATCH}: the inner {@code type} is absent or differs from the clear {@code type};</li>
 * <li>{@link Reason#BAD_INNER_V}: the inner {@code v} is absent or not {@code "1"}.</li>
 * </ol>
 * The push-key refusals ({@link #validatePushKey}, {@link #checkPushKey}) are, in order: {@link Reason#BAD_ENCODING},
 * {@link Reason#BAD_KEY_LENGTH}, {@link Reason#HIGH_BIT}, {@link Reason#NON_CANONICAL}, {@link Reason#LOW_ORDER}.
 *
 * <p><b>The seal is anonymous (DR-063 §1, Iris §7.194 Q3).</b> Anyone holding {@code pkD} can seal to it: a sealed
 * push is confidential towards Google and Apple, not authenticated. A ring or a cancel acts only through the caller's
 * signature inside it.
 *
 * <p>Nothing here logs; no exception message carries key material, {@code sp}, or a plaintext.
 *
 * <p>Vectors: {@code src/test/resources/call/push_seal_vectors_v1.json} (Java reference, ported byte for byte by
 * rsclient-flutter {@code lib/src/call/push_seal.dart}).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class PushSeal {

    private PushSeal() {
    }

    /** The suite id, the {@code push_seal} value of the registration and of {@code serverinfo.pushSealSuites}. */
    public static final String SUITE = "tkm-push/v1";
    /** The version byte, first byte of the header. */
    public static final int VERSION = 0x01;
    /** The one padded plaintext size (DR-063 §1: single PAD; the 2560 bucket of design §1.4 is dropped). */
    public static final int PAD = 2048;
    /** The largest {@code len(J)}: {@code PAD − 2}. */
    public static final int MAX_J = PAD - 2;
    public static final int KID_LEN = 8;
    public static final int KEY_LEN = 32;
    /** {@code 0x01 ‖ kid ‖ epk}. */
    public static final int HEADER_LEN = 1 + KID_LEN + KEY_LEN;
    public static final int TAG_LEN = CallConstants.GCM_TAG_LEN;
    /** The decoded length of {@code sp}: {@code 41 + 2048 + 16}. */
    public static final int BLOB_LEN = HEADER_LEN + PAD + TAG_LEN;
    /** The length of {@code sp} in characters (one {@code .} pad). */
    public static final int SP_LEN = (BLOB_LEN + 2) / 3 * 4;
    /** HKDF output: key 32 ‖ nonce 12. */
    public static final int OKM_LEN = KEY_LEN + CallConstants.GCM_NONCE_LEN;

    public static final String KID_LABEL = "tkm-push/v1/kid";
    public static final String SEAL_INFO = "tkm-push/v1/seal";
    public static final String AAD_LABEL = "tkm-push/v1/aad";

    /** The inner {@code v} of every sealed map (today's clear form). */
    public static final String INNER_V = "1";
    /** The clear {@code v} of a sealed push. */
    public static final String WIRE_V = "2";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_V = "v";
    public static final String FIELD_SP = "sp";

    /**
     * The fixed scalar of the low-order check: 32 bytes of {@code 0x01}, used raw (clamped by X25519). Any clamped
     * scalar is a multiple of the cofactor 8, so X25519 of every point of order 1, 2, 4 or 8 with it is all zero.
     */
    static final byte[] LOW_ORDER_CHECK_SCALAR = filled(0x01);

    /** p = 2^255 − 19. */
    static final BigInteger P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));

    private static final JsonFactory JSON = new JsonFactory();

    /** Why a seal, an open or a push key was refused. The order of the opener's checks is in the class doc. */
    public enum Reason {
        BAD_ENCODING,
        BAD_LENGTH,
        BAD_VERSION,
        KID_MISMATCH,
        AEAD,
        BAD_PADDING,
        BAD_JSON,
        NOT_JCS,
        NON_STRING,
        DUPLICATE_KEY,
        TYPE_MISMATCH,
        BAD_INNER_V,
        TOO_LONG,
        /** A push key (or {@code epk}) whose X25519 output with a clamped scalar is all zero. */
        LOW_ORDER,
        /** A push key that does not decode to exactly 32 bytes. */
        BAD_KEY_LENGTH,
        /** A push key with bit 255 set (never masked). */
        HIGH_BIT,
        /** A push key whose u-coordinate is ≥ p. */
        NON_CANONICAL
    }

    /** A typed refusal. The message is the reason and a short fixed text; it never carries key or push bytes. */
    public static final class PushSealException extends Exception {

        private final Reason reason;

        public PushSealException(Reason reason, String detail) {
            super(reason.name() + ": " + detail);
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }

    /** The clear header of a sealed push, read without any key. */
    public record Header(int version, byte[] kid, byte[] epk) {

        public Header {
            kid = kid.clone();
            epk = epk.clone();
        }

        @Override
        public byte[] kid() {
            return kid.clone();
        }

        @Override
        public byte[] epk() {
            return epk.clone();
        }

        /** {@code push_key_id} form of {@link #kid()}. */
        public String kidHex() {
            return CallBytes.hex(kid);
        }
    }

    // ---- key id and push key ---------------------------------------------------------------------------------------

    /** {@code kid = SHA3-256(ASCII("tkm-push/v1/kid") ‖ pkD)[0..8]}. */
    public static byte[] kid(byte[] pkD) {
        if (pkD == null || pkD.length != KEY_LEN) {
            throw new IllegalArgumentException("push key must be 32 bytes");
        }
        return Arrays.copyOf(CallCrypto.h(CallBytes.ascii(KID_LABEL), pkD), KID_LEN);
    }

    /** {@code push_key_id}: the lower hex of {@link #kid}, 16 characters. */
    public static String kidHex(byte[] pkD) {
        return CallBytes.hex(kid(pkD));
    }

    /**
     * The registration check of {@code push_key} (DR-063 §3.3, design §3.1): strict Base64URL with {@code .} padding
     * (the identity decoder), then {@link #checkPushKey}. rschat refuses with {@code INVALID_PUSH_KEY} on any failure.
     *
     * @return the 32 key bytes
     * @throws PushSealException in order: {@code BAD_ENCODING}, {@code BAD_KEY_LENGTH}, {@code HIGH_BIT},
     * {@code NON_CANONICAL}, {@code LOW_ORDER}
     */
    public static byte[] validatePushKey(String b64) throws PushSealException {
        byte[] k = ChatSignatureVerifier.decodeStrictOrNull(b64);
        if (k == null) {
            throw new PushSealException(Reason.BAD_ENCODING, "push key is not canonical Base64URL");
        }
        checkPushKey(k);
        return k;
    }

    /**
     * The byte checks of a push key, in order: exactly 32 bytes ({@code BAD_KEY_LENGTH}); bit 255 clear, never masked
     * ({@code HIGH_BIT}); u &lt; p ({@code NON_CANONICAL}); X25519({@link #LOW_ORDER_CHECK_SCALAR}, pk) not all zero
     * ({@code LOW_ORDER}). {@code u} is little-endian (RFC 7748 §5).
     */
    public static void checkPushKey(byte[] pk) throws PushSealException {
        if (pk == null || pk.length != KEY_LEN) {
            throw new PushSealException(Reason.BAD_KEY_LENGTH, "push key is not 32 bytes");
        }
        if ((pk[KEY_LEN - 1] & 0x80) != 0) {
            throw new PushSealException(Reason.HIGH_BIT, "push key has bit 255 set");
        }
        if (littleEndian(pk).compareTo(P) >= 0) {
            throw new PushSealException(Reason.NON_CANONICAL, "push key u >= p");
        }
        byte[] z = dhOrNull(LOW_ORDER_CHECK_SCALAR, pk);
        if (z == null) {
            throw new PushSealException(Reason.LOW_ORDER, "push key is a low-order point");
        }
        Arrays.fill(z, (byte) 0);
    }

    // ---- seal -------------------------------------------------------------------------------------------------------

    /**
     * Seals the clear data map {@code m} of a push of clear type {@code type} to the device key {@code pkD}, with a
     * fresh ephemeral from {@code rnd}. The map must already obey the frame rules (inner {@code type} = {@code type},
     * inner {@code v} = {@code "1"}, no null key or value) and fit ({@code len(JCS(m)) ≤ PAD − 2}): the seal refuses
     * otherwise, and the server decides what to send instead (DR-063 §3.1).
     *
     * @return {@code sp}
     * @throws PushSealException {@code NON_STRING} (a null key or value), {@code TOO_LONG}, {@code TYPE_MISMATCH},
     * {@code BAD_INNER_V}, or a push-key reason of {@link #checkPushKey}
     */
    public static String seal(byte[] pkD, String type, Map<String, String> m, SecureRandom rnd) throws PushSealException {
        Objects.requireNonNull(rnd, "rnd");
        byte[] e = new byte[KEY_LEN];
        rnd.nextBytes(e);
        try {
            return seal(pkD, type, m, e);
        } finally {
            Arrays.fill(e, (byte) 0);
        }
    }

    /** {@link #seal(byte[], String, Map, SecureRandom)} with a FIXED ephemeral scalar: vectors and tests only. */
    static String seal(byte[] pkD, String type, Map<String, String> m, byte[] e) throws PushSealException {
        Objects.requireNonNull(type, "type");
        checkPushKey(pkD);
        byte[] j = frame(m);
        if (j.length > MAX_J) {
            throw new PushSealException(Reason.TOO_LONG, "J does not fit the pad");
        }
        // the opener's own frame checks, so a seal never produces a push that cannot open
        parseFrame(j, type);
        return sealPlaintext(pkD, VERSION, type, plaintext(j), e);
    }

    /** {@code J = UTF-8(JCS(m))}; a null map, key or value is {@code NON_STRING}. */
    static byte[] frame(Map<String, String> m) throws PushSealException {
        if (m == null) {
            throw new PushSealException(Reason.NON_STRING, "null map");
        }
        for (Map.Entry<String, String> en : m.entrySet()) {
            if (en.getKey() == null || en.getValue() == null) {
                throw new PushSealException(Reason.NON_STRING, "null key or value");
            }
        }
        return jcs(m).getBytes(StandardCharsets.UTF_8);
    }

    /** {@code pt = u16be(len J) ‖ J ‖ 0x00 × (PAD − 2 − len J)}; {@code J} must fit. */
    static byte[] plaintext(byte[] j) {
        if (j.length > MAX_J) {
            throw new IllegalArgumentException("J does not fit the pad");
        }
        byte[] pt = new byte[PAD];
        pt[0] = (byte) (j.length >>> 8);
        pt[1] = (byte) j.length;
        System.arraycopy(j, 0, pt, 2, j.length);
        return pt;
    }

    /**
     * The raw construction over a caller-built plaintext and version byte, with NO frame check: the vector generator
     * uses it to build refusal cases (non-zero padding, a bad length prefix, a non-JCS {@code J}, version 0x02).
     */
    static String sealPlaintext(byte[] pkD, int version, String type, byte[] pt, byte[] e) throws PushSealException {
        if (e == null || e.length != KEY_LEN) {
            throw new IllegalArgumentException("ephemeral scalar must be 32 bytes");
        }
        byte[] epk = CallCrypto.x25519Public(e);
        byte[] hdr = header(version, kid(pkD), epk);
        byte[] dh = dhOrNull(e, pkD);
        if (dh == null) {
            throw new PushSealException(Reason.LOW_ORDER, "all-zero shared secret");
        }
        byte[] okm = keySchedule(dh, epk, pkD);
        try {
            byte[] ct = CallCrypto.aesGcmSeal(Arrays.copyOfRange(okm, 0, KEY_LEN), Arrays.copyOfRange(okm, KEY_LEN, OKM_LEN),
                    aad(type, hdr), pt);
            return CallSignatures.urlBase64(CallBytes.concat(hdr, ct));
        } finally {
            Arrays.fill(dh, (byte) 0);
            Arrays.fill(okm, (byte) 0);
        }
    }

    static byte[] header(int version, byte[] kid, byte[] epk) {
        return CallBytes.concat(new byte[]{(byte) version}, kid, epk);
    }

    /** {@code HKDF-SHA-512(dh, salt = epk ‖ pkD, info = "tkm-push/v1/seal", 44)}. */
    static byte[] keySchedule(byte[] dh, byte[] epk, byte[] pkD) {
        return CallCrypto.hkdf(dh, CallBytes.concat(epk, pkD), CallBytes.ascii(SEAL_INFO), OKM_LEN);
    }

    /** {@code ASCII("tkm-push/v1/aad") ‖ 0x00 ‖ UTF-8(type) ‖ 0x00 ‖ hdr}. */
    static byte[] aad(String type, byte[] hdr) {
        return CallBytes.concat(CallBytes.ascii(AAD_LABEL), new byte[]{0}, type.getBytes(StandardCharsets.UTF_8),
                new byte[]{0}, hdr);
    }

    // ---- open -------------------------------------------------------------------------------------------------------

    /**
     * Reads the clear header of {@code sp} without any key, so a device can pick its key (current, previous or pending)
     * by {@code kid}. Checks only the encoding and the length; the version is returned as found and checked by
     * {@link #open}.
     *
     * @throws PushSealException {@code BAD_ENCODING}, {@code BAD_LENGTH}
     */
    public static Header parseHeader(String sp) throws PushSealException {
        byte[] blob = decode(sp);
        return new Header(blob[0] & 0xff, Arrays.copyOfRange(blob, 1, 1 + KID_LEN),
                Arrays.copyOfRange(blob, 1 + KID_LEN, HEADER_LEN));
    }

    /**
     * Opens {@code sp} with the device scalar {@code d} under the CLEAR {@code type} of the push and applies every
     * frame rule. The refusal order is the class doc's.
     *
     * @return the clear data map, in {@code J}'s key order, unmodifiable
     */
    public static Map<String, String> open(byte[] d, String clearType, String sp) throws PushSealException {
        if (d == null || d.length != KEY_LEN) {
            throw new IllegalArgumentException("device scalar must be 32 bytes");
        }
        Objects.requireNonNull(clearType, "clearType");
        byte[] blob = decode(sp);
        if ((blob[0] & 0xff) != VERSION) {
            throw new PushSealException(Reason.BAD_VERSION, "unknown version");
        }
        byte[] hdr = Arrays.copyOfRange(blob, 0, HEADER_LEN);
        byte[] kid = Arrays.copyOfRange(blob, 1, 1 + KID_LEN);
        byte[] epk = Arrays.copyOfRange(blob, 1 + KID_LEN, HEADER_LEN);
        byte[] pkD = CallCrypto.x25519Public(d);
        if (!CallCrypto.constantTimeEquals(kid, kid(pkD))) {
            throw new PushSealException(Reason.KID_MISMATCH, "not sealed to this key");
        }
        byte[] dh = dhOrNull(d, epk);
        if (dh == null) {
            throw new PushSealException(Reason.LOW_ORDER, "all-zero shared secret");
        }
        byte[] okm = keySchedule(dh, epk, pkD);
        byte[] pt;
        try {
            pt = CallCrypto.aesGcmOpen(Arrays.copyOfRange(okm, 0, KEY_LEN), Arrays.copyOfRange(okm, KEY_LEN, OKM_LEN),
                    aad(clearType, hdr), Arrays.copyOfRange(blob, HEADER_LEN, BLOB_LEN));
        } catch (CallCrypto.AeadException ex) {
            throw new PushSealException(Reason.AEAD, "tag mismatch");
        } finally {
            Arrays.fill(dh, (byte) 0);
            Arrays.fill(okm, (byte) 0);
        }
        try {
            int len = ((pt[0] & 0xff) << 8) | (pt[1] & 0xff);
            if (len > MAX_J) {
                throw new PushSealException(Reason.TOO_LONG, "length prefix exceeds PAD - 2");
            }
            for (int i = 2 + len; i < PAD; i++) {
                if (pt[i] != 0) {
                    throw new PushSealException(Reason.BAD_PADDING, "non-zero padding");
                }
            }
            return parseFrame(Arrays.copyOfRange(pt, 2, 2 + len), clearType);
        } finally {
            Arrays.fill(pt, (byte) 0);
        }
    }

    /**
     * The frame rules over {@code J}, in the class doc's order from {@code BAD_JSON} to {@code BAD_INNER_V}.
     *
     * @return the map in {@code J}'s key order, unmodifiable
     */
    static Map<String, String> parseFrame(byte[] j, String clearType) throws PushSealException {
        final String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(j)).toString();
        } catch (CharacterCodingException ex) {
            throw new PushSealException(Reason.BAD_JSON, "J is not UTF-8");
        }
        Map<String, String> m = new LinkedHashMap<>();
        boolean nonString = false;
        try (JsonParser p = JSON.createParser(text)) {
            if (p.nextToken() != JsonToken.START_OBJECT) {
                throw new PushSealException(Reason.BAD_JSON, "J is not an object");
            }
            Set<String> seen = new HashSet<>();
            JsonToken t;
            while ((t = p.nextToken()) == JsonToken.FIELD_NAME) {
                String k = p.currentName();
                if (!seen.add(k)) {
                    throw new PushSealException(Reason.DUPLICATE_KEY, "duplicate key");
                }
                JsonToken v = p.nextToken();
                if (v == JsonToken.VALUE_STRING) {
                    m.put(k, p.getText());
                } else {
                    nonString = true;
                    p.skipChildren();
                }
            }
            if (t != JsonToken.END_OBJECT || p.nextToken() != null) {
                throw new PushSealException(Reason.BAD_JSON, "J is not one object");
            }
        } catch (IOException ex) {
            throw new PushSealException(Reason.BAD_JSON, "J does not parse");
        }
        if (nonString) {
            throw new PushSealException(Reason.NON_STRING, "a value is not a string");
        }
        if (!jcs(m).equals(text)) {
            throw new PushSealException(Reason.NOT_JCS, "J is not the JCS of its object");
        }
        if (!clearType.equals(m.get(FIELD_TYPE))) {
            throw new PushSealException(Reason.TYPE_MISMATCH, "inner type differs from the clear type");
        }
        if (!INNER_V.equals(m.get(FIELD_V))) {
            throw new PushSealException(Reason.BAD_INNER_V, "inner v is not 1");
        }
        return Collections.unmodifiableMap(m);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    /** RFC 8785 JCS of a string map, through the signed path's canonicalizer. */
    static String jcs(Map<String, String> m) {
        try {
            return SimpleRequestHelper.getCanonicalJson(m);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Strict decode of {@code sp}: the identity decoder, then the exact length. */
    private static byte[] decode(String sp) throws PushSealException {
        byte[] blob = ChatSignatureVerifier.decodeStrictOrNull(sp);
        if (blob == null) {
            throw new PushSealException(Reason.BAD_ENCODING, "sp is not canonical Base64URL");
        }
        if (blob.length != BLOB_LEN) {
            throw new PushSealException(Reason.BAD_LENGTH, "sp has the wrong decoded length");
        }
        return blob;
    }

    /** X25519(priv, pub), or null when the result is all zero (Bouncy Castle throws; checked again here). */
    static byte[] dhOrNull(byte[] priv, byte[] pub) {
        final byte[] out;
        try {
            out = CallCrypto.x25519(priv, pub);
        } catch (IllegalStateException ex) {
            return null;
        }
        int acc = 0;
        for (byte b : out) {
            acc |= b;
        }
        return acc == 0 ? null : out;
    }

    private static BigInteger littleEndian(byte[] b) {
        byte[] be = new byte[b.length];
        for (int i = 0; i < b.length; i++) {
            be[i] = b[b.length - 1 - i];
        }
        return new BigInteger(1, be);
    }

    private static byte[] filled(int v) {
        byte[] b = new byte[KEY_LEN];
        Arrays.fill(b, (byte) v);
        return b;
    }
}
