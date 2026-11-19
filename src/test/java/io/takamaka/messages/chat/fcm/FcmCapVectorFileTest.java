/*
 * Copyright 2026 AiliA SA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.takamaka.messages.chat.fcm;

import com.fasterxml.jackson.databind.JsonNode;
import io.takamaka.messages.chat.core.ServerInfoResponseBean;
import io.takamaka.messages.exception.MessageException;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.messages.utils.ChatUtils;
import io.takamaka.messages.utils.SimpleRequestHelper;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The committed {@code java_fcm_cap_vectors.json} (DR-074 amendment 1, A-16) is the Java reference output, and every
 * row has the verdict the amendment asks for. rsclient-flutter mirrors the SAME file byte for byte and runs the
 * {@code normative} rows (V1, V2, V2n, V3, V4, V7, V12, V13) through its Dart {@code validate} and builder.
 *
 * <p>To regenerate deliberately: {@code mvn -o test -Dtest=FcmCapVectorFileTest -Dfcm.cap.vectors.write=true}.</p>
 */
class FcmCapVectorFileTest {

    static final String RESOURCE = "/java_fcm_cap_vectors.json";
    static final Path SOURCE = Paths.get("src/test/resources/java_fcm_cap_vectors.json");
    static final String INVALID = FcmCapabilities.ERROR_INVALID_CAP;
    static String text;
    static JsonNode root;
    static InstanceWalletKeystoreInterface iwk;

    @BeforeAll
    static void load() throws Exception {
        if (Boolean.getBoolean("fcm.cap.vectors.write")) {
            Files.writeString(SOURCE, FcmCapVectorGenerator.render());
            text = Files.readString(SOURCE);
        } else {
            try (InputStream in = FcmCapVectorFileTest.class.getResourceAsStream(RESOURCE)) {
                assertNotNull(in, "missing " + RESOURCE + " — run with -Dfcm.cap.vectors.write=true");
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        root = FcmCapVectorGenerator.M.readTree(text);
        iwk = new InstanceWalletKeyStoreBCED25519("fcm_cap_vector_test_wallet", "superSecretPassword");
    }

    private static Map<String, JsonNode> rows() {
        Map<String, JsonNode> m = new LinkedHashMap<>();
        for (JsonNode r : root.get("vectors")) {
            assertNull(m.put(r.get("id").asText(), r), "duplicate row id");
        }
        return m;
    }

    @Test
    void committedFileEqualsTheReferenceOutput() throws Exception {
        assertEquals(FcmCapVectorGenerator.render(), text,
                "vectors drifted from the Java reference: regenerate deliberately (-Dfcm.cap.vectors.write=true)");
        assertTrue(StandardCharsets.US_ASCII.newEncoder().canEncode(text), "the vector file is ASCII");
    }

    @Test
    void theConstantsArePinned() {
        JsonNode k = root.get("cap_constants");
        assertEquals("calls", k.get("calls").asText());
        assertEquals("INVALID_CAP", k.get("error_invalid_cap").asText());
        assertEquals("cap", k.get("cap_key").asText());
        assertEquals("registerfcmtoken", k.get("endpoint").asText());
        assertEquals("FCM_TOKEN_REGISTRATION", k.get("message_type").asText());
        assertEquals(8, k.get("max_values").asInt());
        assertEquals(32, k.get("max_value_length").asInt());
        assertEquals("[a-z0-9-]{1,32}", k.get("value_regex").asText());
        // A-24: the exact text of the storage CHECK of migration 550
        assertEquals("^[a-z0-9-]{1,32}(,[a-z0-9-]{1,32}){0,7}$", k.get("storage_check_regex").asText());
        assertEquals(263, k.get("max_stored_length").asInt());
        assertEquals(List.of("calls"), FcmCapVectorGenerator.M.convertValue(root.get("server_known"), List.class));
    }

    /** The verdicts of A-16, written out by hand: the generator must agree with the amendment, not with itself. */
    @Test
    void everyRowHasTheVerdictOfTheAmendment() {
        // id -> {expect (signer, shape only), expect_server (server advertising ["calls"])}
        Map<String, String[]> want = new LinkedHashMap<>();
        want.put("V1", new String[]{"OK", "OK"});
        want.put("V2", new String[]{"OK", "OK"});
        want.put("V2n", new String[]{"OK", "OK"});
        want.put("V3", new String[]{INVALID, INVALID});
        want.put("V4", new String[]{INVALID, INVALID});
        want.put("V5", new String[]{"OK", "OK"});
        want.put("V7", new String[]{INVALID, INVALID});
        want.put("V8a", new String[]{INVALID, INVALID});
        want.put("V8b", new String[]{INVALID, INVALID});
        want.put("V9", new String[]{"OK", INVALID});
        want.put("V12", new String[]{INVALID, INVALID});
        want.put("V13", new String[]{INVALID, INVALID});
        want.put("B1", new String[]{"OK", INVALID});
        want.put("B2", new String[]{"OK", INVALID});
        want.put("B3", new String[]{"OK", INVALID});
        want.put("B4", new String[]{"OK", INVALID});
        want.put("B5", new String[]{INVALID, INVALID});
        want.put("B6", new String[]{INVALID, INVALID});
        want.put("B7", new String[]{INVALID, INVALID});
        want.put("B8", new String[]{INVALID, INVALID});
        want.put("B9", new String[]{INVALID, INVALID});
        want.put("B10", new String[]{INVALID, INVALID});
        Map<String, JsonNode> rows = rows();
        assertEquals(want.keySet(), rows.keySet());
        for (Map.Entry<String, String[]> w : want.entrySet()) {
            JsonNode r = rows.get(w.getKey());
            List<String> cap = FcmCapVectorGenerator.capOf(r);
            assertEquals(w.getValue()[0], r.get("expect").asText(), w.getKey());
            assertEquals(w.getValue()[1], r.get("expect_server").asText(), w.getKey());
            assertEquals(w.getValue()[0], FcmCapVectorGenerator.code(FcmCapabilities.validate(cap, null)), w.getKey());
            assertEquals(w.getValue()[1], FcmCapVectorGenerator.code(FcmCapabilities.validate(cap, Set.of("calls"))),
                    w.getKey());
            assertEquals("OK".equals(w.getValue()[0]), r.has("canonical"), w.getKey());
            assertEquals("OK".equals(w.getValue()[0]), r.has("stored_form"), w.getKey());
        }
        // C-3: the normative rows are exactly those named by the amendment
        Set<String> normative = new java.util.TreeSet<>();
        rows.values().stream().filter(r -> "normative".equals(r.get("kind").asText()))
                .forEach(r -> normative.add(r.get("id").asText()));
        assertEquals(new java.util.TreeSet<>(List.of("V1", "V2", "V2n", "V3", "V4", "V7", "V12", "V13")), normative);
        // only V2n overrides the fixed device_id
        rows.values().forEach(r -> assertEquals("V2n".equals(r.get("id").asText()), r.has("device_id"),
                r.get("id").asText()));
    }

    @Test
    void v1IsByteIdenticalToTodaysVector_andV2IsTheDocumentedForm() {
        Map<String, JsonNode> rows = rows();
        assertEquals(FcmCanonicalVectorTest.VECTOR_WITH_DEVICE, rows.get("V1").get("canonical").asText());
        assertEquals(FcmCanonicalVectorTest.VECTOR_WITH_DEVICE, rows.get("V5").get("canonical").asText(),
                "an empty list is absent: never sent as []");
        assertEquals("{\"cap\":[\"calls\"],\"device_id\":\"device-01\",\"fcm_token\":\"fixed-fcm-token-XYZ\","
                + "\"nonce\":{\"liveness\":60000,\"nonce\":\"11111111-2222-3333-4444-555555555555\","
                + "\"timestamp\":1700000000000},\"platform\":\"android\"}", rows.get("V2").get("canonical").asText());
        // L-2: cap present AND device_id's explicit null (NON_EMPTY is cap's alone)
        assertTrue(rows.get("V2n").get("device_id").isNull());
        assertEquals("{\"cap\":[\"calls\"],\"device_id\":null,\"fcm_token\":\"fixed-fcm-token-XYZ\","
                + "\"nonce\":{\"liveness\":60000,\"nonce\":\"11111111-2222-3333-4444-555555555555\","
                + "\"timestamp\":1700000000000},\"platform\":\"android\"}", rows.get("V2n").get("canonical").asText());
        assertEquals("calls", rows.get("V2n").get("stored_form").asText());
        assertFalse(rows.get("V1").has("cap"));
        assertTrue(rows.get("V1").get("stored_form").isNull(), "absent is stored as NULL, never ''");
        assertTrue(rows.get("V5").get("stored_form").isNull());
        assertEquals("calls", rows.get("V2").get("stored_form").asText());
        // the inputs survive the file: a null element and a line feed are representable for the Dart mirror
        assertTrue(rows.get("V12").get("cap").get(0).isNull());
        assertEquals("calls\n", rows.get("V13").get("cap").get(0).asText());
        assertEquals(263, rows.get("B1").get("stored_form").asText().length());
    }

    /**
     * The Java signer (the 7-argument {@code getSignedFcmTokenRegistrationRequest}) on every row: an {@code OK} row is
     * signed with exactly the row's canonical form and verifies through the server's path; an {@code INVALID_CAP}
     * row is refused BEFORE signing with a {@link MessageException} naming the code, and nothing else escapes (V12).
     */
    @Test
    void theSignerFollowsEveryRow() throws Exception {
        for (JsonNode r : rows().values()) {
            String id = r.get("id").asText();
            List<String> cap = FcmCapVectorGenerator.capOf(r);
            if ("OK".equals(r.get("expect").asText())) {
                FcmTokenRegistrationRequestBean req = ChatCryptoUtils.getSignedFcmTokenRegistrationRequest(
                        FcmCapVectorGenerator.NONCE, FcmCapVectorGenerator.TOKEN, FcmCapVectorGenerator.PLATFORM,
                        FcmCapVectorGenerator.deviceOf(r), cap, iwk, 0);
                assertEquals(r.get("canonical").asText(),
                        SimpleRequestHelper.getCanonicalJson(req.getFcmTokenRegistrationSignedContentBean()), id);
                String wire = TkmTextUtils.getJacksonMapper().writeValueAsString(req);
                assertNotNull(ChatCryptoUtils.verifySignedMessage(wire), id);
                List<String> parsed = ChatUtils.fromJsonToFcmTokenRegistrationRequestBean(wire)
                        .getFcmTokenRegistrationSignedContentBean().getCap();
                assertEquals(cap == null || cap.isEmpty() ? null : cap, parsed, id);
                assertFalse(wire.contains("\"cap\":[]") || wire.contains("\"cap\":null"), id + ": " + wire);
            } else {
                MessageException ex = assertThrows(MessageException.class,
                        () -> ChatCryptoUtils.getSignedFcmTokenRegistrationRequest(FcmCapVectorGenerator.NONCE,
                                FcmCapVectorGenerator.TOKEN, FcmCapVectorGenerator.PLATFORM,
                                FcmCapVectorGenerator.deviceOf(r), cap, iwk, 0), id);
                assertTrue(ex.getMessage().startsWith("INVALID_CAP"), id + ": " + ex.getMessage());
            }
        }
    }

    @Test
    void hasValueVectors_matchWholeValuesOnly() {
        JsonNode hv = root.get("has_value_vectors");
        assertEquals(8, hv.size());
        int trues = 0;
        for (JsonNode n : hv) {
            String stored = n.get("stored").isNull() ? null : n.get("stored").asText();
            boolean expect = n.get("expect").asBoolean();
            assertEquals(expect, FcmCapabilities.hasValue(stored, n.get("value").asText()), String.valueOf(stored));
            trues += expect ? 1 : 0;
        }
        // calls, calls,other, a,calls; never calls-2 (C-5), never a substring
        assertEquals(3, trues);
    }

    @Test
    void serverInfoVectors_presentAt18_absentAt17() throws Exception {
        JsonNode si = root.get("serverinfo_vectors");
        assertEquals("1.8", si.get("manifest_version_current").asText());
        ServerInfoResponseBean b18 = FcmCapVectorGenerator.M.treeToValue(si.get("manifest_1_8_json"),
                ServerInfoResponseBean.class);
        assertEquals("1.8", b18.getManifestVersion());
        assertEquals(Set.of("calls"), b18.getFcmTokenCapabilities());
        assertFalse(si.get("manifest_1_7_json").has("fcmTokenCapabilities"));
        ServerInfoResponseBean b17 = FcmCapVectorGenerator.M.treeToValue(si.get("manifest_1_7_json"),
                ServerInfoResponseBean.class);
        assertNull(b17.getFcmTokenCapabilities(), "a 1.7 server stores no cap: null, never an empty set");
        assertEquals("rschat:rig1", b17.getAudience());
        assertTrue(si.get("manifest_1_7_expected_fcm_token_capabilities").isNull());
    }
}
