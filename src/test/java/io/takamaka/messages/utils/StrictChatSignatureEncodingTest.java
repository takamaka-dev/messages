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
package io.takamaka.messages.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.takamaka.messages.chat.message.BasicMessageEncryptedContentBean;
import io.takamaka.messages.chat.message.BasicMessageRequestBean;
import io.takamaka.messages.chat.message.BasicMessageSignedContentBean;
import io.takamaka.messages.chat.message.MessageAction;
import io.takamaka.messages.chat.message.MessageActionValidator;
import io.takamaka.messages.chat.message.MessageProtocolVersion;
import io.takamaka.messages.chat.message.ValidationContext;
import io.takamaka.messages.exception.InnerSignatureFailureException;
import io.takamaka.messages.exception.InvalidChatMessageSignatureException;
import io.takamaka.messages.exception.InvalidEmbeddedEnvelopeException;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.TkmCypherProviderBCED25519;
import io.takamaka.wallet.beans.TkmCypherBean;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.bouncycastle.util.encoders.UrlBase64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Strict spelling of the signer key and the signature on the CHAT
 * verification path (operator ruling 2026-11-11) — {@link ChatSignatureVerifier}
 * in front of the wallet-core provider at every Messages chat verify entry
 * point.
 *
 * <p>The vector {@code java_strict_signature_base64_vectors.json} is shared
 * byte-for-byte with wallet-core-flutter
 * ({@code test/strict_signature_base64_test.dart}); its {@code java_verdict}
 * is the chat verdict and Dart must give the same one for all 42 cases.
 */
public class StrictChatSignatureEncodingTest {

    private static final String CONVERSATION = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";
    private static final String SYMKEY = "strictEncodingTestSymmetricKey";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The three spellings Java used to accept and Dart refused. */
    private static final Set<String> FORMERLY_ACCEPTED = Set.of(
            "trailing_newline", "trailing_space", "leading_space", "embedded_space", "embedded_newline",
            "noncanonical_trailing_bits", "extra_trailing_bytes");

    private static JsonNode vectors;
    private static InstanceWalletKeystoreInterface wallet;
    private static SendContext ctx;

    @BeforeAll
    public static void setUp() throws Exception {
        try (InputStream in = StrictChatSignatureEncodingTest.class
                .getResourceAsStream("/java_strict_signature_base64_vectors.json")) {
            assertNotNull(in, "vector missing from test resources");
            vectors = MAPPER.readTree(in);
        }
        wallet = new InstanceWalletKeyStoreBCED25519("strict_encoding_test_wallet", "strictPwd");
        ctx = new SendContext(wallet, 0, CONVERSATION, SYMKEY);
    }

    // ---- the 42-case vector ---------------------------------------------

    @Test
    public void vector_chatVerdictIsTheRecordedOne_andOnlyTheCanonicalSpellingVerifies() {
        final String message = vectors.get("message").asText();
        int n = 0, verifies = 0;
        final Set<String> seen = new TreeSet<>();
        for (JsonNode c : vectors.get("cases")) {
            final String id = c.get("field").asText() + "/" + c.get("variant").asText();
            final TkmCypherBean r = ChatSignatureVerifier.verifyEd25519(
                    c.get("public_key").asText(), c.get("signature").asText(), message);
            assertNotNull(r, id);
            assertEquals(c.get("java_verdict").asText(), r.isValid() ? "verifies" : "refused", id);
            // the rule itself, independent of what the file says
            assertEquals("canonical".equals(c.get("variant").asText()), r.isValid(), id);
            n++;
            verifies += r.isValid() ? 1 : 0;
            seen.add(id);
        }
        assertEquals(42, n);
        assertEquals(42, seen.size());
        assertEquals(2, verifies, "public_key/canonical and signature/canonical only");
    }

    @Test
    public void vector_providerIsUntouched_transactionPathStaysLenient() {
        // wallet-core TkmCypherProviderBCED25519 (blockchain transaction verification) is NOT
        // tightened: it still accepts the three classes. This pins that the strict check lives
        // in Messages only, and that those cases have teeth (same bytes, different text).
        final String message = vectors.get("message").asText();
        final Set<String> providerOnly = new TreeSet<>();
        for (JsonNode c : vectors.get("cases")) {
            final String id = c.get("field").asText() + "/" + c.get("variant").asText();
            String provider;
            try {
                provider = TkmCypherProviderBCED25519.verify(
                        c.get("public_key").asText(), c.get("signature").asText(), message).isValid()
                        ? "verifies" : "refused";
            } catch (RuntimeException ex) {
                provider = "refused"; // the provider lets some decode failures escape
            }
            assertEquals(c.get("java_provider_verdict").asText(), provider, id);
            if ("verifies".equals(provider) && "refused".equals(c.get("java_verdict").asText())) {
                assertTrue(FORMERLY_ACCEPTED.contains(c.get("variant").asText()), id);
                providerOnly.add(id);
            }
            // the chat path never verifies what the provider refuses
            if ("refused".equals(provider)) {
                assertEquals("refused", c.get("java_verdict").asText(), id);
            }
        }
        // whitespace x5 on both fields, trailing bits on both, extra bytes on the key only
        assertEquals(13, providerOnly.size(), providerOnly.toString());
    }

    // ---- the decoder ------------------------------------------------------

    @Test
    public void decoder_roundTripsBouncyCastleForEveryLength() {
        for (int n = 0; n < 70; n++) {
            final byte[] bytes = new byte[n];
            for (int i = 0; i < n; i++) {
                bytes[i] = (byte) (i * 37 + 251);
            }
            final String text = new String(UrlBase64.encode(bytes));
            assertArrayEquals(bytes, ChatSignatureVerifier.decodeStrictOrNull(text), text);
            assertEquals(n == 32, ChatSignatureVerifier.isCanonicalEd25519PublicKey(text), text);
            assertEquals(n == 64, ChatSignatureVerifier.isCanonicalEd25519Signature(text), text);
        }
        assertArrayEquals(new byte[]{0x41, 0x42, 0x43}, ChatSignatureVerifier.decodeStrictOrNull("QUJD"));
        assertArrayEquals(new byte[]{0x41, 0x42}, ChatSignatureVerifier.decodeStrictOrNull("QUI."));
        assertArrayEquals(new byte[]{0x41}, ChatSignatureVerifier.decodeStrictOrNull("QQ.."));
        assertArrayEquals(new byte[0], ChatSignatureVerifier.decodeStrictOrNull(""));
    }

    @Test
    public void decoder_refusesEveryOtherSpelling() {
        // the same list as wallet-core-flutter `Base64UrlUtils.decodeStrict` + trailing bits
        for (String bad : new String[]{
            "QUI=", "QUI", "QQ", "QQ.", "QQ=.", "QQ.=", "Q...", "....", ".",
            "QUI..", "QUJD.", "QUJD....", "QQ..QUJD",
            "+/+/", "ab+c", "ab/c",
            "QUJD\n", " QUJD", "QU JD", "QUJD\r\n", "QUJD ", "\tQUJ",
            "QU*D", "QUéD", "QU\u0000D", "QUŁD",
            "QUJ.", "QR..", "QUL.", "Q_.."}) { // non-zero unused bits
            assertNull(ChatSignatureVerifier.decodeStrictOrNull(bad), MAPPER.valueToTree(bad).toString());
        }
        assertNull(ChatSignatureVerifier.decodeStrictOrNull(null));
    }

    @Test
    public void verifier_neverThrows_onNullOrGarbage() {
        final String key = vectors.get("public_key").asText();
        final String sig = vectors.get("signature").asText();
        final String msg = vectors.get("message").asText();
        for (String[] in : new String[][]{
            {null, sig, msg}, {key, null, msg}, {key, sig, null}, {null, null, null},
            {"", "", ""}, {"é", "é", msg}, {sig, key, msg}}) {
            final TkmCypherBean r = ChatSignatureVerifier.verifyEd25519(in[0], in[1], in[2]);
            assertNotNull(r, Arrays.toString(in));
            assertFalse(r.isValid(), Arrays.toString(in));
        }
        assertTrue(ChatSignatureVerifier.verifyEd25519(key, sig, msg).isValid());
        assertFalse(ChatSignatureVerifier.verifyEd25519(key, sig, msg + "x").isValid());
    }

    // ---- respellings of a real key / signature -----------------------------

    /** Same bytes, different text — what the lenient provider accepts. */
    private static Map<String, String> respell(String canonical, boolean isKey) {
        final Map<String, String> v = new LinkedHashMap<>();
        final String body = canonical.replace(".", "");
        final String pad = canonical.substring(body.length());
        final String alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        final char last = body.charAt(body.length() - 1);
        v.put("noncanonical_trailing_bits",
                body.substring(0, body.length() - 1) + alpha.charAt(alpha.indexOf(last) + 1) + pad);
        v.put("trailing_newline", canonical + "\n");
        v.put("trailing_space", canonical + " ");
        v.put("leading_space", " " + canonical);
        v.put("embedded_space", canonical.substring(0, 8) + " " + canonical.substring(8));
        v.put("embedded_newline", canonical.substring(0, 8) + "\n" + canonical.substring(8));
        if (isKey) {
            final byte[] raw = UrlBase64.decode(canonical);
            v.put("extra_trailing_bytes", new String(UrlBase64.encode(Arrays.copyOf(raw, raw.length + 4))));
        }
        return v;
    }

    private static String withField(String json, String field, String value) throws Exception {
        final ObjectNode node = (ObjectNode) MAPPER.readTree(json);
        assertTrue(node.has(field), field);
        node.put(field, value);
        return MAPPER.writeValueAsString(node);
    }

    private static String toJson(Object bean) throws Exception {
        return TkmTextUtils.getJacksonMapper().writeValueAsString(bean);
    }

    /** Real signed envelopes of several types, all funnelled through verifySignedMessage. */
    private static Map<String, String> signedEnvelopes() throws Exception {
        final Map<String, String> m = new LinkedHashMap<>();
        m.put("SIGNED_TIMESTAMP", toJson(ChatCryptoUtils.getSignedTimestampRequest(1_700_000_000_000L, wallet, 0)));
        m.put("GET_STORAGE_QUOTA", toJson(ChatCryptoUtils.getSignedGetStorageQuotaRequest(1_700_000_000_000L, wallet, 0)));
        m.put("GET_USER_OPTIONS", toJson(ChatCryptoUtils.getSignedGetUserOptionsRequest(null, 1_700_000_000_000L, wallet, 0)));
        m.put("GET_USER_PROFILE", toJson(ChatCryptoUtils.getSignedGetUserProfileRequest(1_700_000_000_000L, wallet, 0)));
        m.put("TOPIC_MESSAGE", toJson(ChatCryptoUtils.getPlainMessageBean(ctx, "strict", null, null)));
        return m;
    }

    @Test
    public void verifySignedMessage_refusesARespelledKeyOrSignature_inTheEnvelope() throws Exception {
        for (Map.Entry<String, String> e : signedEnvelopes().entrySet()) {
            final String json = e.getValue();
            final JsonNode node = MAPPER.readTree(json);
            final String from = node.get("from").asText();
            final String sig = node.get("signature").asText();
            assertNotNull(ChatCryptoUtils.verifySignedMessage(json), e.getKey());
            assertNotNull(ChatCryptoUtils.verifySignedMessage(json, from), e.getKey());

            for (Map.Entry<String, String> k : respell(from, true).entrySet()) {
                final String id = e.getKey() + " from/" + k.getKey();
                assertNotEquals(from, k.getValue(), id);
                final String respelled = withField(json, "from", k.getValue());
                assertThrows(InvalidChatMessageSignatureException.class,
                        () -> ChatCryptoUtils.verifySignedMessage(respelled), id);
                // key handed in by the caller (the server's "expected signer" form)
                assertThrows(InvalidChatMessageSignatureException.class,
                        () -> ChatCryptoUtils.verifySignedMessage(json, k.getValue()), id + " (from[] argument)");
            }
            for (Map.Entry<String, String> s : respell(sig, false).entrySet()) {
                final String id = e.getKey() + " signature/" + s.getKey();
                final String respelled = withField(json, "signature", s.getValue());
                assertThrows(InvalidChatMessageSignatureException.class,
                        () -> ChatCryptoUtils.verifySignedMessage(respelled), id);
            }
        }
    }

    @Test
    public void respellingsHaveTeeth_theLenientProviderStillVerifiesThem() throws Exception {
        // Without the strict door every one of these verified: the key/signature sit OUTSIDE the
        // signed content, so respelling them does not change the signed bytes.
        final BasicMessageRequestBean env = ChatCryptoUtils.getPlainMessageBean(ctx, "teeth", null, null);
        final String canonical = SimpleRequestHelper.getCanonicalJson(env.getBasicMessageSignedContentBean());
        for (Map.Entry<String, String> k : respell(env.getFrom(), true).entrySet()) {
            assertTrue(TkmCypherProviderBCED25519.verify(k.getValue(), env.getSignature(), canonical).isValid(), k.getKey());
            assertFalse(ChatSignatureVerifier.verifyEd25519(k.getValue(), env.getSignature(), canonical).isValid(), k.getKey());
        }
        for (Map.Entry<String, String> s : respell(env.getSignature(), false).entrySet()) {
            assertTrue(TkmCypherProviderBCED25519.verify(env.getFrom(), s.getValue(), canonical).isValid(), s.getKey());
            assertFalse(ChatSignatureVerifier.verifyEd25519(env.getFrom(), s.getValue(), canonical).isValid(), s.getKey());
        }
    }

    // ---- embedded envelopes (share_history) --------------------------------

    private static BasicMessageRequestBean copy(BasicMessageRequestBean o, String from, String signature) {
        return new BasicMessageRequestBean(o.getBasicMessageSignedContentBean(), from, signature,
                o.getMessageType(), o.getSignatureType());
    }

    @Test
    public void shareHistoryProducer_refusesARespelledEmbeddedEnvelope() throws Exception {
        final BasicMessageRequestBean original = ChatCryptoUtils.getPlainMessageBean(ctx, "original", null, null);
        assertNotNull(ChatCryptoUtils.getShareHistoryMessageBean(ctx, original, "relaying", false));
        final List<BasicMessageRequestBean> respelled = new ArrayList<>();
        respell(original.getFrom(), true).values().forEach(k -> respelled.add(copy(original, k, original.getSignature())));
        respell(original.getSignature(), false).values().forEach(s -> respelled.add(copy(original, original.getFrom(), s)));
        assertEquals(13, respelled.size());
        for (BasicMessageRequestBean bad : respelled) {
            assertThrows(InvalidEmbeddedEnvelopeException.class,
                    () -> ChatCryptoUtils.getShareHistoryMessageBean(ctx, bad, "relaying", false),
                    MAPPER.valueToTree(bad.getFrom() + " | " + bad.getSignature()).toString());
        }
    }

    @Test
    public void shareHistoryValidator_refusesARespelledEmbeddedEnvelope() throws Exception {
        final BasicMessageRequestBean original = ChatCryptoUtils.getBasicMessageBean(
                wallet, 0, CONVERSATION, SYMKEY, List.of(),
                BasicMessageEncryptedContentBean.builder().textMessage("original content")
                        .clientProtocolVersion(MessageProtocolVersion.CURRENT).build());
        final BasicMessageRequestBean outer = new BasicMessageRequestBean(
                new BasicMessageSignedContentBean(CONVERSATION, null, null),
                wallet.getPublicKeyAtIndexURL64(0), "outer-sig", "TOPIC_MESSAGE", "Ed25519BC");
        // canonical: no inner-signature failure
        MessageActionValidator.validate(shareHistoryOf(original), outer, ValidationContext.empty());

        final List<BasicMessageRequestBean> respelled = new ArrayList<>();
        respell(original.getFrom(), true).values().forEach(k -> respelled.add(copy(original, k, original.getSignature())));
        respell(original.getSignature(), false).values().forEach(s -> respelled.add(copy(original, original.getFrom(), s)));
        for (BasicMessageRequestBean bad : respelled) {
            assertThrows(InnerSignatureFailureException.class,
                    () -> MessageActionValidator.validate(shareHistoryOf(bad), outer, ValidationContext.empty()),
                    MAPPER.valueToTree(bad.getFrom() + " | " + bad.getSignature()).toString());
        }
    }

    private static BasicMessageEncryptedContentBean shareHistoryOf(BasicMessageRequestBean embedded) {
        return BasicMessageEncryptedContentBean.builder()
                .textMessage("relayed").action(MessageAction.SHARE_HISTORY).originalMessage(embedded)
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
    }

    // ---- guard: no chat verify goes around the door ------------------------

    @Test
    public void guard_noMainCodeCallsTheProviderVerifyDirectly_exceptTheDoorAndTheLegacyBaseBeanPath() throws Exception {
        // SimpleRequestHelper keeps two direct calls: the pre-chat BaseBean pay/stake-request
        // protocol (verifyMessageSignature / verifyEncryptedMessageSignature). It is not chat,
        // was not part of the 2026-11-11 scan, and stays lenient until ruled on.
        final Set<String> callers = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Paths.get("src", "main", "java"))) {
            for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                if (Files.readString(f).contains("TkmCypherProviderBCED25519.verify(")) {
                    callers.add(f.getFileName().toString());
                }
            }
        }
        assertEquals(new TreeSet<>(Set.of("ChatSignatureVerifier.java", "SimpleRequestHelper.java")), callers,
                "a chat verify must go through ChatSignatureVerifier.verifyEd25519");
    }

    // ---- existing fixtures: canonical real signatures still verify ---------

    @Test
    public void existingFixtures_canonicalRealSignaturesStillVerify_andRespelledKeysDoNot() throws Exception {
        final Path root = Paths.get("src", "test", "resources", "cross-platform-vectors", "fixtures");
        int verified = 0;
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path dir : (Iterable<Path>) dirs.sorted()::iterator) {
                final Path wire = dir.resolve("wire_format.json");
                // V* = fixtures whose OUTER envelope is valid by construction
                if (!dir.getFileName().toString().startsWith("V") || !Files.exists(wire)) {
                    continue;
                }
                final String json = Files.readString(wire);
                final JsonNode node = MAPPER.readTree(json);
                final String from = node.get("from").asText();
                final String sig = node.get("signature").asText();
                final String id = dir.getFileName().toString();
                assertTrue(ChatSignatureVerifier.isCanonicalEd25519PublicKey(from), id);
                assertTrue(ChatSignatureVerifier.isCanonicalEd25519Signature(sig), id);
                assertNotNull(ChatCryptoUtils.verifySignedMessage(json), id);
                final String badKey = withField(json, "from", respell(from, true).get("noncanonical_trailing_bits"));
                assertThrows(InvalidChatMessageSignatureException.class,
                        () -> ChatCryptoUtils.verifySignedMessage(badKey), id);
                final String badSig = withField(json, "signature", respell(sig, false).get("noncanonical_trailing_bits"));
                assertThrows(InvalidChatMessageSignatureException.class,
                        () -> ChatCryptoUtils.verifySignedMessage(badSig), id);
                verified++;
            }
        }
        assertTrue(verified >= 10, "fixtures verified: " + verified);
    }
}
