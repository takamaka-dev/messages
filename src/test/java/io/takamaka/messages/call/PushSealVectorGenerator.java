package io.takamaka.messages.call;

import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.takamaka.messages.call.PushSeal.PushSealException;
import io.takamaka.messages.call.PushSeal.Reason;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates {@code call/push_seal_vectors_v1.json} (DR-063 §8, W1): the Java reference of {@code tkm-push/v1}
 * ({@link PushSeal}) that the Dart port (rsclient-flutter {@code push_seal.dart}, W2) must reproduce byte for byte.
 * Deterministic: device scalars and ephemerals are SHA3-256 of fixed labels; every expected refusal code is written
 * here BY HAND and the generator refuses to render a case whose actual verdict differs.
 *
 * <p>Regenerate deliberately: {@code mvn -o test -Dtest=PushSealVectorFileTest -Dpush.seal.vectors.write=true}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class PushSealVectorGenerator {

    private PushSealVectorGenerator() {
    }

    /** Non-ASCII is written as {@code \\uXXXX}: the file stays ASCII; {@code j_hex} carries the exact bytes. */
    static final ObjectMapper M = JsonMapper.builder().enable(JsonWriteFeature.ESCAPE_NON_ASCII).build();

    static final String RING = "CALL_RING";
    static final String ANSWERED = "CALL_ANSWERED";
    static final String CANCELLED = "CALL_CANCELLED";
    static final String MEET = "CALL_MEET";
    static final List<String> TYPES = List.of(RING, ANSWERED, CANCELLED, MEET);

    static final String DEV1 = "dev-1";
    static final String DEV2 = "dev-2";

    /** The opener's refusal order (PushSeal class doc). */
    static final List<Reason> OPEN_ORDER = List.of(Reason.BAD_ENCODING, Reason.BAD_LENGTH, Reason.BAD_VERSION,
            Reason.KID_MISMATCH, Reason.LOW_ORDER, Reason.AEAD, Reason.TOO_LONG, Reason.BAD_PADDING, Reason.BAD_JSON,
            Reason.DUPLICATE_KEY, Reason.NON_STRING, Reason.NOT_JCS, Reason.TYPE_MISMATCH, Reason.BAD_INNER_V);
    /** The push-key refusal order ({@link PushSeal#validatePushKey}). */
    static final List<Reason> KEY_ORDER = List.of(Reason.BAD_ENCODING, Reason.BAD_KEY_LENGTH, Reason.HIGH_BIT,
            Reason.NON_CANONICAL, Reason.LOW_ORDER);

    static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /** The raw (unclamped) device scalar of a named device. */
    static byte[] d(String device) {
        return CallCrypto.h(ascii("tkm-push/v1/vectors/device/" + device));
    }

    static byte[] pk(String device) {
        return CallCrypto.x25519Public(d(device));
    }

    /** The raw (unclamped) ephemeral scalar of a named case. */
    static byte[] e(String caseName) {
        return CallCrypto.h(ascii("tkm-push/v1/vectors/ephemeral/" + caseName));
    }

    // ---- the four example plaintexts --------------------------------------------------------------------------------

    static JsonNode resource(String path) throws Exception {
        try (InputStream in = PushSealVectorGenerator.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            return M.readTree(in);
        }
    }

    static Map<String, String> dataOf(String path, String caseName) throws Exception {
        for (JsonNode c : resource(path).get("cases")) {
            if (caseName.equals(c.get("name").asText())) {
                Map<String, String> m = new LinkedHashMap<>();
                Iterator<Map.Entry<String, JsonNode>> it = c.get("data").fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> en = it.next();
                    m.put(en.getKey(), en.getValue().asText());
                }
                return m;
            }
        }
        throw new IllegalStateException("no case " + caseName + " in " + path);
    }

    /** CALL_RING as FcmService builds it: type, call_id, svc, f, mode, v = "1", ring (call_ring_push_vector_v1 one_callee). */
    static Map<String, String> ringMap() throws Exception {
        return dataOf("/call/call_ring_push_vector_v1.json", "one_callee");
    }

    /** CALL_RING with 25 callees (call_ring_push_vector_v1 twenty_five_callees). */
    static Map<String, String> ring25Map() throws Exception {
        return dataOf("/call/call_ring_push_vector_v1.json", "twenty_five_callees");
    }

    /** CALL_ANSWERED: type, call_id, v. */
    static Map<String, String> answeredMap() throws Exception {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("type", ANSWERED);
        m.put("call_id", ringMap().get("call_id"));
        m.put("v", "1");
        return m;
    }

    /** CALL_CANCELLED: type, call_id, f, v, ring = the signed cancel (call_cancel_vector_v1 one_callee). */
    static Map<String, String> cancelledMap() throws Exception {
        return dataOf("/call/call_cancel_vector_v1.json", "one_callee");
    }

    /** CALL_MEET: a SYNTHETIC example — frame only, not a type contract (the meet push is not designed yet). */
    static Map<String, String> meetMap() throws Exception {
        Map<String, String> r = ringMap();
        Map<String, String> m = new LinkedHashMap<>();
        m.put("type", MEET);
        m.put("call_id", r.get("call_id"));
        m.put("svc", r.get("svc"));
        m.put("f", r.get("f"));
        m.put("mode", CallConstants.MODE_CONFERENCE);
        m.put("v", "1");
        return m;
    }

    /** A synthetic CALL_MEET frame with a non-ASCII value: JCS emits it as raw UTF-8. */
    static Map<String, String> nonAsciiMap() throws Exception {
        Map<String, String> m = meetMap();
        m.put("note", "café ☎ 日本");
        return m;
    }

    static Map<String, String> mapOf(String type) throws Exception {
        return switch (type) {
            case RING -> ringMap();
            case ANSWERED -> answeredMap();
            case CANCELLED -> cancelledMap();
            case MEET -> meetMap();
            default -> throw new IllegalArgumentException(type);
        };
    }

    // ---- raw builders for refusal cases -----------------------------------------------------------------------------

    static byte[] blob(String sp) {
        byte[] b = io.takamaka.messages.utils.ChatSignatureVerifier.decodeStrictOrNull(sp);
        if (b == null) {
            throw new IllegalStateException("not canonical");
        }
        return b;
    }

    static String enc(byte[] b) {
        return CallSignatures.urlBase64(b);
    }

    /** Seals a caller-built J (no frame check) under {@code aadType} to dev-1. */
    static String sealJ(String caseName, String aadType, byte[] j) throws PushSealException {
        return PushSeal.sealPlaintext(pk(DEV1), PushSeal.VERSION, aadType, PushSeal.plaintext(j), e(caseName));
    }

    static String sealJ(String caseName, String aadType, String j) throws PushSealException {
        return sealJ(caseName, aadType, utf8(j));
    }

    static String sealPt(String caseName, int version, String aadType, byte[] pt) throws PushSealException {
        return PushSeal.sealPlaintext(pk(DEV1), version, aadType, pt, e(caseName));
    }

    static String jcs(Map<String, String> m) {
        return PushSeal.jcs(m);
    }

    /** The canonical CALL_ANSWERED J, used as the base of most refusal cases. */
    static String answeredJ() throws Exception {
        return jcs(answeredMap());
    }

    // ---- rendering --------------------------------------------------------------------------------------------------

    static String hex(byte[] b) {
        return CallBytes.hex(b);
    }

    /** The verdict of {@link PushSeal#open}: "OK" or the reason name. */
    static String verdict(String device, String clearType, String sp) {
        try {
            PushSeal.open(d(device), clearType, sp);
            return "OK";
        } catch (PushSealException ex) {
            return ex.getReason().name();
        }
    }

    static String keyVerdict(String b64) {
        try {
            PushSeal.validatePushKey(b64);
            return "OK";
        } catch (PushSealException ex) {
            return ex.getReason().name();
        }
    }

    private static void sealCase(ArrayNode out, String name, String note, String type, Map<String, String> m)
            throws Exception {
        byte[] pkD = pk(DEV1);
        byte[] e = e(name);
        String sp = PushSeal.seal(pkD, type, m, e);
        byte[] epk = CallCrypto.x25519Public(e);
        byte[] hdr = PushSeal.header(PushSeal.VERSION, PushSeal.kid(pkD), epk);
        byte[] dh = CallCrypto.x25519(e, pkD);
        byte[] okm = PushSeal.keySchedule(dh, epk, pkD);
        byte[] j = PushSeal.frame(m);
        ObjectNode c = out.addObject();
        c.put("name", name);
        c.put("note", note);
        c.put("key", DEV1);
        c.put("type", type);
        c.put("e_hex", hex(e));
        c.put("epk_hex", hex(epk));
        c.put("hdr_hex", hex(hdr));
        c.put("dh_hex", hex(dh));
        c.put("okm_hex", hex(okm));
        c.put("key_hex", hex(Arrays.copyOfRange(okm, 0, 32)));
        c.put("nonce_hex", hex(Arrays.copyOfRange(okm, 32, 44)));
        c.put("aad_hex", hex(PushSeal.aad(type, hdr)));
        ObjectNode mm = c.putObject("m");
        m.forEach(mm::put);
        c.put("j", new String(j, StandardCharsets.UTF_8));
        c.put("j_hex", hex(j));
        c.put("j_len", j.length);
        c.put("sp", sp);
        ObjectNode data = c.putObject("data");
        data.put(PushSeal.FIELD_TYPE, type);
        data.put(PushSeal.FIELD_V, PushSeal.WIRE_V);
        data.put(PushSeal.FIELD_SP, sp);
        c.put("data_bytes", M.writeValueAsString(data).length());
        String v = verdict(DEV1, type, sp);
        if (!"OK".equals(v)) {
            throw new IllegalStateException(name + ": does not open: " + v);
        }
        c.put("expect", "OK");
    }

    private static void refusal(ArrayNode out, String name, String note, String device, String clearType, String sp,
            Reason expect, byte[] jOrNull) {
        String v = verdict(device, clearType, sp);
        if (!expect.name().equals(v)) {
            throw new IllegalStateException(name + ": expected " + expect + ", got " + v);
        }
        ObjectNode c = out.addObject();
        c.put("name", name);
        c.put("note", note);
        c.put("key", device);
        c.put("clear_type", clearType);
        if (jOrNull != null) {
            c.put("j_hex", hex(jOrNull));
        }
        c.put("sp", sp);
        c.put("expect", expect.name());
    }

    private static void keyCase(ArrayNode out, String name, String note, String b64, String expect) {
        String v = keyVerdict(b64);
        if (!expect.equals(v)) {
            throw new IllegalStateException(name + ": expected " + expect + ", got " + v);
        }
        ObjectNode c = out.addObject();
        c.put("name", name);
        c.put("note", note);
        c.put("push_key", b64);
        c.put("expect", expect);
        if ("OK".equals(expect)) {
            c.put("push_key_id", PushSeal.kidHex(blob32(b64)));
        }
    }

    private static byte[] blob32(String b64) {
        return io.takamaka.messages.utils.ChatSignatureVerifier.decodeStrictOrNull(b64);
    }

    static byte[] le(BigInteger u) {
        byte[] be = u.toByteArray();
        byte[] out = new byte[32];
        for (int i = 0; i < 32 && i < be.length; i++) {
            out[i] = be[be.length - 1 - i];
        }
        return out;
    }

    private static String swapAlphabet(String s) {
        if (s.indexOf('-') < 0 && s.indexOf('_') < 0) {
            throw new IllegalStateException("no URL-only character to swap");
        }
        return s.replace('-', '+').replace('_', '/');
    }

    /** A device whose push key's Base64URL text contains '-' or '_' (deterministic search). */
    private static String keyWithUrlChar() {
        for (int i = 0;; i++) {
            String s = enc(CallCrypto.x25519Public(d("alphabet-" + i)));
            if (s.indexOf('-') >= 0 || s.indexOf('_') >= 0) {
                return s;
            }
        }
    }

    public static String render() throws Exception {
        ObjectNode root = M.createObjectNode();
        root.put("_origin", "Messages PushSealVectorGenerator (DR-063 draft 1, W1, 2026-11-24): the Java reference of "
                + "the sealed call push tkm-push/v1 (io.takamaka.messages.call.PushSeal). Construction: design "
                + "F2_SEALED_CALL_PUSH_DESIGN_2026-11-04 §1.2, one PAD = 2048 (DR-063 §1). Every expected code below "
                + "was written by hand in the generator and checked against the implementation before rendering.");
        root.put("version", "1.0.0");
        root.put("suite", PushSeal.SUITE);
        root.put("version_byte", PushSeal.VERSION);
        root.put("pad", PushSeal.PAD);
        root.put("max_j", PushSeal.MAX_J);
        root.put("kid_len", PushSeal.KID_LEN);
        root.put("header_len", PushSeal.HEADER_LEN);
        root.put("tag_len", PushSeal.TAG_LEN);
        root.put("okm_len", PushSeal.OKM_LEN);
        root.put("blob_len", PushSeal.BLOB_LEN);
        root.put("sp_len", PushSeal.SP_LEN);
        root.put("inner_v", PushSeal.INNER_V);
        root.put("wire_v", PushSeal.WIRE_V);
        ObjectNode labels = root.putObject("labels");
        labels.put("kid", PushSeal.KID_LABEL);
        labels.put("kid_hex", hex(ascii(PushSeal.KID_LABEL)));
        labels.put("seal_info", PushSeal.SEAL_INFO);
        labels.put("seal_info_hex", hex(ascii(PushSeal.SEAL_INFO)));
        labels.put("aad", PushSeal.AAD_LABEL);
        labels.put("aad_hex", hex(ascii(PushSeal.AAD_LABEL)));
        root.put("low_order_check_scalar_hex", hex(PushSeal.LOW_ORDER_CHECK_SCALAR));
        root.put("p_le_hex", hex(le(PushSeal.P)));
        ArrayNode oo = root.putArray("open_refusal_order");
        OPEN_ORDER.forEach(r -> oo.add(r.name()));
        ArrayNode ko = root.putArray("push_key_refusal_order");
        KEY_ORDER.forEach(r -> ko.add(r.name()));
        ArrayNode notes = root.putArray("notes");
        notes.add("kid = SHA3-256 (FIPS 202, NOT Keccak-256) over ASCII('tkm-push/v1/kid') (15 bytes, labels.kid_hex) "
                + "|| pkD, truncated to the first 8 bytes. push_key_id (registration echo, DB) = lower hex of kid, "
                + "16 characters.");
        notes.add("d (keys.*.d_hex) and e (seal_cases[].e_hex) are RAW 32-byte scalars BEFORE clamping; clamping is "
                + "RFC 7748 §5, inside X25519, on both sides. pkD = X25519(d, 9), epk = X25519(e, 9). d = SHA3-256("
                + "ASCII('tkm-push/v1/vectors/device/' + name)), e = SHA3-256(ASCII('tkm-push/v1/vectors/ephemeral/' "
                + "+ case name)).");
        notes.add("hdr = 0x01 || kid || epk (41 B); dh = X25519(e, pkD); okm = HKDF-SHA-512(ikm = dh, salt = epk || "
                + "pkD, info = ASCII('tkm-push/v1/seal'), L = 44) = key 32 || nonce 12; aad = ASCII('tkm-push/v1/aad')"
                + " || 0x00 || UTF-8(clear type) || 0x00 || hdr; pt = u16be(len J) || J || zeros to 2048; "
                + "sp = Base64URL(hdr || AES-256-GCM(key, nonce, aad, pt)), '.' padding (2105 bytes -> 2808 chars, "
                + "exactly one '.').");
        notes.add("J = UTF-8 of RFC 8785 JCS of the string map (org.erdtman.jcs.JsonCanonicalizer, the signed path's "
                + "canonicalizer, also CallRschat.pushRing's). Non-ASCII is raw UTF-8 in J; this file escapes it as "
                + "\\uXXXX at the JSON level only, j_hex is authoritative.");
        notes.add("sp decoding: strict Base64URL, the identity decoder (ChatSignatureVerifier.decodeStrictOrNull): URL "
                + "alphabet only, whole 4-char blocks, '.' padding in the last block only, no whitespace, zero unused "
                + "bits. '+', '/', '=', a missing '.', any whitespace or newline -> BAD_ENCODING.");
        notes.add("Frame parse: strict UTF-8, then one left-to-right JSON parse of ONE top-level object. A repeated "
                + "top-level key throws DUPLICATE_KEY at once (so it competes with a later syntax error by position); "
                + "a non-string top-level value is reported as NON_STRING only after a complete, duplicate-free parse;"
                + " then NOT_JCS (J bytes != JCS of the parsed object), TYPE_MISMATCH (inner type absent or != clear "
                + "type), BAD_INNER_V (inner v absent or != '1'). Per-type key sets are NOT checked (DR-063 §2).");
        notes.add("push key: strict Base64URL ('.' padding) -> BAD_ENCODING; not 32 bytes -> BAD_KEY_LENGTH; bit 255 "
                + "set (never masked) -> HIGH_BIT; little-endian u >= p = 2^255-19 -> NON_CANONICAL; X25519("
                + "low_order_check_scalar, pk) all zero -> LOW_ORDER (any clamped scalar is a multiple of the "
                + "cofactor 8). The same byte checks run on pkD at seal time.");
        notes.add("An open with an all-zero X25519(d, epk) is LOW_ORDER, between KID_MISMATCH and AEAD.");

        ObjectNode keys = root.putObject("keys");
        for (String dev : List.of(DEV1, DEV2)) {
            ObjectNode k = keys.putObject(dev);
            k.put("d_hex", hex(d(dev)));
            k.put("pk_hex", hex(pk(dev)));
            k.put("pk_b64url", enc(pk(dev)));
            k.put("kid_hex", PushSeal.kidHex(pk(dev)));
        }

        // ---- seal cases ----
        ArrayNode seals = root.putArray("seal_cases");
        sealCase(seals, "seal_call_ring", "CALL_RING as FcmService builds it (call_ring_push_vector_v1 one_callee)",
                RING, ringMap());
        sealCase(seals, "seal_call_answered", "CALL_ANSWERED {type, call_id, v}", ANSWERED, answeredMap());
        sealCase(seals, "seal_call_cancelled", "CALL_CANCELLED {type, call_id, f, v, ring = the signed cancel of "
                + "call_cancel_vector_v1 one_callee}", CANCELLED, cancelledMap());
        sealCase(seals, "seal_call_meet", "CALL_MEET: SYNTHETIC example, frame only, not a type contract", MEET,
                meetMap());
        sealCase(seals, "seal_call_ring_25", "CALL_RING with 25 callees (call_ring_push_vector_v1 "
                + "twenty_five_callees): same sp length as the 1-callee ring", RING, ring25Map());
        sealCase(seals, "seal_non_ascii", "SYNTHETIC CALL_MEET frame with a non-ASCII value: raw UTF-8 in J (JCS), "
                + "frame only", MEET, nonAsciiMap());

        Map<String, String> sealed = new LinkedHashMap<>();
        for (JsonNode c : seals) {
            if (!c.get("name").asText().equals("seal_call_ring_25") && !c.get("name").asText().equals("seal_non_ascii")) {
                sealed.put(c.get("type").asText(), c.get("sp").asText());
            }
        }

        // ---- relabel cases: every ordered pair ----
        ArrayNode relabel = root.putArray("relabel_cases");
        for (String from : TYPES) {
            for (String to : TYPES) {
                if (from.equals(to)) {
                    continue;
                }
                String name = "relabel_" + from + "_as_" + to;
                String sp = sealed.get(from);
                String v = verdict(DEV1, to, sp);
                if (!Reason.AEAD.name().equals(v)) {
                    throw new IllegalStateException(name + ": " + v);
                }
                ObjectNode c = relabel.addObject();
                c.put("name", name);
                c.put("key", DEV1);
                c.put("sealed_type", from);
                c.put("clear_type", to);
                c.put("sp", sp);
                c.put("expect", Reason.AEAD.name());
            }
        }

        // ---- refusal cases, in the opener's order ----
        ArrayNode ref = root.putArray("refusal_cases");
        String base = sealed.get(ANSWERED);
        byte[] baseBlob = blob(base);
        String aJ = answeredJ();
        String callId = answeredMap().get("call_id");

        // BAD_ENCODING
        refusal(ref, "enc_std_alphabet", "'-'/'_' replaced by '+'/'/', '.' padding kept", DEV1, ANSWERED,
                swapAlphabet(base), Reason.BAD_ENCODING, null);
        refusal(ref, "enc_eq_padding", "'=' instead of '.'", DEV1, ANSWERED,
                base.substring(0, base.length() - 1) + "=", Reason.BAD_ENCODING, null);
        refusal(ref, "enc_missing_padding", "the '.' dropped (2807 chars)", DEV1, ANSWERED,
                base.substring(0, base.length() - 1), Reason.BAD_ENCODING, null);
        refusal(ref, "enc_trailing_newline", "a trailing line feed", DEV1, ANSWERED, base + "\n",
                Reason.BAD_ENCODING, null);
        refusal(ref, "enc_embedded_newline", "a line feed after 76 characters", DEV1, ANSWERED,
                base.substring(0, 76) + "\n" + base.substring(76), Reason.BAD_ENCODING, null);
        refusal(ref, "enc_leading_space", "a leading space", DEV1, ANSWERED, " " + base, Reason.BAD_ENCODING, null);
        refusal(ref, "enc_std_base64", "java.util.Base64 standard form of the same bytes ('=' padding)", DEV1,
                ANSWERED, java.util.Base64.getEncoder().encodeToString(baseBlob), Reason.BAD_ENCODING, null);
        {
            // the last data character has 2 unused bits: set one
            char last = base.charAt(base.length() - 2);
            String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
            char bad = alphabet.charAt(alphabet.indexOf(last) ^ 1);
            refusal(ref, "enc_nonzero_unused_bits", "the unused low bits of the last data character are not zero",
                    DEV1, ANSWERED, base.substring(0, base.length() - 2) + bad + ".", Reason.BAD_ENCODING, null);
        }
        refusal(ref, "enc_dot_inside", "a '.' in the middle", DEV1, ANSWERED,
                base.substring(0, 100) + "." + base.substring(101), Reason.BAD_ENCODING, null);

        // BAD_LENGTH
        refusal(ref, "len_short", "3 bytes missing at the end (2102 bytes)", DEV1, ANSWERED,
                enc(Arrays.copyOf(baseBlob, PushSeal.BLOB_LEN - 3)), Reason.BAD_LENGTH, null);
        refusal(ref, "len_trailing_bytes", "3 zero bytes appended (2108 bytes)", DEV1, ANSWERED,
                enc(Arrays.copyOf(baseBlob, PushSeal.BLOB_LEN + 3)), Reason.BAD_LENGTH, null);
        refusal(ref, "len_one_trailing_byte", "1 byte appended (2106 bytes, no padding character)", DEV1, ANSWERED,
                enc(Arrays.copyOf(baseBlob, PushSeal.BLOB_LEN + 1)), Reason.BAD_LENGTH, null);
        refusal(ref, "len_empty", "the empty string (0 bytes)", DEV1, ANSWERED, "", Reason.BAD_LENGTH, null);
        {
            byte[] b = Arrays.copyOf(baseBlob, PushSeal.BLOB_LEN + 1);
            b[0] = 0x02;
            refusal(ref, "two_faults_version_and_length", "version 0x02 AND one trailing byte: BAD_LENGTH wins",
                    DEV1, ANSWERED, enc(b), Reason.BAD_LENGTH, null);
        }

        // BAD_VERSION
        refusal(ref, "version_02", "a well-formed seal with version byte 0x02", DEV1, ANSWERED,
                sealPt("version_02", 0x02, ANSWERED, PushSeal.plaintext(utf8(aJ))), Reason.BAD_VERSION, null);
        refusal(ref, "version_00", "a well-formed seal with version byte 0x00", DEV1, ANSWERED,
                sealPt("version_00", 0x00, ANSWERED, PushSeal.plaintext(utf8(aJ))), Reason.BAD_VERSION, null);
        {
            byte[] b = baseBlob.clone();
            b[0] = 0x02;
            b[1] ^= 0x01;
            refusal(ref, "two_faults_version_and_kid", "version 0x02 AND a wrong kid: BAD_VERSION wins", DEV1, ANSWERED,
                    enc(b), Reason.BAD_VERSION, null);
        }

        // KID_MISMATCH
        refusal(ref, "kid_other_device", "sealed to dev-1, opened with dev-2", DEV2, ANSWERED, base,
                Reason.KID_MISMATCH, null);
        {
            byte[] b = baseBlob.clone();
            b[1] ^= 0x01;
            refusal(ref, "kid_flipped", "one kid bit flipped (the AAD would fail too: KID_MISMATCH wins)", DEV1,
                    ANSWERED, enc(b), Reason.KID_MISMATCH, null);
        }

        // LOW_ORDER
        {
            byte[] b = baseBlob.clone();
            Arrays.fill(b, 1 + PushSeal.KID_LEN, PushSeal.HEADER_LEN, (byte) 0);
            refusal(ref, "low_order_epk_zero", "epk replaced by u = 0: X25519(d, epk) all zero", DEV1, ANSWERED, enc(b),
                    Reason.LOW_ORDER, null);
            byte[] c = baseBlob.clone();
            System.arraycopy(ORDER8, 0, c, 1 + PushSeal.KID_LEN, 32);
            refusal(ref, "low_order_epk_order8", "epk replaced by a point of order 8", DEV1, ANSWERED, enc(c),
                    Reason.LOW_ORDER, null);
        }

        // AEAD
        {
            byte[] b = baseBlob.clone();
            b[PushSeal.HEADER_LEN + 5] ^= 0x01;
            refusal(ref, "aead_ciphertext_bit", "one ciphertext bit flipped", DEV1, ANSWERED, enc(b), Reason.AEAD, null);
            byte[] t = baseBlob.clone();
            t[PushSeal.BLOB_LEN - 1] ^= (byte) 0x80;
            refusal(ref, "aead_tag_bit", "one tag bit flipped", DEV1, ANSWERED, enc(t), Reason.AEAD, null);
            byte[] x = baseBlob.clone();
            System.arraycopy(pk(DEV2), 0, x, 1 + PushSeal.KID_LEN, 32);
            refusal(ref, "aead_epk_swapped", "epk replaced by another valid point (kid unchanged)", DEV1, ANSWERED,
                    enc(x), Reason.AEAD, null);
        }

        // TOO_LONG
        {
            byte[] pt = new byte[PushSeal.PAD];
            pt[0] = (byte) (2047 >>> 8);
            pt[1] = (byte) 2047;
            byte[] j = utf8(aJ);
            System.arraycopy(j, 0, pt, 2, j.length);
            refusal(ref, "too_long_2047", "length prefix 2047 = PAD - 1", DEV1, ANSWERED,
                    sealPt("too_long_2047", PushSeal.VERSION, ANSWERED, pt), Reason.TOO_LONG, null);
            byte[] p2 = pt.clone();
            p2[0] = (byte) 0xff;
            p2[1] = (byte) 0xff;
            refusal(ref, "too_long_ffff", "length prefix 0xffff", DEV1, ANSWERED,
                    sealPt("too_long_ffff", PushSeal.VERSION, ANSWERED, p2), Reason.TOO_LONG, null);
            byte[] p3 = PushSeal.plaintext(j);
            p3[0] = (byte) (2047 >>> 8);
            p3[1] = (byte) 2047;
            p3[PushSeal.PAD - 1] = 1;
            refusal(ref, "two_faults_too_long_and_padding", "prefix 2047 AND a non-zero last byte: TOO_LONG wins",
                    DEV1, ANSWERED, sealPt("two_faults_too_long_and_padding", PushSeal.VERSION, ANSWERED, p3),
                    Reason.TOO_LONG, null);
        }

        // BAD_PADDING
        {
            byte[] j = utf8(aJ);
            byte[] p1 = PushSeal.plaintext(j);
            p1[PushSeal.PAD - 1] = 0x01;
            refusal(ref, "padding_last_byte", "the last padding byte is 0x01", DEV1, ANSWERED,
                    sealPt("padding_last_byte", PushSeal.VERSION, ANSWERED, p1), Reason.BAD_PADDING, j);
            byte[] p2 = PushSeal.plaintext(j);
            p2[2 + j.length] = 0x20;
            refusal(ref, "padding_first_byte", "the byte right after J is a space (the prefix is honoured)", DEV1,
                    ANSWERED, sealPt("padding_first_byte", PushSeal.VERSION, ANSWERED, p2), Reason.BAD_PADDING, j);
            byte[] p3 = PushSeal.plaintext(utf8("{}"));
            p3[2 + 2] = 0x01;
            refusal(ref, "two_faults_padding_and_json", "non-zero padding AND J = {} (no type): BAD_PADDING wins",
                    DEV1, ANSWERED, sealPt("two_faults_padding_and_json", PushSeal.VERSION, ANSWERED, p3),
                    Reason.BAD_PADDING, utf8("{}"));
        }

        // BAD_JSON
        jsonRefusal(ref, "json_not_json", "J = not json", "not json", Reason.BAD_JSON);
        jsonRefusal(ref, "json_array", "J is a top-level array", "[\"" + ANSWERED + "\"]", Reason.BAD_JSON);
        jsonRefusal(ref, "json_string", "J is a top-level string", "\"" + ANSWERED + "\"", Reason.BAD_JSON);
        jsonRefusal(ref, "json_empty", "len(J) = 0", "", Reason.BAD_JSON);
        jsonRefusal(ref, "json_trailing", "trailing content after the object", aJ + "x", Reason.BAD_JSON);
        jsonRefusal(ref, "json_two_objects", "two objects", aJ + "{}", Reason.BAD_JSON);
        jsonRefusal(ref, "json_unterminated", "the closing brace missing", aJ.substring(0, aJ.length() - 1),
                Reason.BAD_JSON);
        jsonRefusal(ref, "json_bom", "a UTF-8 byte order mark before the object", "﻿" + aJ, Reason.BAD_JSON);
        {
            byte[] good = utf8("{\"call_id\":\"" + callId + "\",\"type\":\"" + ANSWERED + "\",\"v\":\"1\",\"x\":\"ab\"}");
            byte[] bad = good.clone();
            int at = new String(good, StandardCharsets.UTF_8).indexOf("\"ab\"") + 1;
            bad[at] = (byte) 0xc3;
            bad[at + 1] = (byte) 0x28;
            refusal(ref, "json_invalid_utf8", "an invalid UTF-8 sequence (C3 28) inside a string", DEV1, ANSWERED,
                    sealJ("json_invalid_utf8", ANSWERED, bad), Reason.BAD_JSON, bad);
        }
        jsonRefusal(ref, "two_faults_json_and_nonstring", "a number value AND trailing content: BAD_JSON wins",
                "{\"call_id\":1,\"type\":\"" + ANSWERED + "\",\"v\":\"1\"}x", Reason.BAD_JSON);

        // DUPLICATE_KEY
        jsonRefusal(ref, "dup_type", "type twice",
                "{\"call_id\":\"" + callId + "\",\"type\":\"" + ANSWERED + "\",\"type\":\"" + ANSWERED + "\",\"v\":\"1\"}",
                Reason.DUPLICATE_KEY);
        {
            Map<String, String> many = new LinkedHashMap<>(cancelledMap());
            for (int i = 0; i < 30; i++) {
                many.put(String.format("x%02d", i), "value-" + i);
            }
            String j = jcs(many);
            String dupJ = j.substring(0, j.length() - 1) + ",\"x05\":\"value-5\"}";
            jsonRefusal(ref, "dup_last_of_many", "35 keys in JCS order, then x05 repeated as the LAST key", dupJ,
                    Reason.DUPLICATE_KEY);
        }
        jsonRefusal(ref, "two_faults_nonstring_then_duplicate", "a number value, then a duplicate key: DUPLICATE_KEY "
                + "wins (non-string is reported only after a complete parse)",
                "{\"call_id\":1,\"call_id\":\"" + callId + "\",\"type\":\"" + ANSWERED + "\",\"v\":\"1\"}",
                Reason.DUPLICATE_KEY);
        jsonRefusal(ref, "two_faults_duplicate_then_syntax", "a duplicate key, then a syntax error: DUPLICATE_KEY "
                + "wins (detected while parsing, by position)",
                "{\"type\":\"" + ANSWERED + "\",\"type\":\"" + ANSWERED + "\",\"v\":\"1\"",
                Reason.DUPLICATE_KEY);

        // NON_STRING
        jsonRefusal(ref, "nonstring_number", "call_id is a number",
                "{\"call_id\":1,\"type\":\"" + ANSWERED + "\",\"v\":\"1\"}", Reason.NON_STRING);
        jsonRefusal(ref, "nonstring_null", "call_id is null",
                "{\"call_id\":null,\"type\":\"" + ANSWERED + "\",\"v\":\"1\"}", Reason.NON_STRING);
        jsonRefusal(ref, "nonstring_bool", "call_id is true",
                "{\"call_id\":true,\"type\":\"" + ANSWERED + "\",\"v\":\"1\"}", Reason.NON_STRING);
        jsonRefusal(ref, "nonstring_object", "call_id is an object",
                "{\"call_id\":{\"a\":\"b\"},\"type\":\"" + ANSWERED + "\",\"v\":\"1\"}", Reason.NON_STRING);
        jsonRefusal(ref, "nonstring_array", "call_id is an array",
                "{\"call_id\":[\"" + callId + "\"],\"type\":\"" + ANSWERED + "\",\"v\":\"1\"}", Reason.NON_STRING);
        jsonRefusal(ref, "nonstring_v_number", "v is the number 1: NON_STRING, not BAD_INNER_V",
                "{\"call_id\":\"" + callId + "\",\"type\":\"" + ANSWERED + "\",\"v\":1}", Reason.NON_STRING);
        jsonRefusal(ref, "two_faults_nonstring_and_unsorted", "a number value AND unsorted keys: NON_STRING wins",
                "{\"v\":\"1\",\"type\":\"" + ANSWERED + "\",\"call_id\":1}", Reason.NON_STRING);

        // NOT_JCS
        jsonRefusal(ref, "notjcs_unsorted", "keys not in JCS order",
                "{\"type\":\"" + ANSWERED + "\",\"call_id\":\"" + callId + "\",\"v\":\"1\"}", Reason.NOT_JCS);
        jsonRefusal(ref, "notjcs_whitespace", "a space after a colon",
                "{\"call_id\": \"" + callId + "\",\"type\":\"" + ANSWERED + "\",\"v\":\"1\"}", Reason.NOT_JCS);
        jsonRefusal(ref, "notjcs_leading_space", "a space before the object", " " + aJ, Reason.NOT_JCS);
        jsonRefusal(ref, "notjcs_trailing_newline", "a line feed after the object", aJ + "\n", Reason.NOT_JCS);
        jsonRefusal(ref, "notjcs_escaped_non_ascii", "e-acute written as the escape \\u00e9 where JCS emits raw UTF-8",
                "{\"call_id\":\"" + callId + "\",\"note\":\"caf\\u00e9\",\"type\":\"" + ANSWERED + "\",\"v\":\"1\"}",
                Reason.NOT_JCS);
        jsonRefusal(ref, "notjcs_escaped_solidus", "'/' written as \\/ where JCS emits '/'",
                "{\"call_id\":\"" + callId + "\",\"note\":\"a\\/b\",\"type\":\"" + ANSWERED + "\",\"v\":\"1\"}",
                Reason.NOT_JCS);
        jsonRefusal(ref, "two_faults_notjcs_and_type", "unsorted keys AND a wrong inner type: NOT_JCS wins",
                "{\"type\":\"" + RING + "\",\"call_id\":\"" + callId + "\",\"v\":\"1\"}", Reason.NOT_JCS);

        // TYPE_MISMATCH
        jsonRefusal(ref, "type_inner_differs", "inner type CALL_RING under clear (and AAD) type CALL_ANSWERED",
                "{\"call_id\":\"" + callId + "\",\"type\":\"" + RING + "\",\"v\":\"1\"}", Reason.TYPE_MISMATCH);
        jsonRefusal(ref, "type_absent", "no inner type",
                "{\"call_id\":\"" + callId + "\",\"v\":\"1\"}", Reason.TYPE_MISMATCH);
        jsonRefusal(ref, "type_lower_case", "inner type in lower case",
                "{\"call_id\":\"" + callId + "\",\"type\":\"call_answered\",\"v\":\"1\"}", Reason.TYPE_MISMATCH);
        jsonRefusal(ref, "two_faults_type_and_v", "a wrong inner type AND v = 2: TYPE_MISMATCH wins",
                "{\"call_id\":\"" + callId + "\",\"type\":\"" + MEET + "\",\"v\":\"2\"}", Reason.TYPE_MISMATCH);

        // BAD_INNER_V
        jsonRefusal(ref, "v_2", "inner v = 2",
                "{\"call_id\":\"" + callId + "\",\"type\":\"" + ANSWERED + "\",\"v\":\"2\"}", Reason.BAD_INNER_V);
        jsonRefusal(ref, "v_absent", "no inner v",
                "{\"call_id\":\"" + callId + "\",\"type\":\"" + ANSWERED + "\"}", Reason.BAD_INNER_V);
        jsonRefusal(ref, "v_empty", "inner v is the empty string",
                "{\"call_id\":\"" + callId + "\",\"type\":\"" + ANSWERED + "\",\"v\":\"\"}", Reason.BAD_INNER_V);

        // ---- push keys ----
        ArrayNode pk = root.putArray("push_key_cases");
        String good = enc(pk(DEV1));
        keyCase(pk, "ok_dev_1", "dev-1's key", good, "OK");
        keyCase(pk, "ok_dev_2", "dev-2's key", enc(pk(DEV2)), "OK");
        String withUrl = keyWithUrlChar();
        keyCase(pk, "ok_url_characters", "a key whose text has '-' or '_'", withUrl, "OK");
        keyCase(pk, "enc_std_alphabet", "the same key with '+'/'/' ('.' kept)", swapAlphabet(withUrl),
                Reason.BAD_ENCODING.name());
        keyCase(pk, "enc_eq_padding", "'=' instead of '.'", good.substring(0, good.length() - 1) + "=",
                Reason.BAD_ENCODING.name());
        keyCase(pk, "enc_missing_padding", "the '.' dropped (43 chars)", good.substring(0, good.length() - 1),
                Reason.BAD_ENCODING.name());
        keyCase(pk, "enc_trailing_newline", "a trailing line feed", good + "\n", Reason.BAD_ENCODING.name());
        keyCase(pk, "enc_leading_space", "a leading space", " " + good, Reason.BAD_ENCODING.name());
        keyCase(pk, "enc_std_base64", "java.util.Base64 standard form ('=' padding)",
                java.util.Base64.getEncoder().encodeToString(pk(DEV1)), Reason.BAD_ENCODING.name());
        keyCase(pk, "len_31", "31 bytes", enc(Arrays.copyOf(pk(DEV1), 31)), Reason.BAD_KEY_LENGTH.name());
        keyCase(pk, "len_33", "33 bytes", enc(Arrays.copyOf(pk(DEV1), 33)), Reason.BAD_KEY_LENGTH.name());
        keyCase(pk, "len_0", "the empty string (0 bytes)", "", Reason.BAD_KEY_LENGTH.name());
        {
            byte[] hb = pk(DEV1);
            hb[31] |= (byte) 0x80;
            keyCase(pk, "high_bit", "dev-1's key with bit 255 set (never masked)", enc(hb), Reason.HIGH_BIT.name());
            byte[] hz = new byte[32];
            hz[31] = (byte) 0x80;
            keyCase(pk, "two_faults_high_bit_and_low_order", "u = 0 with bit 255 set: HIGH_BIT wins", enc(hz),
                    Reason.HIGH_BIT.name());
        }
        keyCase(pk, "non_canonical_p_plus_9", "u = p + 9 (canonical 9 in disguise)",
                enc(le(PushSeal.P.add(BigInteger.valueOf(9)))), Reason.NON_CANONICAL.name());
        keyCase(pk, "non_canonical_p", "u = p (0 in disguise): NON_CANONICAL before LOW_ORDER", enc(le(PushSeal.P)),
                Reason.NON_CANONICAL.name());
        keyCase(pk, "low_order_u0", "u = 0", enc(new byte[32]), Reason.LOW_ORDER.name());
        keyCase(pk, "low_order_u1", "u = 1", enc(le(BigInteger.ONE)), Reason.LOW_ORDER.name());
        keyCase(pk, "low_order_order8", "a point of order 8", enc(ORDER8), Reason.LOW_ORDER.name());
        keyCase(pk, "low_order_p_minus_1", "u = p - 1 (order 2)", enc(le(PushSeal.P.subtract(BigInteger.ONE))),
                Reason.LOW_ORDER.name());

        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentObjectsWith(new DefaultIndenter("  ", "\n"));
        pp.indentArraysWith(new DefaultIndenter("  ", "\n"));
        return M.writer(pp).writeValueAsString(root) + "\n";
    }

    /** A point of order 8 on Curve25519 (little-endian u). */
    static final byte[] ORDER8 = CallBytes.unhex("e0eb7a7c3b41b8ae1656e3faf19fc46ada098deb9c32b1fd866205165f49b800");

    private static void jsonRefusal(ArrayNode ref, String name, String note, String j, Reason expect)
            throws PushSealException {
        byte[] jb = utf8(j);
        refusal(ref, name, note + " (sealed under CALL_ANSWERED, opened under CALL_ANSWERED)", DEV1, ANSWERED,
                sealJ(name, ANSWERED, jb), expect, jb);
    }

    public static void main(String[] args) throws Exception {
        String text = render();
        for (String a : args) {
            Path p = Paths.get(a);
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, text);
            System.out.println("wrote " + p.toAbsolutePath() + " (" + text.length() + " chars)");
        }
    }
}
