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
import io.takamaka.messages.call.PushSeal;
import io.takamaka.messages.chat.core.ServerInfoResponseBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.utils.CHAT_MESSAGE_TYPES;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The committed {@code java_fcm_push_key_vectors.json} (DR-063 §3.3) is the Java reference output: an old client's
 * canonical registration is byte-identical to today's, the push fields sign and verify through the chat path, and the
 * response / serverinfo members are absent when not set. rsclient-flutter mirrors the same file.
 *
 * <p>To regenerate deliberately: {@code mvn -o test -Dtest=FcmPushKeyVectorFileTest -Dfcm.push.key.vectors.write=true}.
 */
class FcmPushKeyVectorFileTest {

    static final String RESOURCE = "/java_fcm_push_key_vectors.json";
    static final Path SOURCE = Paths.get("src/test/resources/java_fcm_push_key_vectors.json");
    static String text;
    static JsonNode root;
    static InstanceWalletKeystoreInterface iwk;

    @BeforeAll
    static void load() throws Exception {
        if (Boolean.getBoolean("fcm.push.key.vectors.write")) {
            Files.writeString(SOURCE, FcmPushKeyVectorGenerator.render());
            text = Files.readString(SOURCE);
        } else {
            try (InputStream in = FcmPushKeyVectorFileTest.class.getResourceAsStream(RESOURCE)) {
                assertNotNull(in, "missing " + RESOURCE + " — run with -Dfcm.push.key.vectors.write=true");
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        root = FcmPushKeyVectorGenerator.M.readTree(text);
        iwk = new InstanceWalletKeyStoreBCED25519("fcm_push_key_vector_test_wallet", "superSecretPassword");
    }

    static Map<String, JsonNode> rows() {
        Map<String, JsonNode> m = new LinkedHashMap<>();
        for (JsonNode r : root.get("vectors")) {
            assertNull(m.put(r.get("id").asText(), r), "duplicate row id");
        }
        return m;
    }

    static String str(JsonNode r, String key) {
        return r.get(key) == null || r.get(key).isNull() ? null : r.get(key).asText();
    }

    static FcmTokenRegistrationSignedContentBean contentOf(JsonNode r) {
        List<String> cap = r.has("cap") ? FcmPushKeyVectorGenerator.M.convertValue(r.get("cap"),
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
        }) : null;
        return FcmPushKeyVectorGenerator.content(str(r, "device_id"), cap, str(r, "push_key"), str(r, "push_seal"));
    }

    @Test
    void committedFileEqualsTheReferenceOutput() throws Exception {
        assertEquals(FcmPushKeyVectorGenerator.render(), text,
                "vectors drifted from the Java reference: regenerate deliberately (-Dfcm.push.key.vectors.write=true)");
        assertTrue(StandardCharsets.US_ASCII.newEncoder().canEncode(text), "the vector file is ASCII");
    }

    @Test
    void anOldClientsCanonicalFormIsUnchanged() throws Exception {
        Map<String, JsonNode> rows = rows();
        assertEquals(FcmCanonicalVectorTest.VECTOR_WITH_DEVICE, rows.get("P0").get("canonical").asText());
        assertEquals(FcmCanonicalVectorTest.VECTOR_NULL_DEVICE, rows.get("P0n").get("canonical").asText());
        // and through every constructor an old caller uses
        assertEquals(FcmCanonicalVectorTest.VECTOR_WITH_DEVICE, SimpleRequestHelper.getCanonicalJson(
                new FcmTokenRegistrationSignedContentBean(FcmPushKeyVectorGenerator.NONCE, "fixed-fcm-token-XYZ",
                        "android", "device-01")));
        assertEquals(FcmCanonicalVectorTest.VECTOR_WITH_DEVICE, SimpleRequestHelper.getCanonicalJson(
                new FcmTokenRegistrationSignedContentBean(FcmPushKeyVectorGenerator.NONCE, "fixed-fcm-token-XYZ",
                        "android", "device-01", null)));
    }

    @Test
    void theDocumentedForms() {
        Map<String, JsonNode> rows = rows();
        String key = FcmPushKeyVectorGenerator.PUSH_KEY;
        assertEquals(44, key.length());
        assertEquals("{\"device_id\":\"device-01\",\"fcm_token\":\"fixed-fcm-token-XYZ\","
                + "\"nonce\":{\"liveness\":60000,\"nonce\":\"11111111-2222-3333-4444-555555555555\","
                + "\"timestamp\":1700000000000},\"platform\":\"android\",\"push_key\":\"" + key + "\","
                + "\"push_seal\":\"tkm-push/v1\"}", rows.get("P1").get("canonical").asText());
        assertEquals("{\"cap\":[\"calls\"],\"device_id\":null,\"fcm_token\":\"fixed-fcm-token-XYZ\","
                + "\"nonce\":{\"liveness\":60000,\"nonce\":\"11111111-2222-3333-4444-555555555555\","
                + "\"timestamp\":1700000000000},\"platform\":\"android\",\"push_key\":\"" + key + "\","
                + "\"push_seal\":\"tkm-push/v1\"}", rows.get("P3").get("canonical").asText());
        assertEquals("OK", rows.get("P1").get("push_key_check").asText());
        assertEquals("LOW_ORDER", rows.get("P4").get("push_key_check").asText(), "the low-order point is refused");
        assertEquals("ABSENT", rows.get("P0").get("push_key_check").asText());
        assertFalse(rows.get("P5").get("canonical").asText().contains("push_seal"));
        assertTrue(rows.get("P1").get("push_key_id").asText().matches("[0-9a-f]{16}"));
        // the same key id as the push-seal vector file's dev-1
        assertEquals(PushSeal.kidHex(FcmPushKeyVectorGenerator.pushKeyBytes()), rows.get("P1").get("push_key_id").asText());
    }

    /** Every row signs through the chat path, verifies on the server's path, and parses back with its push fields. */
    @Test
    void signVerifyRoundTrip() throws Exception {
        for (JsonNode r : rows().values()) {
            String id = r.get("id").asText();
            FcmTokenRegistrationSignedContentBean content = contentOf(r);
            String canonical = SimpleRequestHelper.getCanonicalJson(content);
            assertEquals(r.get("canonical").asText(), canonical, id);
            FcmTokenRegistrationRequestBean req = new FcmTokenRegistrationRequestBean(content,
                    iwk.getPublicKeyAtIndexURL64(0), SimpleRequestHelper.signChatMessage(canonical, iwk, 0),
                    CHAT_MESSAGE_TYPES.FCM_TOKEN_REGISTRATION.name(), iwk.getWalletCypher().name());
            String wire = TkmTextUtils.getJacksonMapper().writeValueAsString(req);
            assertNotNull(ChatCryptoUtils.verifySignedMessage(wire), id);
            FcmTokenRegistrationSignedContentBean parsed = ChatUtils.fromJsonToFcmTokenRegistrationRequestBean(wire)
                    .getFcmTokenRegistrationSignedContentBean();
            assertEquals(str(r, "push_key"), parsed.getPushKey(), id);
            assertEquals(str(r, "push_seal"), parsed.getPushSeal(), id);
            assertFalse(wire.contains("\"push_key\":null") || wire.contains("\"push_seal\":null"), id + ": " + wire);
        }
    }

    @Test
    void aSwappedPushKeyBreaksTheSignature() throws Exception {
        JsonNode r = rows().get("P1");
        FcmTokenRegistrationSignedContentBean content = contentOf(r);
        String canonical = SimpleRequestHelper.getCanonicalJson(content);
        FcmTokenRegistrationRequestBean req = new FcmTokenRegistrationRequestBean(content,
                iwk.getPublicKeyAtIndexURL64(0), SimpleRequestHelper.signChatMessage(canonical, iwk, 0),
                CHAT_MESSAGE_TYPES.FCM_TOKEN_REGISTRATION.name(), iwk.getWalletCypher().name());
        // a relay substitutes its own key: the signature no longer verifies
        req.getFcmTokenRegistrationSignedContentBean().setPushKey(rows().get("P4").get("push_key").asText());
        String swapped = TkmTextUtils.getJacksonMapper().writeValueAsString(req);
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(swapped));
        // a relay strips both fields: the signature no longer verifies either
        req.getFcmTokenRegistrationSignedContentBean().setPushKey(null);
        req.getFcmTokenRegistrationSignedContentBean().setPushSeal(null);
        String stripped = TkmTextUtils.getJacksonMapper().writeValueAsString(req);
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(stripped));
    }

    @Test
    void responseAndServerInfoMembers() throws Exception {
        JsonNode resp = root.get("response_vectors");
        assertEquals(PushSeal.kidHex(FcmPushKeyVectorGenerator.pushKeyBytes()), resp.get("stored").get("push_key_id").asText());
        assertFalse(resp.get("not_stored").has("push_key_id"));
        FcmTokenRegistrationResponseBean parsed = TkmTextUtils.getJacksonMapper().treeToValue(resp.get("stored"),
                FcmTokenRegistrationResponseBean.class);
        assertEquals(resp.get("stored").get("push_key_id").asText(), parsed.getPushKeyId());

        JsonNode si = root.get("serverinfo_vectors");
        assertEquals("tkm-push/v1", si.get("with_suite").get("push_seal_suites").get(0).asText());
        assertEquals(1, si.get("with_suite").get("push_seal_suites").size());
        assertFalse(si.get("without_suite").has("push_seal_suites"));
        assertFalse(si.get("empty_suite_list").has("push_seal_suites"));
        assertEquals(List.of("tkm-push/v1"), FcmPushKeyVectorGenerator.M.treeToValue(si.get("with_suite"),
                ServerInfoResponseBean.class).getPushSealSuites());
        assertNull(FcmPushKeyVectorGenerator.M.treeToValue(si.get("without_suite"), ServerInfoResponseBean.class)
                .getPushSealSuites());
        // the 1.8 field set serialises exactly as before (no new member)
        assertEquals(FcmPushKeyVectorGenerator.M.writeValueAsString(si.get("without_suite")),
                FcmPushKeyVectorGenerator.M.writeValueAsString(FcmPushKeyVectorGenerator.M.valueToTree(
                        new ServerInfoResponseBean("0.11.0-SNAPSHOT", "1.2", 1, 0, 2,
                                ServerInfoResponseBean.MANIFEST_VERSION_CURRENT, 0L, 0L, 0, 0, 0L, null, 0L, null,
                                "rschat:rig1", java.util.Set.of(FcmCapabilities.CALLS)))));
    }
}
