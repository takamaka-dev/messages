package io.takamaka.messages.call;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.takamaka.messages.call.beans.CallChannelBean;
import io.takamaka.messages.call.beans.CallChannelBodyBean;
import io.takamaka.messages.call.channel.HybridChannel;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Audio-only calls S1, Iris §7.189 F1 (design AUDIO_ONLY_CALLS_DESIGN_2026-11-19 §6 «The exact parse spec»): the
 * forward-compatible parse of a received channel body, {@link CallChannelBodyBean#parseChannelBody(String)}, its use
 * by {@link HybridChannel#acceptChannel} for opens, and the cross-platform vector file
 * {@code call/call_channel_body_unknown_kind_vectors_v1.json} the Dart port ({@code CallChannelBody.parse}) verifies
 * case by case: the committed copy must equal what this reference generates now (drift guard); regenerate with
 * {@code -Dcall.vectors.write=true}.
 */
class CallChannelBodyUnknownKindTest {

    static final String RESOURCE = "/call/call_channel_body_unknown_kind_vectors_v1.json";

    static CallVectorScenario s;

    @BeforeAll
    static void build() throws Exception {
        s = new CallVectorScenario();
    }

    // ---- the helper -------------------------------------------------------------------------------------------

    @Test
    void knownKindsAreTheFourAndCaseSensitive() {
        assertEquals(Set.of("epoch", "broadcast", "catchup", "media"), CallChannelBodyBean.KNOWN_KINDS);
        assertEquals(Set.of("epoch", "conv_seed"), CallChannelBodyBean.UNKNOWN_KIND_KEEPS);
        assertTrue(CallChannelBodyBean.isKnownKind("media"));
        assertFalse(CallChannelBodyBean.isKnownKind("Media"));
        assertFalse(CallChannelBodyBean.isKnownKind(null));
        assertEquals("epoch", CallConstants.H_EPOCH);
        assertEquals("broadcast", CallConstants.H_BROADCAST);
    }

    @Test
    void anUnknownKindKeepsOnlyHEpochConvSeedAndIsNotADeclaration() throws Exception {
        CallChannelBodyBean b = CallChannelBodyBean.parseChannelBody(
                "{\"h\":\"future\",\"epoch\":7,\"conv_seed\":\"ab\",\"secret\":\"zz\",\"media\":\"audio\",\"roster\":[\"aa\"],\"x\":{\"y\":1}}");
        assertEquals("future", b.getH());
        assertEquals(7L, b.getEpoch());
        assertEquals("ab", b.getConvSeed());
        assertNull(b.getSecret());
        assertNull(b.getRoster());
        assertNull(b.getMedia());
        assertNull(b.declaredMedia());
        assertTrue(b.ignoredKind());
        assertFalse(b.knownKind());
        assertEquals("{\"conv_seed\":\"ab\",\"epoch\":7,\"h\":\"future\"}", CallJson.canonical(b));
        // the helper methods are not bean properties: they never reach the wire
        assertFalse(CallJson.toWire(b).contains("Kind"));
    }

    @Test
    void aKnownAbsentOrNonStringHKeepsTheStrictSchemaUnchanged() throws Exception {
        for (String bad : List.of("{\"h\":\"media\",\"media\":\"audio\",\"ms\":1}", "{\"h\":1}", "{\"h\":null,\"x\":1}",
                "{\"x\":1}", "{\"h\":\"catchup\",\"epoch\":\"3\"}", "{\"h\":true}")) {
            assertThrows(JsonProcessingException.class, () -> CallChannelBodyBean.parseChannelBody(bad), bad);
            assertThrows(JsonProcessingException.class, () -> CallJson.parse(bad, CallChannelBodyBean.class), bad);
        }
        for (String good : List.of("{}", "{\"h\":\"media\",\"media\":\"audio\"}", "{\"h\":\"catchup\",\"req\":\"r\",\"epoch\":3}",
                "{\"conv_seed\":\"ab\"}", "{\"h\":null}")) {
            assertEquals(CallJson.parse(good, CallChannelBodyBean.class), CallChannelBodyBean.parseChannelBody(good), good);
        }
    }

    @Test
    void strictJsonComesFirst() {
        for (String bad : List.of("{\"h\":\"future\",\"x\":1,\"x\":2}", "{\"h\":\"future\",\"h\":\"other\"}", "[]", "\"future\"",
                "null", "{\"h\":\"future\"", "", "   ", "\n")) {
            assertThrows(JsonProcessingException.class, () -> CallChannelBodyBean.parseChannelBody(bad), bad);
        }
    }

    // ---- HybridChannel: an open whose body is an unknown kind ------------------------------------------------

    /** The owner's open to bob, re-sealed with an arbitrary body plaintext (what a newer peer may send). */
    static CallChannelBean openWithPlaintext(String plaintext) {
        HybridChannel.OpenResult open = HybridChannel.openChannel(s.callId, s.annO, s.legs.get("owner").xPriv, s.annB,
                s.kem, CallTestKeys.seed("encaps/s1-unknown-kind"), null, null, s.ids.get("owner"), 1L);
        CallChannelBean ch = open.channel();
        byte[] aad = HybridChannel.channelAad(ch);
        assertArrayEquals(open.aad(), aad, "the aad excludes box and sg");
        ch.setBox(CallBytes.hex(HybridChannel.seal(open.combined().chainAB(), 0, 0, aad, CallBytes.ascii(plaintext))));
        CallSignatures.sign(ch, s.ids.get("owner"));
        return ch;
    }

    static HybridChannel.AcceptResult acceptAtBob(CallChannelBean ch) throws CallProtocolException {
        CallVectorScenario.Leg b = s.legs.get("bob");
        return HybridChannel.acceptChannel(ch, CallVectorScenario.rosterOf(s.annO, s.annB), s.annB, b.xPriv, b.kemKeys.dk(), s.kem);
    }

    @Test
    void anOpenCarryingAnUnknownKindIsAcceptedWithItsEpochAndConvSeed() throws Exception {
        String seed = CallBytes.hex(s.convSeed);
        String plaintext = "{\"conv_seed\":\"" + seed + "\",\"epoch\":7,\"h\":\"future\",\"secret\":\"zz\",\"x\":{\"y\":[1,2]}}";
        // before S1 the strict schema refused it (unknown member x), which disturbed the rotation
        assertThrows(JsonProcessingException.class, () -> CallJson.parse(plaintext, CallChannelBodyBean.class));
        HybridChannel.AcceptResult acc = acceptAtBob(openWithPlaintext(plaintext));
        CallChannelBodyBean body = acc.body();
        assertEquals("future", body.getH());
        assertEquals(7L, body.getEpoch(), "O-7: the caller takes ref = body.epoch from the kept member");
        assertEquals(seed, body.getConvSeed(), "§10.1: conv_seed is kept");
        assertTrue(body.ignoredKind(), "no handover, catch-up or declaration");
        assertNull(body.getSecret());
        assertNull(body.declaredMedia());
        assertEquals("{\"conv_seed\":\"" + seed + "\",\"epoch\":7,\"h\":\"future\"}", CallJson.canonical(body));
        assertArrayEquals(CallBytes.ascii(plaintext), acc.bodyBytes(), "bodyBytes stay the plaintext as received");
    }

    @Test
    void anOpenCarryingAnUnknownKindWithoutEpochLeavesItAbsent() throws Exception {
        CallChannelBodyBean body = acceptAtBob(openWithPlaintext("{\"h\":\"future\",\"x\":1}")).body();
        assertEquals("future", body.getH());
        assertNull(body.getEpoch(), "O-7: ref = own epoch");
        assertNull(body.getConvSeed());
    }

    @Test
    void anOpenRefusesAWronglyTypedKeptMemberAndAKnownKindWithAnUnknownMember() {
        for (String bad : List.of("{\"h\":\"future\",\"epoch\":\"7\"}", "{\"h\":\"future\",\"conv_seed\":1}",
                "{\"h\":\"future\",\"x\":1,\"x\":2}", "{\"h\":\"media\",\"media\":\"audio\",\"ms\":1}", "{\"h\":1}")) {
            CallProtocolException ex = assertThrows(CallProtocolException.class, () -> acceptAtBob(openWithPlaintext(bad)), bad);
            assertEquals(CallError.KEY_REFUSED, ex.getError(), bad);
            assertEquals("body", ex.getReason(), bad);
        }
    }

    @Test
    void theScenarioOpensStillParseAsBefore() throws Exception {
        // the handover opens of the vector scenario: known kind, unchanged bean
        String raw = new String(s.accOB.bodyBytes(), StandardCharsets.US_ASCII);
        assertEquals(CallJson.parse(raw, CallChannelBodyBean.class), s.accOB.body());
        assertTrue(s.accOB.body().knownKind());
        assertEquals(raw, CallJson.canonical(s.accOB.body()));
    }

    // ---- the vector file ----------------------------------------------------------------------------------------

    /**
     * Where THIS reference refuses: json (strict JSON / not an object), kept_member (unknown kind: a kept member's
     * type), any_member (unknown kind: the whole-tree rule on any member, dropped ones included), schema (strict bean).
     */
    static String refusalStage(String body, Exception refusal) {
        if (refusal.getMessage() != null && refusal.getMessage().startsWith(CallChannelBodyBean.ANY_MEMBER_REFUSAL)) {
            return "any_member";
        }
        JsonNode tree;
        try {
            tree = CallJson.mapper().readTree(body);
        } catch (JsonProcessingException ex) {
            return "json";
        }
        if (tree == null || !tree.isObject()) {
            return "json";
        }
        JsonNode h = tree.get("h");
        return h != null && h.isTextual() && !CallChannelBodyBean.isKnownKind(h.textValue()) ? "kept_member" : "schema";
    }

    static Map<String, Object> verdict(String body) {
        Map<String, Object> e = new LinkedHashMap<>();
        final CallChannelBodyBean b;
        try {
            b = CallChannelBodyBean.parseChannelBody(body);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            e.put("ok", false);
            e.put("stage", refusalStage(body, ex));
            // what HybridChannel.acceptChannel throws for such an open
            e.put("error", CallError.KEY_REFUSED.code());
            e.put("reason", "body");
            return e;
        }
        String canonical = CallJson.canonical(b);
        e.put("ok", true);
        e.put("h", b.getH());
        e.put("known_kind", b.knownKind());
        e.put("ignored_kind", b.ignoredKind());
        e.put("epoch", b.getEpoch());
        e.put("conv_seed", b.getConvSeed());
        e.put("declared_media", b.declaredMedia());
        e.put("canonical", canonical);
        e.put("plaintext_hex", CallBytes.hex(CallBytes.ascii(canonical)));
        return e;
    }

    static Map<String, Object> bodyCase(String name, String note, String body) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("name", name);
        c.put("note", note);
        c.put("body", body);
        c.put("expect", verdict(body));
        return c;
    }

    /** The vector file as this reference generates it now. */
    static String generate() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("_origin", "Messages CallChannelBodyUnknownKindTest (audio-only calls S1, Iris §7.189 F1; design "
                + "AUDIO_ONLY_CALLS_DESIGN_2026-11-19 §6 «The exact parse spec»; Java CallChannelBodyBean.parseChannelBody, "
                + "Dart CallChannelBody.parse). `body` is the plaintext of a channel box as received (an open or a message). "
                + "Strict JSON first (duplicate keys and a non-object refused); then an `h` that is a JSON string and not one "
                + "of `known_kinds` (case-sensitive) is an UNKNOWN kind: only `h`, `epoch`, `conv_seed` are kept, every other "
                + "member is dropped before any schema check; `epoch` must be a JSON integer within ±(2^53-1), `conv_seed` a "
                + "JSON string, JSON null = absent; and the WHOLE body, dropped members included, must pass what the Dart "
                + "reader (StrictJson) refuses anywhere while reading: every JSON integer within ±(2^53-1) (a fraction or "
                + "an exponent form in a dropped member stays allowed) and no value nested deeper than `max_depth` "
                + "containers (the root object is depth 0). A known, absent or non-string `h` keeps the full strict schema. "
                + "`expect.ok`: accepted or refused; a port must agree on `ok` and, when accepted, on every other member. "
                + "`stage` is where THIS reference refuses (json | kept_member | any_member | schema), `error`/`reason` what "
                + "HybridChannel.acceptChannel throws for such an open (key_refused / body). Accepted: `canonical` = JCS of "
                + "the parsed body (for an unknown kind: of the kept members only), `plaintext_hex` = its ASCII, "
                + "`known_kind` = h is a known kind, `ignored_kind` = h is a string and not a known kind (on an open: a "
                + "non-handover open that still applies O-7 with `epoch` and §10.1 with `conv_seed`; on a message: ignored, "
                + "no refusal), `declared_media` = CallChannelBodyBean.declaredMedia (null for any non-media kind).");
        root.put("known_kinds", List.of(CallConstants.H_EPOCH, CallConstants.H_BROADCAST, CallConstants.H_CATCHUP,
                CallConstants.H_MEDIA));
        root.put("unknown_kind_keeps", List.of("h", "epoch", "conv_seed"));
        root.put("max_safe_integer", (1L << 53) - 1);
        root.put("max_depth", CallChannelBodyBean.MAX_DEPTH);
        List<Map<String, Object>> cases = new ArrayList<>();
        // unknown kinds: accepted, reduced to the kept members
        cases.add(bodyCase("unknown_extra_field", "an unknown kind drops an unknown member", "{\"h\":\"future\",\"x\":1}"));
        cases.add(bodyCase("unknown_keeps_epoch_conv_seed", "epoch and conv_seed are kept, secret is dropped",
                "{\"h\":\"future\",\"epoch\":7,\"conv_seed\":\"ab\",\"secret\":\"zz\"}"));
        cases.add(bodyCase("unknown_drops_members_of_known_kinds", "the members of the known kinds are dropped too",
                "{\"h\":\"future\",\"media\":\"audio\",\"roster\":[\"aa\"],\"secret\":\"zz\",\"key\":\"00\",\"req\":\"r\","
                + "\"era\":\"e\",\"chain_idx\":1,\"since\":2,\"prev_ts\":3}"));
        cases.add(bodyCase("unknown_with_media_is_no_declaration", "media on an unknown kind: dropped, declared_media null",
                "{\"h\":\"future\",\"media\":\"audio\"}"));
        cases.add(bodyCase("unknown_nested_extra", "dropped members may be of any JSON type",
                "{\"h\":\"future\",\"x\":{\"y\":[1,2.5,null]},\"z\":true,\"w\":null}"));
        cases.add(bodyCase("unknown_epoch_null", "epoch:null = absent", "{\"h\":\"future\",\"epoch\":null}"));
        cases.add(bodyCase("unknown_conv_seed_null", "conv_seed:null = absent", "{\"h\":\"future\",\"conv_seed\":null}"));
        cases.add(bodyCase("unknown_epoch_max_safe", "epoch = 2^53-1 is in the safe range",
                "{\"h\":\"future\",\"epoch\":9007199254740991}"));
        cases.add(bodyCase("unknown_case_sensitive_media", "kinds are case-sensitive: Media is unknown", "{\"h\":\"Media\"}"));
        cases.add(bodyCase("unknown_upper_case", "an upper-case kind", "{\"h\":\"FUTURE\"}"));
        cases.add(bodyCase("unknown_empty_string", "the empty string is a string and not a known kind", "{\"h\":\"\"}"));
        cases.add(bodyCase("unknown_epoch_min_safe", "epoch = -(2^53-1) is in the safe range",
                "{\"h\":\"future\",\"epoch\":-9007199254740991}"));
        cases.add(bodyCase("unknown_dropped_integer_max_safe", "a dropped integer = 2^53-1 is in the safe range",
                "{\"h\":\"future\",\"x\":9007199254740991}"));
        cases.add(bodyCase("unknown_dropped_fraction", "a fraction in a dropped member is allowed (as in Dart)",
                "{\"h\":\"future\",\"x\":1.5}"));
        cases.add(bodyCase("unknown_dropped_depth_max", "a dropped member nested to max_depth (innermost value at depth 64)",
                "{\"h\":\"future\",\"x\":" + "[".repeat(64) + "]".repeat(64) + "}"));
        // unknown kinds: refused
        cases.add(bodyCase("unknown_epoch_string", "a kept member stays strictly typed: epoch as a string",
                "{\"h\":\"future\",\"epoch\":\"7\"}"));
        cases.add(bodyCase("unknown_epoch_fraction", "epoch as a fraction", "{\"h\":\"future\",\"epoch\":1.5}"));
        cases.add(bodyCase("unknown_epoch_float_form", "epoch in a float form", "{\"h\":\"future\",\"epoch\":7.0}"));
        cases.add(bodyCase("unknown_epoch_unsafe", "epoch = 2^53 is outside the safe range",
                "{\"h\":\"future\",\"epoch\":9007199254740992}"));
        cases.add(bodyCase("unknown_conv_seed_number", "conv_seed as a number", "{\"h\":\"future\",\"conv_seed\":1}"));
        cases.add(bodyCase("unknown_epoch_unsafe_negative", "epoch = -2^53 is outside the safe range",
                "{\"h\":\"future\",\"epoch\":-9007199254740992}"));
        cases.add(bodyCase("unknown_dropped_integer_unsafe", "every integer, dropped ones included: 2^53 is outside the safe range",
                "{\"h\":\"future\",\"x\":9007199254740992}"));
        cases.add(bodyCase("unknown_dropped_integer_unsafe_negative", "a dropped integer = -2^53",
                "{\"h\":\"future\",\"x\":-9007199254740992}"));
        cases.add(bodyCase("unknown_dropped_nested_integer_unsafe", "an unsafe integer nested inside a dropped member",
                "{\"h\":\"future\",\"x\":{\"y\":[1,9007199254740993]}}"));
        cases.add(bodyCase("unknown_dropped_too_deep", "a dropped member nested past max_depth (a value at depth 65)",
                "{\"h\":\"future\",\"x\":" + "[".repeat(65) + "]".repeat(65) + "}"));
        cases.add(bodyCase("unknown_duplicate_dropped_key", "strict JSON first: a duplicate key, even of a dropped member",
                "{\"h\":\"future\",\"x\":1,\"x\":2}"));
        cases.add(bodyCase("unknown_duplicate_h", "h twice", "{\"h\":\"future\",\"h\":\"other\"}"));
        // known, absent or non-string h: the strict schema
        cases.add(bodyCase("known_media_extra_field", "a known kind keeps the strict schema: ms is refused",
                "{\"h\":\"media\",\"media\":\"audio\",\"ms\":1}"));
        cases.add(bodyCase("h_number", "a non-string h keeps the strict schema", "{\"h\":1}"));
        cases.add(bodyCase("h_boolean", "h as a boolean", "{\"h\":true}"));
        cases.add(bodyCase("h_object", "h as an object", "{\"h\":{\"k\":\"v\"}}"));
        cases.add(bodyCase("h_null_extra_field", "h:null = absent h: the strict schema refuses x", "{\"h\":null,\"x\":1}"));
        cases.add(bodyCase("h_null", "h:null = absent: the empty body", "{\"h\":null}"));
        cases.add(bodyCase("no_h_extra_field", "no h: the strict schema refuses x", "{\"x\":1}"));
        cases.add(bodyCase("empty_body", "control: the empty body", "{}"));
        cases.add(bodyCase("no_h_conv_seed", "control: an era-0 inv open without a handover", "{\"conv_seed\":\"ab\"}"));
        cases.add(bodyCase("known_media", "control: the S0 media notice", "{\"h\":\"media\",\"media\":\"audio\"}"));
        cases.add(bodyCase("known_media_escaped_h", "a JSON escape decodes to the known kind media",
                "{\"h\":\"\\u006dedia\",\"media\":\"audio\"}"));
        cases.add(bodyCase("known_catchup", "control: the catch-up request", "{\"h\":\"catchup\",\"req\":\"r\",\"epoch\":3}"));
        cases.add(bodyCase("known_catchup_epoch_string", "control: a known kind with a wrongly typed epoch",
                "{\"h\":\"catchup\",\"epoch\":\"3\"}"));
        // not an object
        cases.add(bodyCase("not_an_object_array", "a JSON array", "[]"));
        cases.add(bodyCase("not_an_object_string", "a JSON string", "\"future\""));
        cases.add(bodyCase("not_an_object_null", "JSON null", "null"));
        // no JSON value at all
        cases.add(bodyCase("no_value_empty", "the empty text: no JSON value", ""));
        cases.add(bodyCase("no_value_spaces", "spaces only: no JSON value", "   "));
        cases.add(bodyCase("no_value_newline", "a newline only: no JSON value", "\n"));
        root.put("body_cases", cases);
        return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(root) + "\n";
    }

    static JsonNode committed() throws Exception {
        try (InputStream in = CallChannelBodyUnknownKindTest.class.getResourceAsStream(RESOURCE)) {
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
        try (InputStream in = CallChannelBodyUnknownKindTest.class.getResourceAsStream(RESOURCE)) {
            assertNotNull(in, "missing " + RESOURCE + " — run with -Dcall.vectors.write=true");
            committed = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(generated, committed, "the committed vector file drifted from the reference");
    }

    @Test
    void theVectorPinsTheIntendedVerdicts() throws Exception {
        Map<String, String> got = new LinkedHashMap<>();
        for (JsonNode c : committed().get("body_cases")) {
            JsonNode e = c.get("expect");
            got.put(c.get("name").asText(), e.get("ok").asBoolean()
                    ? e.get("canonical").asText() + " | " + (e.get("known_kind").asBoolean() ? "known" : e.get("ignored_kind").asBoolean()
                    ? "ignored" : "no-kind") + " | " + (e.get("declared_media").isNull() ? "<none>" : e.get("declared_media").asText())
                    : "refused:" + e.get("stage").asText());
        }
        Map<String, String> want = new LinkedHashMap<>();
        want.put("unknown_extra_field", "{\"h\":\"future\"} | ignored | <none>");
        want.put("unknown_keeps_epoch_conv_seed", "{\"conv_seed\":\"ab\",\"epoch\":7,\"h\":\"future\"} | ignored | <none>");
        want.put("unknown_drops_members_of_known_kinds", "{\"h\":\"future\"} | ignored | <none>");
        want.put("unknown_with_media_is_no_declaration", "{\"h\":\"future\"} | ignored | <none>");
        want.put("unknown_nested_extra", "{\"h\":\"future\"} | ignored | <none>");
        want.put("unknown_epoch_null", "{\"h\":\"future\"} | ignored | <none>");
        want.put("unknown_conv_seed_null", "{\"h\":\"future\"} | ignored | <none>");
        want.put("unknown_epoch_max_safe", "{\"epoch\":9007199254740991,\"h\":\"future\"} | ignored | <none>");
        want.put("unknown_case_sensitive_media", "{\"h\":\"Media\"} | ignored | <none>");
        want.put("unknown_upper_case", "{\"h\":\"FUTURE\"} | ignored | <none>");
        want.put("unknown_empty_string", "{\"h\":\"\"} | ignored | <none>");
        want.put("unknown_epoch_min_safe", "{\"epoch\":-9007199254740991,\"h\":\"future\"} | ignored | <none>");
        want.put("unknown_dropped_integer_max_safe", "{\"h\":\"future\"} | ignored | <none>");
        want.put("unknown_dropped_fraction", "{\"h\":\"future\"} | ignored | <none>");
        want.put("unknown_dropped_depth_max", "{\"h\":\"future\"} | ignored | <none>");
        want.put("unknown_epoch_string", "refused:kept_member");
        want.put("unknown_epoch_fraction", "refused:kept_member");
        want.put("unknown_epoch_float_form", "refused:kept_member");
        want.put("unknown_epoch_unsafe", "refused:kept_member");
        want.put("unknown_conv_seed_number", "refused:kept_member");
        want.put("unknown_epoch_unsafe_negative", "refused:kept_member");
        want.put("unknown_dropped_integer_unsafe", "refused:any_member");
        want.put("unknown_dropped_integer_unsafe_negative", "refused:any_member");
        want.put("unknown_dropped_nested_integer_unsafe", "refused:any_member");
        want.put("unknown_dropped_too_deep", "refused:any_member");
        want.put("unknown_duplicate_dropped_key", "refused:json");
        want.put("unknown_duplicate_h", "refused:json");
        want.put("known_media_extra_field", "refused:schema");
        want.put("h_number", "refused:schema");
        want.put("h_boolean", "refused:schema");
        want.put("h_object", "refused:schema");
        want.put("h_null_extra_field", "refused:schema");
        want.put("h_null", "{} | no-kind | <none>");
        want.put("no_h_extra_field", "refused:schema");
        want.put("empty_body", "{} | no-kind | <none>");
        want.put("no_h_conv_seed", "{\"conv_seed\":\"ab\"} | no-kind | <none>");
        want.put("known_media", "{\"h\":\"media\",\"media\":\"audio\"} | known | audio");
        want.put("known_media_escaped_h", "{\"h\":\"media\",\"media\":\"audio\"} | known | audio");
        want.put("known_catchup", "{\"epoch\":3,\"h\":\"catchup\",\"req\":\"r\"} | known | <none>");
        want.put("known_catchup_epoch_string", "refused:schema");
        want.put("not_an_object_array", "refused:json");
        want.put("not_an_object_string", "refused:json");
        want.put("not_an_object_null", "refused:json");
        want.put("no_value_empty", "refused:json");
        want.put("no_value_spaces", "refused:json");
        want.put("no_value_newline", "refused:json");
        assertEquals(want, got);
    }

    @Test
    void everyVectorCaseReDerivesFromTheFileAlone() throws Exception {
        ObjectMapper plain = new ObjectMapper();
        for (JsonNode c : committed().get("body_cases")) {
            // through the text, so a Long epoch compares equal to the int node the file reads back
            assertEquals(c.get("expect"), plain.readTree(plain.writeValueAsString(verdict(c.get("body").asText()))),
                    c.get("name").asText());
        }
    }
}
