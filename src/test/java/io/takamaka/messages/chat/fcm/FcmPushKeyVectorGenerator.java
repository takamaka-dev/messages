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

import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.takamaka.messages.call.CallCrypto;
import io.takamaka.messages.call.PushSeal;
import io.takamaka.messages.chat.constant.ChatServerEndpoints;
import io.takamaka.messages.chat.core.NonceResponseBean;
import io.takamaka.messages.chat.core.ServerInfoResponseBean;
import io.takamaka.messages.utils.CHAT_MESSAGE_TYPES;
import io.takamaka.messages.utils.SimpleRequestHelper;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import org.bouncycastle.util.encoders.UrlBase64;

/**
 * Generates {@code java_fcm_push_key_vectors.json} (DR-063 §3.3 / §8, W1): the canonical form of the FCM registration
 * content with and without {@code push_key} / {@code push_seal}, the {@code push_key_id} echo of the response, and
 * the {@code push_seal_suites} member of {@code serverinfo}. Deterministic: the fixed input of
 * {@code FcmCanonicalVectorTest} and the {@code dev-1} key of {@code call/push_seal_vectors_v1.json}.
 *
 * <p>Regenerate deliberately: {@code mvn -o test -Dtest=FcmPushKeyVectorFileTest -Dfcm.push.key.vectors.write=true}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class FcmPushKeyVectorGenerator {

    private FcmPushKeyVectorGenerator() {
    }

    static final ObjectMapper M = JsonMapper.builder().enable(JsonWriteFeature.ESCAPE_NON_ASCII).build();

    // the fixed input of FcmCanonicalVectorTest.VECTOR_WITH_DEVICE (keep identical)
    static final NonceResponseBean NONCE = new NonceResponseBean(
            "11111111-2222-3333-4444-555555555555", 1700000000000L, 60000L);
    static final String TOKEN = "fixed-fcm-token-XYZ";
    static final String PLATFORM = "android";
    static final String DEVICE_ID = "device-01";

    /** The push key of {@code dev-1} in {@code call/push_seal_vectors_v1.json}. */
    static byte[] pushKeyBytes() {
        return CallCrypto.x25519Public(CallCrypto.h("tkm-push/v1/vectors/device/dev-1".getBytes(StandardCharsets.US_ASCII)));
    }

    static String b64(byte[] b) {
        return new String(UrlBase64.encode(b), StandardCharsets.US_ASCII);
    }

    static final String PUSH_KEY = b64(pushKeyBytes());

    static FcmTokenRegistrationSignedContentBean content(String deviceId, List<String> cap, String pushKey, String pushSeal) {
        return new FcmTokenRegistrationSignedContentBean(NONCE, TOKEN, PLATFORM, deviceId, cap, pushKey, pushSeal);
    }

    static String verdict(String pushKey) {
        if (pushKey == null) {
            return "ABSENT";
        }
        try {
            PushSeal.validatePushKey(pushKey);
            return "OK";
        } catch (PushSeal.PushSealException ex) {
            return ex.getReason().name();
        }
    }

    private static void row(ArrayNode rows, String id, String description, String deviceId, List<String> cap,
            String pushKey, String pushSeal) throws Exception {
        ObjectNode r = rows.addObject();
        r.put("id", id);
        r.put("description", description);
        r.put("device_id", deviceId);
        if (cap != null) {
            ArrayNode a = r.putArray("cap");
            cap.forEach(a::add);
        }
        r.put("push_key", pushKey);
        r.put("push_seal", pushSeal);
        r.put("push_key_check", verdict(pushKey));
        if (pushKey != null && "OK".equals(verdict(pushKey))) {
            r.put("push_key_id", PushSeal.kidHex(PushSeal.validatePushKey(pushKey)));
        }
        r.put("canonical", SimpleRequestHelper.getCanonicalJson(content(deviceId, cap, pushKey, pushSeal)));
    }

    static ServerInfoResponseBean serverInfo(List<String> suites) {
        ServerInfoResponseBean s = new ServerInfoResponseBean();
        s.setServerVersion("0.11.0-SNAPSHOT");
        s.setProtocolCurrent("1.2");
        s.setProtocolMajor(1);
        s.setProtocolMinMinor(0);
        s.setProtocolMaxMinor(2);
        s.setManifestVersion(ServerInfoResponseBean.MANIFEST_VERSION_CURRENT);
        s.setAudience("rschat:rig1");
        s.setFcmTokenCapabilities(Set.of(FcmCapabilities.CALLS));
        s.setPushSealSuites(suites);
        return s;
    }

    public static String render() throws Exception {
        ObjectNode root = M.createObjectNode();
        root.put("version", "1.0.0");
        root.put("generated_by", "FcmPushKeyVectorGenerator.java");
        root.put("source", "Messages (Java reference)");
        root.put("decision", "DR-063 draft 1 §3.3: push_key + push_seal in the signed FCM registration content, "
                + "push_key_id in the response, serverinfo.push_seal_suites");

        ObjectNode k = root.putObject("constants");
        k.put("endpoint", ChatServerEndpoints.REGISTER_FCM_TOKEN);
        k.put("message_type", CHAT_MESSAGE_TYPES.FCM_TOKEN_REGISTRATION.name());
        k.put("content_key", "fcm_token_registration_signed_content");
        k.put("push_key_key", "push_key");
        k.put("push_seal_key", "push_seal");
        k.put("push_key_id_key", "push_key_id");
        k.put("serverinfo_key", "push_seal_suites");
        k.put("suite", PushSeal.SUITE);
        k.put("note", "push_key: Base64URL with '.' padding, 32 bytes = 44 chars, checked by PushSeal.validatePushKey "
                + "(push_key_check: OK or the refusal reason; the server answers INVALID_PUSH_KEY). push_seal: the "
                + "exact ASCII suite id. Both are OMITTED from the signed content when null (NON_NULL on each field "
                + "only; device_id keeps its explicit null, cap its NON_EMPTY): an old client's canonical form is "
                + "byte-identical to today's (rows P0, P0n). A client sends them only to a server whose "
                + "serverinfo.push_seal_suites lists the suite. push_key_id = lower hex of "
                + "SHA3-256('tkm-push/v1/kid' || key)[0..8], computed by the server, echoed only when stored.");

        ObjectNode in = root.putObject("fixed_input");
        in.set("nonce", M.valueToTree(NONCE));
        in.put("fcm_token", TOKEN);
        in.put("platform", PLATFORM);

        ArrayNode rows = root.putArray("vectors");
        row(rows, "P0", "old client: no push fields, no cap (= FcmCanonicalVectorTest.VECTOR_WITH_DEVICE)", DEVICE_ID,
                null, null, null);
        row(rows, "P0n", "old client, device_id null (= FcmCanonicalVectorTest.VECTOR_NULL_DEVICE)", null, null, null,
                null);
        row(rows, "P1", "push_key + push_seal", DEVICE_ID, null, PUSH_KEY, PushSeal.SUITE);
        row(rows, "P2", "push_key + push_seal + cap [calls]", DEVICE_ID, List.of(FcmCapabilities.CALLS), PUSH_KEY,
                PushSeal.SUITE);
        row(rows, "P3", "push_key + push_seal with device_id null: device_id's null stays signed", null,
                List.of(FcmCapabilities.CALLS), PUSH_KEY, PushSeal.SUITE);
        row(rows, "P4", "a low-order push key (u = 0): signable, refused by the server (LOW_ORDER)", DEVICE_ID,
                List.of(FcmCapabilities.CALLS), b64(new byte[32]), PushSeal.SUITE);
        row(rows, "P5", "push_key alone (no push_seal): signable, refused by the server (both or neither)", DEVICE_ID,
                null, PUSH_KEY, null);

        ObjectNode resp = root.putObject("response_vectors");
        resp.set("stored", M.readTree(TkmTextUtils.getJacksonMapper().writeValueAsString(
                new FcmTokenRegistrationResponseBean(true, "FCM token registered successfully", null, 1700000000123L,
                        PushSeal.kidHex(pushKeyBytes())))));
        resp.set("not_stored", M.readTree(TkmTextUtils.getJacksonMapper().writeValueAsString(
                FcmTokenRegistrationResponseBean.success(1700000000123L))));
        resp.put("note", "push_key_id present only when the server stored the key; absent otherwise (never null).");

        ObjectNode si = root.putObject("serverinfo_vectors");
        si.set("with_suite", M.valueToTree(serverInfo(List.of(PushSeal.SUITE))));
        si.set("without_suite", M.valueToTree(serverInfo(null)));
        si.set("empty_suite_list", M.valueToTree(serverInfo(List.of())));
        si.put("note", "push_seal_suites is advertised only when FCM AND calls are enabled; absent (never [] or null) "
                + "otherwise. A client keeps it in three states per endpoint: unknown (not read), none, listed.");

        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentObjectsWith(new DefaultIndenter("  ", "\n"));
        pp.indentArraysWith(new DefaultIndenter("  ", "\n"));
        return M.writer(pp).writeValueAsString(root) + "\n";
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
