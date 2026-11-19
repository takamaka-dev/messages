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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.takamaka.messages.chat.core.NonceResponseBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.utils.CHAT_MESSAGE_TYPES;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.messages.utils.ChatUtils;
import io.takamaka.messages.utils.SimpleRequestHelper;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DR-074 amendment 1, A-14: the signing bytes of a registration WITHOUT {@code cap} are byte-identical to the bytes
 * every deployed client signs today, and the hand-built wire spellings of A-16 (V6, V11, V12, V13) meet the
 * verification path the way the amendment says. The verifier here is the server's: {@code verifySignedMessage}
 * re-derives the canonical form from the PARSED content.
 */
class FcmCapSigningBytesTest {

    /** Today's bean, field for field and annotation for annotation (Messages 1.14.0, before amendment 1). */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class TodaysFcmTokenRegistrationSignedContentBean {

        private NonceResponseBean nonce;
        @JsonProperty("fcm_token")
        private String fcmToken;
        private String platform;
        @JsonProperty("device_id")
        private String deviceId;
    }

    static final NonceResponseBean NONCE = FcmCapVectorGenerator.NONCE;
    static final ObjectMapper WIRE = TkmTextUtils.getJacksonMapper();
    static InstanceWalletKeystoreInterface iwk;

    @BeforeAll
    static void wallet() throws Exception {
        iwk = new InstanceWalletKeyStoreBCED25519("fcm_cap_signing_test_wallet", "superSecretPassword");
    }

    @Test
    void withoutCap_theSigningBytesAreTodays() throws Exception {
        for (String platform : new String[]{"android", "ios", "web"}) {
            for (String device : new String[]{"device-01", null}) {
                String today = SimpleRequestHelper.getCanonicalJson(
                        new TodaysFcmTokenRegistrationSignedContentBean(NONCE, "fixed-fcm-token-XYZ", platform, device));
                String fourArgs = SimpleRequestHelper.getCanonicalJson(
                        new FcmTokenRegistrationSignedContentBean(NONCE, "fixed-fcm-token-XYZ", platform, device));
                String capNull = SimpleRequestHelper.getCanonicalJson(
                        new FcmTokenRegistrationSignedContentBean(NONCE, "fixed-fcm-token-XYZ", platform, device, null));
                String capEmpty = SimpleRequestHelper.getCanonicalJson(
                        new FcmTokenRegistrationSignedContentBean(NONCE, "fixed-fcm-token-XYZ", platform, device,
                                List.of()));
                String label = platform + "/" + device;
                assertEquals(today, fourArgs, label);
                assertEquals(today, capNull, label);
                assertEquals(today, capEmpty, label);
                // the wire form (what the server re-serialises before canonicalising) is unchanged too
                assertEquals(WIRE.writeValueAsString(
                        new TodaysFcmTokenRegistrationSignedContentBean(NONCE, "fixed-fcm-token-XYZ", platform, device)),
                        WIRE.writeValueAsString(
                                new FcmTokenRegistrationSignedContentBean(NONCE, "fixed-fcm-token-XYZ", platform, device)),
                        label);
                // and device_id keeps its explicit null: NON_EMPTY is on the cap field only
                assertEquals(device == null, today.contains("\"device_id\":null"), label);
                assertFalse(capEmpty.contains("cap"), label);
            }
        }
        assertEquals(FcmCanonicalVectorTest.VECTOR_WITH_DEVICE, SimpleRequestHelper.getCanonicalJson(
                new TodaysFcmTokenRegistrationSignedContentBean(NONCE, "fixed-fcm-token-XYZ", "android", "device-01")));
        assertEquals(FcmCanonicalVectorTest.VECTOR_NULL_DEVICE, SimpleRequestHelper.getCanonicalJson(
                new TodaysFcmTokenRegistrationSignedContentBean(NONCE, "fixed-fcm-token-XYZ", "android", null)));
    }

    @Test
    void theSixArgumentSigner_signsTodaysBytes() throws Exception {
        FcmTokenRegistrationRequestBean req = ChatCryptoUtils.getSignedFcmTokenRegistrationRequest(
                NONCE, "fixed-fcm-token-XYZ", "android", null, iwk, 0);
        assertNull(req.getFcmTokenRegistrationSignedContentBean().getCap());
        assertEquals(FcmCanonicalVectorTest.VECTOR_NULL_DEVICE,
                SimpleRequestHelper.getCanonicalJson(req.getFcmTokenRegistrationSignedContentBean()));
    }

    /** A request signed by today's client (today's bean shape, today's bytes) verifies under the new bean. */
    @Test
    void todaysClient_stillVerifies() throws Exception {
        for (String device : new String[]{"device-01", null}) {
            ObjectNode content = WIRE.valueToTree(
                    new TodaysFcmTokenRegistrationSignedContentBean(NONCE, "fixed-fcm-token-XYZ", "ios", device));
            Object verified = ChatCryptoUtils.verifySignedMessage(envelope(content));
            FcmTokenRegistrationRequestBean bean = assertInstanceOf(FcmTokenRegistrationRequestBean.class, verified);
            assertNull(bean.getFcmTokenRegistrationSignedContentBean().getCap());
            assertNull(FcmCapabilities.validate(bean.getFcmTokenRegistrationSignedContentBean().getCap(),
                    Set.of(FcmCapabilities.CALLS)));
        }
    }

    /** V2 by hand: the same bytes as the signer's, verified, and the parsed list is the one signed. */
    @Test
    void v2_handBuilt_verifies() throws Exception {
        FcmTokenRegistrationRequestBean bean = verified(c -> c.putArray("cap").add("calls"));
        assertEquals(List.of("calls"), bean.getFcmTokenRegistrationSignedContentBean().getCap());
    }

    /** V6: {@code []} or {@code null} in the signed bytes: the server's form drops it, so the signature fails. */
    @Test
    void v6_emptyOrNullCapInTheSignedBytes_isASignatureError() throws Exception {
        assertSignatureRefused(c -> c.putArray("cap"));
        assertSignatureRefused(c -> c.putNull("cap"));
    }

    /**
     * V11, at the Messages layer (the RSocket decoder of rschat is T-4's): {@code [5]} is coerced to {@code ["5"]}
     * and re-serialised differently from what was signed; a bare string is not a list and fails to parse.
     */
    @Test
    void v11_aNumberOrAStringIsRefused() throws Exception {
        assertSignatureRefused(c -> c.putArray("cap").add(5));
        assertSignatureRefused(c -> c.put("cap", "calls"));
    }

    /**
     * V12 / Iris F4: a hand-built {@code [null]} SURVIVES the signature check (it re-serialises as {@code [null]}),
     * so the content check must refuse it with {@code INVALID_CAP}, without throwing.
     */
    @Test
    void v12_nullElement_survivesTheSignature_andIsRefusedByTheCheck() throws Exception {
        FcmTokenRegistrationRequestBean bean = verified(c -> c.putArray("cap").addNull());
        List<String> cap = bean.getFcmTokenRegistrationSignedContentBean().getCap();
        assertEquals(1, cap.size());
        assertNull(cap.get(0));
        assertEquals(FcmCapabilities.ERROR_INVALID_CAP, FcmCapabilities.validate(cap, Set.of(FcmCapabilities.CALLS)));
        assertEquals(FcmCapabilities.ERROR_INVALID_CAP, FcmCapabilities.validate(cap, null));
    }

    /** V13: {@code "calls\n"} verifies as signed; the content check refuses it. */
    @Test
    void v13_trailingLineFeed_verifiesAndIsRefusedByTheCheck() throws Exception {
        FcmTokenRegistrationRequestBean bean = verified(c -> c.putArray("cap").add("calls\n"));
        assertEquals(FcmCapabilities.ERROR_INVALID_CAP,
                FcmCapabilities.validate(bean.getFcmTokenRegistrationSignedContentBean().getCap(),
                        Set.of(FcmCapabilities.CALLS)));
    }

    /**
     * A-14 item 3: JCS keeps array order, so an unsorted list verifies AS SENT, and the sorted list is NOT what was
     * signed. Hence the server refuses (V3) instead of sorting: a sorted copy would be «verify one, store another».
     */
    @Test
    void v3_theSignatureCoversTheOrderSent() throws Exception {
        ObjectNode content = baseContent();
        content.putArray("cap").add("calls").add("a");
        String signedCanon = SimpleRequestHelper.getCanonicalJson(content);
        String sig = SimpleRequestHelper.signChatMessage(signedCanon, iwk, 0);
        FcmTokenRegistrationRequestBean bean = assertInstanceOf(FcmTokenRegistrationRequestBean.class,
                ChatCryptoUtils.verifySignedMessage(envelope(content, sig)));
        assertEquals(List.of("calls", "a"), bean.getFcmTokenRegistrationSignedContentBean().getCap());
        assertEquals(FcmCapabilities.ERROR_INVALID_CAP,
                FcmCapabilities.validate(bean.getFcmTokenRegistrationSignedContentBean().getCap(), null));
        ObjectNode sorted = content.deepCopy();
        ((ArrayNode) sorted.get("cap")).removeAll().add("a").add("calls");
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(envelope(sorted, sig)));
    }

    /** {@code cap} is signed: adding, removing or changing it after signing breaks the signature. */
    @Test
    void capIsCoveredByTheSignature() throws Exception {
        FcmTokenRegistrationRequestBean req = ChatCryptoUtils.getSignedFcmTokenRegistrationRequest(
                NONCE, "fixed-fcm-token-XYZ", "android", "device-01", List.of("calls"), iwk, 0);
        ObjectNode env = WIRE.valueToTree(req);
        ObjectNode content = (ObjectNode) env.get("fcm_token_registration_signed_content");
        content.remove("cap");
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(env.toString()));

        FcmTokenRegistrationRequestBean plain = ChatCryptoUtils.getSignedFcmTokenRegistrationRequest(
                NONCE, "fixed-fcm-token-XYZ", "android", "device-01", iwk, 0);
        ObjectNode env2 = WIRE.valueToTree(plain);
        ((ObjectNode) env2.get("fcm_token_registration_signed_content")).putArray("cap").add("calls");
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(env2.toString()));
    }

    /** The signer copies the list: a later change to the caller's list does not alter the signed content. */
    @Test
    void theSignerCopiesTheList() throws Exception {
        List<String> mine = new ArrayList<>(List.of("calls"));
        FcmTokenRegistrationRequestBean req = ChatCryptoUtils.getSignedFcmTokenRegistrationRequest(
                NONCE, "fixed-fcm-token-XYZ", "android", "device-01", mine, iwk, 0);
        mine.add("zzz");
        assertEquals(List.of("calls"), req.getFcmTokenRegistrationSignedContentBean().getCap());
        assertTrue(ChatCryptoUtils.verifySignedMessage(WIRE.writeValueAsString(req)) != null);
    }

    @Test
    void isCapRefused_isAHelperNotAWireMember() throws Exception {
        assertTrue(FcmTokenRegistrationResponseBean.error("INVALID_CAP", "x").isCapRefused());
        assertFalse(FcmTokenRegistrationResponseBean.error("REGISTRATION_ERROR", "x").isCapRefused());
        assertFalse(FcmTokenRegistrationResponseBean.error("SIGNATURE_ERROR", "x").isCapRefused());
        assertFalse(FcmTokenRegistrationResponseBean.error("INVALID_CAP", "x").isRetryLater());
        assertFalse(FcmTokenRegistrationResponseBean.success(1L).isCapRefused());
        String json = WIRE.writeValueAsString(FcmTokenRegistrationResponseBean.error("INVALID_CAP", "m"));
        assertFalse(json.contains("capRefused") || json.contains("cap_refused"), json);
        assertEquals(json, WIRE.writeValueAsString(WIRE.readValue(json, FcmTokenRegistrationResponseBean.class)));
    }

    // ---- helpers ----
    private static ObjectNode baseContent() {
        return WIRE.valueToTree(new FcmTokenRegistrationSignedContentBean(NONCE, "fixed-fcm-token-XYZ", "android",
                "device-01"));
    }

    private static String envelope(ObjectNode content) throws Exception {
        return envelope(content, SimpleRequestHelper.signChatMessage(SimpleRequestHelper.getCanonicalJson(content), iwk, 0));
    }

    /** A full FCM registration envelope around a hand-built content, signed over the content's JCS form. */
    private static String envelope(JsonNode content, String signature) throws Exception {
        ObjectNode env = WIRE.valueToTree(new FcmTokenRegistrationRequestBean(null,
                iwk.getPublicKeyAtIndexURL64(0), signature, CHAT_MESSAGE_TYPES.FCM_TOKEN_REGISTRATION.name(),
                iwk.getWalletCypher().name()));
        env.set("fcm_token_registration_signed_content", content);
        return env.toString();
    }

    private static FcmTokenRegistrationRequestBean verified(Consumer<ObjectNode> cap) throws Exception {
        ObjectNode content = baseContent();
        cap.accept(content);
        String wire = envelope(content);
        // the parser accepts it (the same mapper the server's verify path uses)
        ChatUtils.fromJsonToFcmTokenRegistrationRequestBean(wire);
        return assertInstanceOf(FcmTokenRegistrationRequestBean.class, ChatCryptoUtils.verifySignedMessage(wire));
    }

    private static void assertSignatureRefused(Consumer<ObjectNode> cap) throws Exception {
        ObjectNode content = baseContent();
        cap.accept(content);
        String wire = envelope(content);
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(wire), content.toString());
    }
}
