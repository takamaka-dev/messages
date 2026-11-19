package io.takamaka.messages.call.rschat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.takamaka.messages.call.CallBytes;
import io.takamaka.messages.call.CallConstants;
import io.takamaka.messages.call.CallError;
import io.takamaka.messages.call.CallJson;
import io.takamaka.messages.call.CallProtocolException;
import io.takamaka.messages.call.CallSignatures;
import io.takamaka.messages.call.CallTestKeys;
import io.takamaka.messages.call.beans.CallChannelBodyBean;
import io.takamaka.messages.call.beans.CallRingBean;
import io.takamaka.messages.call.beans.CallSignedObject;
import io.takamaka.messages.call.epoch.Commits;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audio-only calls, slice S0 = RECEIVERS (design AUDIO_ONLY_CALLS_DESIGN_2026-11-19 §5–§7, rulings R-1/R-2/R-3;
 * protocol 1.1): the ring's optional {@code media} ({@link CallRingBean}, {@link CallRschat#checkRing}), the pairwise
 * channel body kind {@code h:"media"} ({@link CallChannelBodyBean}), and the cross-platform vector file
 * {@code call/call_audio_only_s0_vectors_v1.json} the Dart port verifies case by case: the committed copy must equal
 * what this reference generates now (drift guard); regenerate with {@code -Dcall.vectors.write=true}.
 */
class CallAudioOnlyS0Test {

    static final String RESOURCE = "/call/call_audio_only_s0_vectors_v1.json";
    static final String AUD = CallRschat.audience("test");
    static final String CALL = "ab".repeat(32);
    static final String NONCE = "00".repeat(31) + "cc";
    static final long TS = 1_700_000_000_000L;
    static final String SVC = "london-test-1";
    static final AsymmetricCipherKeyPair CALLER = CallTestKeys.identity("s0-caller");

    /**
     * A ring signed BEFORE S0 existed: {@code data.ring} of the case {@code one_callee} of
     * {@code call_ring_push_vector_v1.json} (G-6, 2026-11-03; keys g6-owner / g6-callee-1), copied here literally so
     * that a regeneration of that file cannot move the anchor.
     */
    static final String PRE_S0_RING = "{\"aud\":\"rschat:test\",\"call\":\"abababababababababababababababababababababababab"
            + "abababababababab\",\"f\":\"H4iQl65uuBgtk4XbsMCEOGrY-xX7sbR9jYjVycYJOVs.\",\"mode\":\"standard\",\"n\":\"0000000000"
            + "0000000000000000000000000000000000000000000000000000aa\",\"sg\":\"985a0070c4c6565d6d709ace5391c8744a9d05fd57bf2732"
            + "10d67eaa5aef5fe72acc459b60c80d9353973a5230b28d5865d0b1b586750662eca25746cf56fd0c\",\"svc\":\"london-test-1\",\"t\":"
            + "\"ring\",\"to\":[\"U-IYZ9wDWLHwLgTsmbmJZysPhHWhFQdvMHtQ2wqf_IY.\"],\"ts\":1700000000000,\"v\":1}";

    static String id(AsymmetricCipherKeyPair kp) {
        return CallSignatures.identityOf(kp);
    }

    static AsymmetricCipherKeyPair callee(int i) {
        return CallTestKeys.identity("s0-callee-" + i);
    }

    static List<String> callees(int n) {
        List<String> to = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            to.add(id(callee(i)));
        }
        return to;
    }

    /** A ring signed by {@code signer} with the given {@code k} and {@code media} (either may be null). */
    static CallRingBean signed(AsymmetricCipherKeyPair signer, List<String> to, String mode, String k, String media) {
        CallRingBean r = CallRschat.ring(CALL, SVC, to, mode, NONCE, AUD, TS);
        r.setK(k);
        r.setMedia(media);
        CallSignatures.sign(r, signer);
        return r;
    }

    static CallRingBean audioRing(int n) {
        return signed(CALLER, callees(n), CallConstants.MODE_STANDARD, null, CallConstants.RING_MEDIA_AUDIO);
    }

    static void refusedMedia(CallRingBean r) {
        CallProtocolException ex = assertThrows(CallProtocolException.class, () -> CallRschat.checkRing(r, CallRschat.RING_TO_MAX));
        assertEquals(CallError.BAD_SIGNATURE, ex.getError());
        assertEquals("ring media", ex.getReason());
    }

    // ---- the constants ------------------------------------------------------------------------------------------------

    @Test
    void theConstantsOfS0() {
        assertEquals("1.1", CallConstants.PROTOCOL_VERSION);
        assertEquals("1.1", CallConstants.PROTOCOL_VERSION_1_1);
        assertEquals("1.0", CallConstants.PROTOCOL_VERSION_1_0);
        assertEquals("audio", CallConstants.RING_MEDIA_AUDIO);
        assertEquals("media", CallConstants.H_MEDIA);
        assertEquals("video", CallConstants.MEDIA_VIDEO);
        assertEquals("audio", CallConstants.MEDIA_AUDIO);
        // the wire major and every label are unchanged: 1.1 is a manifest-range value only
        assertEquals(1, CallConstants.VERSION);
        assertEquals("tkm-call/v1/", CallConstants.PREFIX);
    }

    // ---- the ring field -----------------------------------------------------------------------------------------------

    @Test
    void aRingWithoutMediaIsByteIdenticalToAPreS0Ring() throws Exception {
        // the same inputs as the G-6 vector, built with today's bean: canonical form, signature and push text are equal
        AsymmetricCipherKeyPair owner = CallTestKeys.identity("g6-owner");
        CallRingBean r = CallRschat.ring(CALL, SVC, List.of(id(CallTestKeys.identity("g6-callee-1"))),
                CallConstants.MODE_STANDARD, "00".repeat(31) + "aa", AUD, TS);
        CallSignatures.sign(r, owner);
        assertNull(r.getMedia());
        assertEquals(PRE_S0_RING, CallRschat.pushRing(r));
        assertFalse(CallJson.toWire(r).contains("media"));
        // … and it is the committed vector's text
        JsonNode g6;
        try (InputStream in = CallAudioOnlyS0Test.class.getResourceAsStream("/call/call_ring_push_vector_v1.json")) {
            assertNotNull(in);
            g6 = new ObjectMapper().readTree(in);
        }
        assertEquals("one_callee", g6.get("cases").get(0).get("name").asText());
        assertEquals(PRE_S0_RING, g6.get("cases").get(0).get("data").get("ring").asText());
        // the pre-S0 text parses, verifies, is a video ring and re-serialises to itself
        CallRingBean old = CallJson.parse(PRE_S0_RING, CallRingBean.class);
        CallSignatures.verify(old, CallConstants.T_RING, AUD);
        CallRschat.checkRing(old, CallRschat.RING_TO_MAX);
        assertNull(old.getMedia());
        assertFalse(CallRschat.isAudioRing(old));
        assertEquals(PRE_S0_RING, CallRschat.pushRing(old));
    }

    @Test
    void anAudioRingIsAcceptedAndTheSignatureCoversMedia() throws Exception {
        CallRingBean r = audioRing(2);
        assertTrue(CallJson.toWire(r).contains("\"media\":\"audio\""));
        assertTrue(CallSignatures.canonical(r).contains(",\"media\":\"audio\",\"mode\":"), "JCS: media sorts before mode");
        CallRingBean back = CallJson.parse(CallJson.toWire(r), CallRingBean.class);
        CallSignatures.verify(back, CallConstants.T_RING, AUD);
        CallRschat.checkRing(back, CallRschat.RING_TO_MAX);
        assertEquals("audio", back.getMedia());
        assertTrue(CallRschat.isAudioRing(back));
        assertFalse(CallRschat.isAnswered(back));
        assertFalse(CallRschat.isCancelled(back));
        assertTrue(CallJson.parse(CallJson.toWire(r)) instanceof CallRingBean, "the t-dispatching parser knows the field");
        // conference rings carry it too
        CallRschat.checkRing(signed(CALLER, callees(3), CallConstants.MODE_CONFERENCE, null, "audio"), CallRschat.RING_TO_MAX);
        // a video ring signed, then labelled audio (or the reverse): the signature no longer verifies
        CallRingBean video = signed(CALLER, callees(1), CallConstants.MODE_STANDARD, null, null);
        video.setMedia("audio");
        CallRingBean added = CallJson.parse(CallJson.toWire(video), CallRingBean.class);
        assertThrows(CallProtocolException.class, () -> CallSignatures.verify(added, CallConstants.T_RING, AUD));
        CallRingBean audio = audioRing(1);
        audio.setMedia(null);
        CallRingBean stripped = CallJson.parse(CallJson.toWire(audio), CallRingBean.class);
        assertThrows(CallProtocolException.class, () -> CallSignatures.verify(stripped, CallConstants.T_RING, AUD));
        assertFalse(CallRschat.isAudioRing(null));
    }

    @Test
    void anyOtherMediaValueIsRefusedLikeABadMode() throws Exception {
        for (String m : List.of("video", "", "Audio", "AUDIO", " audio", "audio ", "screen", "audio,video")) {
            CallRingBean r = signed(CALLER, callees(1), CallConstants.MODE_STANDARD, null, m);
            CallSignatures.verify(r, CallConstants.T_RING, AUD); // validly signed: the refusal is the shape check's
            refusedMedia(r);
            assertFalse(CallRschat.isAudioRing(r), m);
        }
        // the media refusal is not the limit: it comes before the to checks
        refusedMedia(signed(CALLER, callees(26), CallConstants.MODE_STANDARD, null, "video"));
        // a bad mode is still "ring fields"
        CallRingBean badMode = signed(CALLER, callees(1), "broadcast", null, "audio");
        assertEquals("ring fields",
                assertThrows(CallProtocolException.class, () -> CallRschat.checkRing(badMode, 25)).getReason());
        // a well-formed over-long audio ring is still the limit
        assertEquals(CallError.RATE_LIMITED, assertThrows(CallProtocolException.class,
                () -> CallRschat.checkRing(audioRing(26), 25)).getError());
    }

    @Test
    void mediaOnAnAnsweredNoticeOrACancelIsRefused() throws Exception {
        String me = id(callee(1));
        CallRingBean answered = signed(callee(1), List.of(me), CallConstants.MODE_STANDARD, CallConstants.RING_K_ANSWERED, "audio");
        refusedMedia(answered);
        CallRingBean cancelled = signed(CALLER, callees(1), CallConstants.MODE_STANDARD, CallConstants.RING_K_CANCELLED, "audio");
        refusedMedia(cancelled);
        assertFalse(CallRschat.isAudioRing(answered));
        assertFalse(CallRschat.isAudioRing(cancelled));
        // an unknown k with media: refused as media first (k is checked last)
        refusedMedia(signed(CALLER, callees(1), CallConstants.MODE_STANDARD, "hungup", "audio"));
        // the device's cancel verifier refuses it too (one reason for every shape failure)
        CallProtocolException ex = assertThrows(CallProtocolException.class,
                () -> CallRschat.checkCancel(CallRschat.pushRing(cancelled), AUD, CALL, id(CALLER), me, 25));
        assertEquals(CallError.BAD_SIGNATURE, ex.getError());
        assertEquals("cancel shape", ex.getReason());
        // without media both are accepted as before
        CallRschat.checkRing(signed(callee(1), List.of(me), CallConstants.MODE_STANDARD, CallConstants.RING_K_ANSWERED, null), 25);
        CallRschat.checkCancel(CallRschat.pushRing(signed(CALLER, callees(1), CallConstants.MODE_STANDARD,
                CallConstants.RING_K_CANCELLED, null)), AUD, CALL, id(CALLER), me, 25);
    }

    @Test
    void aSignedAudioRingSurvivesThePushAndStillVerifies() throws Exception {
        CallRingBean r = audioRing(3);
        String me = callees(3).get(1);
        // rschat: strict parse of the request, verify, check, then pushRing re-serialises FROM THE BEAN
        CallRingBean atRschat = CallJson.parse(CallJson.toWire(r), CallRingBean.class);
        CallSignatures.verify(atRschat, CallConstants.T_RING, AUD);
        CallRschat.checkRing(atRschat, CallRschat.RING_TO_MAX);
        String pushed = CallRschat.pushRing(atRschat);
        assertTrue(pushed.contains("\"media\":\"audio\""), pushed);
        assertTrue(CallBytes.isAscii(pushed));
        // the notice (and so the push's own fields) is unchanged: call, svc, f, mode — no media
        CallRingNoticeBean notice = CallRschat.notice(atRschat);
        assertFalse(CallJson.toWire(notice).contains("media"));
        // the device: the same verifier as for any pushed ring; media is read from the VERIFIED ring
        CallRingBean v = CallRschat.checkPushRing(pushed, AUD, notice, me, CallRschat.RING_TO_MAX);
        assertEquals(id(CALLER), v.getF());
        assertEquals("audio", v.getMedia());
        assertTrue(CallRschat.isAudioRing(v));
        assertEquals(pushed, CallRschat.pushRing(v), "idempotent");
        // media dropped or added in transit: the pushed ring no longer verifies
        String dropped = pushed.replace("\"media\":\"audio\",", "");
        assertFalse(dropped.equals(pushed));
        assertThrows(CallProtocolException.class, () -> CallRschat.checkPushRing(dropped, AUD, notice, me, 25));
        CallRingBean video = signed(CALLER, callees(3), CallConstants.MODE_STANDARD, null, null);
        String addedInTransit = CallRschat.pushRing(video).replace(",\"mode\":", ",\"media\":\"audio\",\"mode\":");
        assertThrows(CallProtocolException.class,
                () -> CallRschat.checkPushRing(addedInTransit, AUD, CallRschat.notice(video), me, 25));
        // a signed media:"video" ring is refused on the device as at rschat
        CallRingBean bad = signed(CALLER, callees(3), CallConstants.MODE_STANDARD, null, "video");
        assertThrows(CallProtocolException.class,
                () -> CallRschat.checkPushRing(CallRschat.pushRing(bad), AUD, CallRschat.notice(bad), me, 25));
    }

    @Test
    void theStrictParserStillRefusesAnyUnknownRingField() throws Exception {
        String good = CallRschat.pushRing(audioRing(1));
        CallJson.parse(good, CallRingBean.class);
        for (String extra : List.of("\"video\":true", "\"x\":\"1\"", "\"medias\":\"audio\"", "\"Media\":\"audio\"", "\"conv\":\"00\"")) {
            String bad = good.substring(0, good.length() - 1) + "," + extra + "}";
            assertThrows(JsonProcessingException.class, () -> CallJson.parse(bad, CallRingBean.class), extra);
            assertThrows(JsonProcessingException.class, () -> CallJson.parse(bad), extra);
        }
        // a duplicate media key is refused
        String dup = good.replace(",\"mode\":", ",\"media\":\"audio\",\"mode\":");
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(dup, CallRingBean.class));
        // media is not a ring-notice field
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(
                "{\"call\":\"" + CALL + "\",\"svc\":\"s\",\"f\":\"" + id(CALLER) + "\",\"mode\":\"standard\",\"media\":\"audio\"}",
                CallRingNoticeBean.class));
    }

    // ---- the channel body kind h:"media" ------------------------------------------------------------------------------

    static CallChannelBodyBean mediaBody(String media) {
        CallChannelBodyBean b = new CallChannelBodyBean();
        b.setH(CallConstants.H_MEDIA);
        b.setMedia(media);
        return b;
    }

    @Test
    void theMediaNoticeBodyIsTwoFieldsAndRoundTrips() throws Exception {
        assertEquals("{\"h\":\"media\",\"media\":\"audio\"}", CallJson.canonical(mediaBody(CallConstants.MEDIA_AUDIO)));
        assertEquals("{\"h\":\"media\",\"media\":\"video\"}", CallJson.canonical(mediaBody(CallConstants.MEDIA_VIDEO)));
        for (String m : List.of("audio", "video")) {
            String c = CallJson.canonical(mediaBody(m));
            CallChannelBodyBean back = CallJson.parse(c, CallChannelBodyBean.class);
            assertEquals(mediaBody(m), back);
            assertEquals("media", back.getH());
            assertEquals(m, back.getMedia());
            assertEquals(c, CallJson.canonical(back));
        }
        // key order on the wire is free; the canonical form is one
        assertEquals("{\"h\":\"media\",\"media\":\"audio\"}",
                CallJson.canonical(CallJson.parse("{\"media\":\"audio\",\"h\":\"media\"}", CallChannelBodyBean.class)));
        // forward compatibility: the bean does not refuse an unknown value (the receiver shows «unknown cause»)
        CallChannelBodyBean screen = CallJson.parse("{\"h\":\"media\",\"media\":\"screen\"}", CallChannelBodyBean.class);
        assertEquals("screen", screen.getMedia());
    }

    @Test
    void theOtherBodiesAreUnchangedAndTheStrictParserStillRefusesUnknownBodyFields() throws Exception {
        // a body without media never emits the field: the catch-up request and the empty body are today's bytes
        assertEquals("{\"epoch\":17,\"h\":\"catchup\"}", CallJson.canonical(Commits.catchUpRequest(17)));
        assertEquals("{}", CallJson.canonical(new CallChannelBodyBean()));
        assertNull(CallJson.parse("{\"epoch\":17,\"h\":\"catchup\"}", CallChannelBodyBean.class).getMedia());
        // no ms counter, no other field
        for (String bad : List.of("{\"h\":\"media\",\"media\":\"audio\",\"ms\":1}", "{\"h\":\"media\",\"media\":\"audio\",\"x\":\"1\"}",
                "{\"h\":\"media\",\"Media\":\"audio\"}", "{\"h\":\"media\",\"media\":\"audio\",\"media\":\"video\"}")) {
            assertThrows(JsonProcessingException.class, () -> CallJson.parse(bad, CallChannelBodyBean.class), bad);
        }
    }

    @Test
    void aReceiverReadsMediaOnlyWhenHIsMedia() throws Exception {
        assertEquals("audio", mediaBody("audio").declaredMedia());
        assertEquals("video", mediaBody("video").declaredMedia());
        // an unknown value (the empty string included) is a declaration: the receiver shows «unknown cause»
        assertEquals("screen", mediaBody("screen").declaredMedia());
        assertEquals("", CallJson.parse("{\"h\":\"media\",\"media\":\"\"}", CallChannelBodyBean.class).declaredMedia());
        // h:"media" without media (absent or null): no declaration
        assertNull(mediaBody(null).declaredMedia());
        assertNull(CallJson.parse("{\"h\":\"media\"}", CallChannelBodyBean.class).declaredMedia());
        CallChannelBodyBean nul = CallJson.parse("{\"h\":\"media\",\"media\":null}", CallChannelBodyBean.class);
        assertNull(nul.declaredMedia());
        assertEquals("{\"h\":\"media\"}", CallJson.canonical(nul));
        // media under any other h, or without h, parses but is NOT a declaration
        CallChannelBodyBean noH = CallJson.parse("{\"media\":\"audio\"}", CallChannelBodyBean.class);
        assertEquals("audio", noH.getMedia());
        assertNull(noH.declaredMedia());
        CallChannelBodyBean catchup = CallJson.parse("{\"epoch\":17,\"h\":\"catchup\",\"media\":\"audio\"}", CallChannelBodyBean.class);
        assertEquals("audio", catchup.getMedia());
        assertNull(catchup.declaredMedia());
        for (String h : List.of("epoch", "broadcast", "Media", "MEDIA", "media ", "")) {
            CallChannelBodyBean b = mediaBody("audio");
            b.setH(h);
            assertNull(b.declaredMedia(), h);
        }
        assertNull(new CallChannelBodyBean().declaredMedia());
        assertNull(Commits.catchUpRequest(17).declaredMedia());
        // the helper is not a bean property: it never reaches the wire
        assertEquals("{\"h\":\"media\",\"media\":\"audio\"}", CallJson.canonical(mediaBody("audio")));
        assertFalse(CallJson.toWire(mediaBody("audio")).contains("declared"));
    }

    @Test
    void mediaMustBeAJsonStringInBothObjects() throws Exception {
        // Jackson would otherwise read true as "true" and 1 as "1"; the Dart schema refuses a non-string
        String ring = CallRschat.pushRing(signed(CALLER, callees(1), CallConstants.MODE_STANDARD, null, null));
        for (String v : List.of("true", "1", "1.5", "[\"audio\"]", "{\"a\":\"audio\"}")) {
            String badRing = ring.replace(",\"mode\":", ",\"media\":" + v + ",\"mode\":");
            assertThrows(JsonProcessingException.class, () -> CallJson.parse(badRing, CallRingBean.class), v);
            String badBody = "{\"h\":\"media\",\"media\":" + v + "}";
            assertThrows(JsonProcessingException.class, () -> CallJson.parse(badBody, CallChannelBodyBean.class), v);
        }
    }

    // ---- the cross-platform vector file -------------------------------------------------------------------------------

    /** The reference verdict of a ring text: strict parse, signature under the ring context for {@code aud}, shape. */
    static Map<String, Object> ringVerdict(String ringJson, String aud) {
        Map<String, Object> e = new LinkedHashMap<>();
        final CallRingBean r;
        try {
            r = CallJson.parse(ringJson, CallRingBean.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            e.put("ok", false);
            e.put("stage", "parse");
            return e;
        }
        try {
            CallSignatures.verify(r, CallConstants.T_RING, aud);
        } catch (CallProtocolException ex) {
            e.put("ok", false);
            e.put("stage", "signature");
            return e;
        }
        try {
            CallRschat.checkRing(r, CallRschat.RING_TO_MAX);
        } catch (CallProtocolException ex) {
            e.put("ok", false);
            e.put("stage", "shape");
            e.put("error", ex.getError().code());
            e.put("reason", ex.getReason());
            return e;
        }
        e.put("ok", true);
        e.put("stage", "ok");
        e.put("f", r.getF());
        e.put("k", r.getK());
        e.put("media", r.getMedia());
        e.put("audio_ring", CallRschat.isAudioRing(r));
        e.put("canonical", CallSignatures.canonical(r));
        e.put("signing_octets_hex", CallBytes.hex(CallSignatures.signingOctets(r)));
        e.put("sg", r.getSg());
        e.put("push_ring", CallRschat.pushRing(r));
        return e;
    }

    static Map<String, Object> ringCase(String name, String note, String ringJson) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", name);
        c.put("note", note);
        c.put("ring", ringJson);
        c.put("expect", ringVerdict(ringJson, AUD));
        return c;
    }

    /** The reference reading of a body plaintext: strict parse, then its canonical form (= the sealed plaintext). */
    static Map<String, Object> bodyVerdict(String bodyJson) {
        Map<String, Object> e = new LinkedHashMap<>();
        final CallChannelBodyBean b;
        try {
            b = CallJson.parse(bodyJson, CallChannelBodyBean.class);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            e.put("ok", false);
            return e;
        }
        String canonical = CallJson.canonical(b);
        e.put("ok", true);
        e.put("h", b.getH());
        e.put("media", b.getMedia());
        // what a receiver takes: media is read only when h == "media" (CallChannelBodyBean.declaredMedia)
        String declared = b.declaredMedia();
        e.put("declaration", declared);
        e.put("known_value", CallConstants.H_MEDIA.equals(b.getH())
                ? (Object) (CallConstants.MEDIA_AUDIO.equals(declared) || CallConstants.MEDIA_VIDEO.equals(declared)) : null);
        e.put("canonical", canonical);
        e.put("plaintext_hex", CallBytes.hex(CallBytes.ascii(canonical)));
        return e;
    }

    static Map<String, Object> bodyCase(String name, String note, String bodyJson) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", name);
        c.put("note", note);
        c.put("body", bodyJson);
        c.put("expect", bodyVerdict(bodyJson));
        return c;
    }

    static Map<String, Object> key(String name) {
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("seed_hex", CallBytes.hex(CallTestKeys.seed("identity/" + name)));
        k.put("identity", id(CallTestKeys.identity(name)));
        return k;
    }

    static String withExtra(String json, String member) {
        return json.substring(0, json.length() - 1) + "," + member + "}";
    }

    /** The vector file as this reference generates it now. */
    static String generate() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("_origin", "Messages CallAudioOnlyS0Test (audio-only calls S0, design AUDIO_ONLY_CALLS_DESIGN_2026-11-19 §5-§7, "
                + "rulings R-1/R-2/R-3). `ring_cases`: `ring` is the text a verifier receives (the callring request, the push "
                + "field `ring`, the live `signed_ring`); `expect.ok` = strict parse (unknown field / duplicate key refused), then "
                + "the call signature under tkm-call/v1/ring for `aud`, then CallRschat.checkRing with `ring_to_max`. `expect.stage` "
                + "is where THIS reference refuses (parse | signature | shape); a port must agree on `ok` and, for the shape cases, "
                + "on `error` and `reason`. For an accepted ring: `canonical` = JCS of the object without sg, `signing_octets_hex` = "
                + "ASCII(\"tkm-call/v1/ring\") || 00 || canonical, `sg` = Ed25519 (deterministic) over them, `push_ring` = JCS of the "
                + "whole bean (CallRschat.pushRing), `media` = the verified field (null = a video ring). `body_cases`: `body` is the "
                + "plaintext of a pairwise channel box as received; `expect.ok` = strict parse of CallChannelBodyBean; `canonical` / "
                + "`plaintext_hex` = the bytes a sender seals (JCS, ASCII); `media` = the parsed field; `declaration` = what a RECEIVER "
                + "takes (CallChannelBodyBean.declaredMedia): the media value ONLY when h == \"media\", else null — null also for "
                + "h:\"media\" without media (no declaration = unknown cause); `known_value` is emitted only when h == \"media\" "
                + "(else null): true for video/audio, false = the receiver shows «unknown cause». In `ring_cases` a JSON "
                + "\"media\":null is the absent field (same canonical and push_ring as the ring without it). "
                + "Keys: seeds are SHA3-256(ASCII(\"tkm-call/v1/vectors/identity/\" + name)) (CallTestKeys.identity).");
        root.put("protocol_version", CallConstants.PROTOCOL_VERSION);
        root.put("aud", AUD);
        root.put("ring_to_max", CallRschat.RING_TO_MAX);
        root.put("ring_media_audio", CallConstants.RING_MEDIA_AUDIO);
        root.put("h_media", CallConstants.H_MEDIA);
        root.put("media_values", List.of(CallConstants.MEDIA_VIDEO, CallConstants.MEDIA_AUDIO));
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("s0-caller", key("s0-caller"));
        for (int i = 1; i <= 3; i++) {
            keys.put("s0-callee-" + i, key("s0-callee-" + i));
        }
        root.put("keys", keys);

        final String std = CallConstants.MODE_STANDARD;
        final List<String> one = callees(1);
        final String me = one.get(0);
        final String audio = CallRschat.pushRing(audioRing(1));
        final String video = CallRschat.pushRing(signed(CALLER, one, std, null, null));
        List<Map<String, Object>> rings = new ArrayList<>();
        rings.add(ringCase("audio_ring", "media:\"audio\" on a ring, one callee: accepted", audio));
        rings.add(ringCase("audio_ring_conference_three_callees", "mode conference, three callees: accepted",
                CallRschat.pushRing(signed(CALLER, callees(3), CallConstants.MODE_CONFERENCE, null, "audio"))));
        rings.add(ringCase("video_ring_no_media", "no media = a video ring, the pre-S0 bytes (same inputs as audio_ring)", video));
        rings.add(ringCase("media_null", "video_ring_no_media with \"media\":null spliced in: null = absent, so sg still verifies; "
                + "canonical and push_ring are those of video_ring_no_media (re-serialised from the parsed object, never the raw text)",
                video.replace(",\"mode\":", ",\"media\":null,\"mode\":")));
        rings.add(ringCase("media_video", "validly signed media:\"video\": the only allowed value is audio",
                CallRschat.pushRing(signed(CALLER, one, std, null, "video"))));
        rings.add(ringCase("media_empty", "validly signed media:\"\"", CallRschat.pushRing(signed(CALLER, one, std, null, ""))));
        rings.add(ringCase("media_wrong_case", "validly signed media:\"Audio\": the value is case-sensitive",
                CallRschat.pushRing(signed(CALLER, one, std, null, "Audio"))));
        rings.add(ringCase("media_with_k_answered", "an answered notice (signed by the callee, to=[f]) with media",
                CallRschat.pushRing(signed(callee(1), List.of(me), std, CallConstants.RING_K_ANSWERED, "audio"))));
        rings.add(ringCase("media_with_k_cancelled", "the caller's cancel with media",
                CallRschat.pushRing(signed(CALLER, one, std, CallConstants.RING_K_CANCELLED, "audio"))));
        rings.add(ringCase("media_video_26_callees", "order of the checks: 26 callees (over ring_to_max) AND media:\"video\" is "
                + "bad_signature \"ring media\", not rate_limited \"ring_to_max\" (media is checked before to)",
                CallRschat.pushRing(signed(CALLER, callees(26), std, null, "video"))));
        rings.add(ringCase("media_with_unknown_k", "order of the checks: k:\"hungup\" AND media:\"audio\" is \"ring media\", not "
                + "\"ring k\" (k is checked last)",
                CallRschat.pushRing(signed(CALLER, one, std, "hungup", "audio"))));
        rings.add(ringCase("k_answered_no_media", "control: the same answered notice without media is accepted",
                CallRschat.pushRing(signed(callee(1), List.of(me), std, CallConstants.RING_K_ANSWERED, null))));
        rings.add(ringCase("k_cancelled_no_media", "control: the same cancel without media is accepted",
                CallRschat.pushRing(signed(CALLER, one, std, CallConstants.RING_K_CANCELLED, null))));
        rings.add(ringCase("unknown_k_no_media", "k:\"hungup\" without media: refused by checkRing as \"ring k\" on both "
                + "sides (pins the unknown-k refusal by a shared vector, not only by Java asserts; 83, 11-19)",
                CallRschat.pushRing(signed(CALLER, one, std, "hungup", null))));
        rings.add(ringCase("unknown_extra_field", "audio_ring plus an unknown member: the schema stays closed",
                withExtra(audio, "\"x\":\"1\"")));
        rings.add(ringCase("unknown_extra_field_video", "audio_ring plus \"video\":true: no sibling field exists",
                withExtra(audio, "\"video\":true")));
        rings.add(ringCase("duplicate_media", "audio_ring with media twice", audio.replace(",\"mode\":", ",\"media\":\"audio\",\"mode\":")));
        rings.add(ringCase("media_not_a_string", "video_ring_no_media with media:true spliced in",
                video.replace(",\"mode\":", ",\"media\":true,\"mode\":")));
        rings.add(ringCase("media_added_after_signing", "video_ring_no_media with media:\"audio\" spliced in: sg does not cover it",
                video.replace(",\"mode\":", ",\"media\":\"audio\",\"mode\":")));
        rings.add(ringCase("media_stripped_after_signing", "audio_ring with media removed: sg covered it",
                audio.replace("\"media\":\"audio\",", "")));
        rings.add(ringCase("wrong_audience", "audio_ring signed for rschat:prod", CallRschat.pushRing(signedFor("prod"))));
        root.put("ring_cases", rings);

        List<Map<String, Object>> bodies = new ArrayList<>();
        bodies.add(bodyCase("media_audio", "the sender declares audio only", CallJson.canonical(mediaBody(CallConstants.MEDIA_AUDIO))));
        bodies.add(bodyCase("media_video", "the sender declares video", CallJson.canonical(mediaBody(CallConstants.MEDIA_VIDEO))));
        bodies.add(bodyCase("media_audio_other_key_order", "key order as received is free; the canonical form is one",
                "{\"media\":\"audio\",\"h\":\"media\"}"));
        bodies.add(bodyCase("media_unknown_value", "forward compatibility: an unknown value parses, known_value=false",
                "{\"h\":\"media\",\"media\":\"screen\"}"));
        bodies.add(bodyCase("unknown_extra_field_ms", "there is no ms counter: an unknown body field is refused",
                "{\"h\":\"media\",\"media\":\"audio\",\"ms\":1}"));
        bodies.add(bodyCase("duplicate_media", "media twice", "{\"h\":\"media\",\"media\":\"audio\",\"media\":\"video\"}"));
        bodies.add(bodyCase("media_not_a_string", "media as a number", "{\"h\":\"media\",\"media\":1}"));
        bodies.add(bodyCase("media_absent", "h:\"media\" without media: accepted, no declaration (= unknown cause)", "{\"h\":\"media\"}"));
        bodies.add(bodyCase("media_null", "media:null = absent: accepted, no declaration, canonical {\"h\":\"media\"}",
                "{\"h\":\"media\",\"media\":null}"));
        bodies.add(bodyCase("media_empty_string", "media:\"\": accepted, a declaration with an unknown value",
                "{\"h\":\"media\",\"media\":\"\"}"));
        bodies.add(bodyCase("media_without_h", "media without h parses, but a receiver reads media only when h == \"media\": "
                + "no declaration", "{\"media\":\"audio\"}"));
        bodies.add(bodyCase("media_on_catchup", "media on another body kind parses, but is not a declaration",
                "{\"epoch\":17,\"h\":\"catchup\",\"media\":\"audio\"}"));
        bodies.add(bodyCase("catchup_unchanged", "control: a pre-S0 body carries no media and keeps its bytes",
                CallJson.canonical(Commits.catchUpRequest(17))));
        root.put("body_cases", bodies);
        return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(root) + "\n";
    }

    static CallRingBean signedFor(String network) {
        CallRingBean r = CallRschat.ring(CALL, SVC, callees(1), CallConstants.MODE_STANDARD, NONCE, CallRschat.audience(network), TS);
        r.setMedia(CallConstants.RING_MEDIA_AUDIO);
        CallSignatures.sign(r, CALLER);
        return r;
    }

    static JsonNode committed() throws Exception {
        try (InputStream in = CallAudioOnlyS0Test.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "missing " + RESOURCE + " — run with -Dcall.vectors.write=true");
            return new ObjectMapper().readTree(in);
        }
    }

    @Test
    void vectorFileEqualsWhatTheReferenceGeneratesNow() throws Exception {
        String generated = generate();
        if (Boolean.getBoolean("call.vectors.write")) {
            Path out = Path.of("src/test/resources" + RESOURCE);
            Files.createDirectories(out.getParent());
            Files.writeString(out, generated, StandardCharsets.UTF_8);
        }
        String committed;
        try (InputStream in = CallAudioOnlyS0Test.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "missing " + RESOURCE + " — run with -Dcall.vectors.write=true");
            committed = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(generated, committed, "the committed vector file drifted from the reference");
    }

    @Test
    void theVectorPinsTheIntendedVerdicts() throws Exception {
        JsonNode root = committed();
        assertEquals("1.1", root.get("protocol_version").asText());
        Map<String, String> verdict = new LinkedHashMap<>();
        for (JsonNode c : root.get("ring_cases")) {
            JsonNode e = c.get("expect");
            verdict.put(c.get("name").asText(), e.get("ok").asBoolean()
                    ? "ok:" + (e.get("media").isNull() ? "-" : e.get("media").asText())
                    : e.get("stage").asText() + (e.has("reason") ? ":" + e.get("error").asText() + ":" + e.get("reason").asText() : ""));
        }
        Map<String, String> want = new LinkedHashMap<>();
        want.put("audio_ring", "ok:audio");
        want.put("audio_ring_conference_three_callees", "ok:audio");
        want.put("video_ring_no_media", "ok:-");
        want.put("media_null", "ok:-");
        want.put("media_video", "shape:bad_signature:ring media");
        want.put("media_empty", "shape:bad_signature:ring media");
        want.put("media_wrong_case", "shape:bad_signature:ring media");
        want.put("media_with_k_answered", "shape:bad_signature:ring media");
        want.put("media_with_k_cancelled", "shape:bad_signature:ring media");
        want.put("media_video_26_callees", "shape:bad_signature:ring media");
        want.put("media_with_unknown_k", "shape:bad_signature:ring media");
        want.put("k_answered_no_media", "ok:-");
        want.put("k_cancelled_no_media", "ok:-");
        want.put("unknown_k_no_media", "shape:bad_signature:ring k");
        want.put("unknown_extra_field", "parse");
        want.put("unknown_extra_field_video", "parse");
        want.put("duplicate_media", "parse");
        want.put("media_not_a_string", "parse");
        want.put("media_added_after_signing", "signature");
        want.put("media_stripped_after_signing", "signature");
        want.put("wrong_audience", "signature");
        assertEquals(want, verdict);

        Map<String, String> bodies = new LinkedHashMap<>();
        for (JsonNode c : root.get("body_cases")) {
            JsonNode e = c.get("expect");
            // canonical | declaration (<none> = JSON null) | known_value (- = JSON null: not a media body)
            bodies.put(c.get("name").asText(), e.get("ok").asBoolean()
                    ? e.get("canonical").asText()
                    + " | " + (e.get("declaration").isNull() ? "<none>" : "'" + e.get("declaration").asText() + "'")
                    + " | " + (e.get("known_value").isNull() ? "-" : e.get("known_value").asText()) : "refused");
        }
        Map<String, String> wantBodies = new LinkedHashMap<>();
        wantBodies.put("media_audio", "{\"h\":\"media\",\"media\":\"audio\"} | 'audio' | true");
        wantBodies.put("media_video", "{\"h\":\"media\",\"media\":\"video\"} | 'video' | true");
        wantBodies.put("media_audio_other_key_order", "{\"h\":\"media\",\"media\":\"audio\"} | 'audio' | true");
        wantBodies.put("media_unknown_value", "{\"h\":\"media\",\"media\":\"screen\"} | 'screen' | false");
        wantBodies.put("unknown_extra_field_ms", "refused");
        wantBodies.put("duplicate_media", "refused");
        wantBodies.put("media_not_a_string", "refused");
        wantBodies.put("media_absent", "{\"h\":\"media\"} | <none> | false");
        wantBodies.put("media_null", "{\"h\":\"media\"} | <none> | false");
        wantBodies.put("media_empty_string", "{\"h\":\"media\",\"media\":\"\"} | '' | false");
        wantBodies.put("media_without_h", "{\"media\":\"audio\"} | <none> | -");
        wantBodies.put("media_on_catchup", "{\"epoch\":17,\"h\":\"catchup\",\"media\":\"audio\"} | <none> | -");
        wantBodies.put("catchup_unchanged", "{\"epoch\":17,\"h\":\"catchup\"} | <none> | -");
        assertEquals(wantBodies, bodies);
        // "media":null on a ring is the absent field: the video ring's canonical form, signature and push text
        JsonNode nul = root.get("ring_cases").get(3);
        assertEquals("media_null", nul.get("name").asText());
        assertTrue(nul.get("ring").asText().contains("\"media\":null"));
        JsonNode vid = root.get("ring_cases").get(2).get("expect");
        assertEquals(root.get("ring_cases").get(2).get("ring").asText(), vid.get("push_ring").asText());
        for (String f : List.of("canonical", "push_ring", "sg", "signing_octets_hex")) {
            assertEquals(vid.get(f), nul.get("expect").get(f), f);
        }
        assertTrue(nul.get("expect").get("media").isNull());
        assertFalse(nul.get("expect").get("audio_ring").asBoolean());
        // the audio ring and the video ring differ ONLY by the media member (and so by sg)
        JsonNode a = root.get("ring_cases").get(0).get("expect");
        JsonNode v = root.get("ring_cases").get(2).get("expect");
        assertEquals(v.get("canonical").asText(), a.get("canonical").asText().replace("\"media\":\"audio\",", ""));
        assertFalse(a.get("sg").asText().equals(v.get("sg").asText()));
    }

    @Test
    void everyVectorCaseReDerivesFromTheFileAlone() throws Exception {
        JsonNode root = committed();
        String aud = root.get("aud").asText();
        ObjectMapper plain = new ObjectMapper();
        for (JsonNode c : root.get("ring_cases")) {
            String name = c.get("name").asText();
            JsonNode e = c.get("expect");
            assertEquals(e, plain.valueToTree(ringVerdict(c.get("ring").asText(), aud)), name);
            if (e.get("ok").asBoolean()) {
                // the signature in the file verifies over the octets in the file, with the key named by f
                CallSignedObject r = CallJson.parse(c.get("ring").asText());
                assertEquals(e.get("signing_octets_hex").asText(), CallBytes.hex(CallSignatures.signingOctets(r)), name);
                assertEquals("tkm-call/v1/ring\u0000" + e.get("canonical").asText(),
                        new String(CallBytes.unhex(e.get("signing_octets_hex").asText()), StandardCharsets.US_ASCII), name);
                assertTrue(CallSignatures.isValid(r, CallConstants.T_RING, aud), name);
                // deterministic Ed25519: signing again with the fixture key gives the same sg
                String signerName = null;
                var it = root.get("keys").fields();
                while (it.hasNext()) {
                    var k = it.next();
                    if (k.getValue().get("identity").asText().equals(e.get("f").asText())) {
                        signerName = k.getKey();
                    }
                }
                assertNotNull(signerName, name);
                CallRingBean again = CallJson.parse(c.get("ring").asText(), CallRingBean.class);
                again.setSg(null);
                CallSignatures.sign(again, CallTestKeys.ed25519(CallBytes.unhex(root.get("keys").get(signerName).get("seed_hex").asText())));
                assertEquals(e.get("sg").asText(), again.getSg(), name);
            }
        }
        for (JsonNode c : root.get("body_cases")) {
            assertEquals(c.get("expect"), plain.valueToTree(bodyVerdict(c.get("body").asText())), c.get("name").asText());
        }
    }
}
