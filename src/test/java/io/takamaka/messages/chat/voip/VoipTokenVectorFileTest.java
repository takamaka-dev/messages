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
package io.takamaka.messages.chat.voip;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.takamaka.messages.chat.core.ServerInfoResponseBean;
import io.takamaka.messages.utils.ChatSignatureVerifier;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The committed {@code java_voip_token_vectors.json} (DR-074 §11) is the Java reference output, and every case in it
 * has the verdict the DR asks for. A port (rsclient-flutter) reads the SAME file.
 *
 * <p>To regenerate deliberately: {@code mvn -o test -Dtest=VoipTokenVectorFileTest -Dvoip.vectors.write=true}.</p>
 */
class VoipTokenVectorFileTest {

    static final String RESOURCE = "/voip/java_voip_token_vectors.json";
    static final Path SOURCE = Paths.get("src/test/resources/voip/java_voip_token_vectors.json");
    static final ObjectMapper M = new ObjectMapper();
    static String text;
    static JsonNode root;

    @BeforeAll
    static void load() throws Exception {
        if (Boolean.getBoolean("voip.vectors.write")) {
            Files.createDirectories(SOURCE.getParent());
            Files.writeString(SOURCE, VoipTokenVectorGenerator.render());
            text = Files.readString(SOURCE);
        } else {
            try (InputStream in = VoipTokenVectorFileTest.class.getResourceAsStream(RESOURCE)) {
                assertNotNull(in, "missing " + RESOURCE + " — run with -Dvoip.vectors.write=true");
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        root = M.readTree(text);
    }

    private static Map<String, JsonNode> byName(String array) {
        Map<String, JsonNode> m = new HashMap<>();
        for (JsonNode c : root.get(array)) {
            assertNull(m.put(c.get("name").asText(), c), "duplicate case name");
        }
        return m;
    }

    private static String routeOp(JsonNode c) {
        return "registervoiptoken".equals(c.get("route").asText()) ? VoipTokenRules.OP_REGISTER : VoipTokenRules.OP_UNREGISTER;
    }

    @Test
    void committedFileEqualsTheReferenceOutput() throws Exception {
        assertEquals(VoipTokenVectorGenerator.render(), text,
                "vectors drifted from the Java reference: regenerate deliberately (-Dvoip.vectors.write=true)");
        assertTrue(StandardCharsets.US_ASCII.newEncoder().canEncode(text), "the vector file is ASCII");
    }

    @Test
    void theWireNamesArePinned() {
        JsonNode k = root.get("voip_token_constants");
        assertEquals("registervoiptoken", k.get("endpoint_register").asText());
        assertEquals("unregistervoiptoken", k.get("endpoint_unregister").asText());
        assertEquals("VOIP_TOKEN_REGISTRATION", k.get("message_type").asText());
        assertEquals("voip_token_signed_content", k.get("content_key").asText());
        assertEquals("tkm-voip-token-v1", k.get("token_hash_domain").asText());
        assertEquals("USER_NOT_REGISTERED", VoipTokenRules.USER_NOT_REGISTERED);
    }

    @Test
    void positiveVectors_verifyAndPassTheCheck() throws Exception {
        Map<String, JsonNode> pos = byName("positive_vectors");
        assertEquals(5, pos.size());
        String signer = root.get("signer_identity").get("signer_public_key").asText();
        for (JsonNode c : pos.values()) {
            String name = c.get("name").asText();
            assertEquals("OK", c.get("expected").asText(), name);
            assertEquals("OK", VoipTokenVectorGenerator.verdict(c.get("envelope_json"), routeOp(c), "rschat:rig1"), name);
            // the canonical string is exactly what the signature covers, and its bytes are its UTF-8
            String canon = c.get("signed_canonical_jcs").asText();
            assertEquals(HexFormat.of().formatHex(canon.getBytes(StandardCharsets.UTF_8)),
                    c.get("signed_canonical_utf8_hex").asText(), name);
            assertTrue(ChatSignatureVerifier.verifyEd25519(signer, c.get("signature_b64url").asText(), canon).isValid(), name);
            // seven keys, sorted, the nested nonce sorted too
            assertTrue(canon.startsWith("{\"aud\":\"rschat:rig1\",\"did\":\""), name + ": " + canon);
            assertTrue(canon.contains(",\"nonce\":{\"liveness\":60000,\"nonce\":\"11111111-2222-3333-4444-555555555555\","
                    + "\"timestamp\":1700000000000},\"op\":\""), name + ": " + canon);
            // a server with another audience refuses the same envelope
            assertEquals(VoipTokenRules.AUD_MISMATCH,
                    VoipTokenVectorGenerator.verdict(c.get("envelope_json"), routeOp(c), "rschat:rig2"), name);
        }
        assertEquals(200, pos.get("register_token_200").get("envelope_json").get("voip_token_signed_content")
                .get("vt").asText().length());
    }

    @Test
    void negativeVectors_haveTheVerdictOfTheDesign() throws Exception {
        Map<String, JsonNode> neg = byName("negative_vectors");
        Map<String, String> want = new HashMap<>();
        want.put("vt_uppercase_hex", VoipTokenRules.VALIDATION_ERROR);
        want.put("vt_odd_length", VoipTokenRules.VALIDATION_ERROR);
        want.put("vt_too_short", VoipTokenRules.VALIDATION_ERROR);
        want.put("op_not_the_routes", VoipTokenRules.OP_MISMATCH);
        want.put("op_not_the_routes_reverse", VoipTokenRules.OP_MISMATCH);
        want.put("op_unknown", VoipTokenRules.OP_MISMATCH);
        want.put("env_unknown", VoipTokenRules.VALIDATION_ERROR);
        want.put("tp_forbidden_character", VoipTokenRules.VALIDATION_ERROR);
        want.put("did_too_short", VoipTokenRules.VALIDATION_ERROR);
        want.put("aud_wrong", VoipTokenRules.AUD_MISMATCH);
        // Iris S-5: null verifies and is refused by the field check; absent fails the signature
        want.put("did_null", VoipTokenRules.VOIP_FIELD_MISSING);
        want.put("did_absent", VoipTokenRules.SIGNATURE_ERROR);
        want.put("extra_key_signed", VoipTokenRules.SIGNATURE_ERROR);
        want.put("extra_key_unsigned", VoipTokenRules.VALIDATION_ERROR);
        want.put("content_changed_after_signing", VoipTokenRules.SIGNATURE_ERROR);
        want.put("voip_content_under_fcm_type", VoipTokenRules.SIGNATURE_ERROR);
        want.put("fcm_content_under_voip_type", VoipTokenRules.SIGNATURE_ERROR);
        assertEquals(want.keySet(), neg.keySet());
        String signer = root.get("signer_identity").get("signer_public_key").asText();
        for (JsonNode c : neg.values()) {
            String name = c.get("name").asText();
            assertEquals(want.get(name), c.get("expected").asText(), name);
            assertEquals(want.get(name), VoipTokenVectorGenerator.verdict(c.get("envelope_json"), routeOp(c), "rschat:rig1"), name);
            assertFalse(c.get("nonce_consumed").asBoolean(), name);
            assertEquals(VoipTokenRules.SIGNATURE_ERROR.equals(want.get(name)) ? "signature" : "content_check",
                    c.get("refused_at").asText(), name);
            // every negative envelope carries a signature that IS valid over the recorded canonical form: the
            // refusal is the server's rule, never a broken fixture
            assertTrue(ChatSignatureVerifier.verifyEd25519(signer, c.get("signature_b64url").asText(),
                    c.get("signed_canonical_jcs").asText()).isValid(), name);
        }
        assertTrue(neg.get("did_null").get("signed_canonical_jcs").asText().contains("\"did\":null"));
        assertFalse(neg.get("did_absent").get("signed_canonical_jcs").asText().contains("\"did\""));
        assertFalse(neg.get("did_absent").get("envelope_json").get("voip_token_signed_content").has("did"));
    }

    @Test
    void crossTypeVectors_theFcmCaseRefusesAVoipEnvelope() throws Exception {
        Map<String, JsonNode> x = byName("cross_type_vectors");
        assertEquals(2, x.size());
        for (JsonNode c : x.values()) {
            assertEquals(VoipTokenRules.SIGNATURE_ERROR, c.get("expected").asText(), c.get("name").asText());
            assertEquals(VoipTokenRules.SIGNATURE_ERROR, VoipTokenVectorGenerator.fcmVerdict(c.get("envelope_json")));
        }
    }

    @Test
    void tokenHashVectors_preimageAndHash() throws Exception {
        JsonNode th = root.get("token_hash_vectors");
        assertEquals(3, th.size());
        for (JsonNode n : th) {
            String env = n.get("env").asText(), tp = n.get("tp").asText(), vt = n.get("vt").asText();
            byte[] pre = HexFormat.of().parseHex(n.get("preimage_utf8_hex").asText());
            // the separators, spelled out independently of the production helper
            assertEquals("tkm-voip-token-v1\n" + env + "\n" + tp + "\n" + vt, new String(pre, StandardCharsets.UTF_8));
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pre)), n.get("token_hash").asText());
            assertEquals(n.get("token_hash").asText(), VoipTokenRules.tokenHash(env, tp, vt));
            assertEquals(64, n.get("token_hash").asText().length());
        }
        // the environment and the topic are inside the hash: the same vt gives three different endpoints
        assertFalse(th.get(0).get("token_hash").asText().equals(th.get(1).get("token_hash").asText()));
    }

    @Test
    void serverInfoVectors_audiencePresentAt17_absentAt16() throws Exception {
        JsonNode si = root.get("serverinfo_vectors");
        assertEquals("1.7", si.get("manifest_version_current").asText());
        ServerInfoResponseBean b17 = M.treeToValue(si.get("manifest_1_7_json"), ServerInfoResponseBean.class);
        assertEquals("rschat:rig1", b17.getAudience());
        assertEquals("1.7", b17.getManifestVersion());
        assertFalse(si.get("manifest_1_6_json").has("audience"));
        ServerInfoResponseBean b16 = M.treeToValue(si.get("manifest_1_6_json"), ServerInfoResponseBean.class);
        assertNull(b16.getAudience(), "a 1.6 server has no audience: null, never empty");
        assertTrue(si.get("manifest_1_6_expected_audience").isNull());
    }
}
