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
package io.takamaka.messages.testtools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.takamaka.messages.chat.attachment.ChatMediaPlaceholderBean;
import io.takamaka.messages.chat.message.BasicMessageEncryptedContentBean;
import io.takamaka.messages.chat.message.BasicMessageRequestBean;
import io.takamaka.messages.chat.message.MessageAction;
import io.takamaka.messages.chat.message.MessageProtocolVersion;
import io.takamaka.messages.utils.CHAT_MESSAGE_TYPES;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * Manual-run utility (NOT a CI test — the class name does not match the
 * Surefire {@code *Test} pattern, so {@code mvn test} skips it). Regenerates
 * the cross-platform snapshot fixtures described in Phase 1 plan §3.5.
 *
 * <p>Run with:</p>
 * <pre>mvn -o test -Dtest=FixtureGenerator#regenerateAll</pre>
 *
 * <p>Re-running is a deliberate human act: it overwrites the committed
 * snapshots. The wire-format JSON it produces carries fresh random IV/salt
 * per run (no seeded RNG — see §2.11), so byte-equality across runs is
 * neither expected nor required. The cross-platform contract is
 * agreement on <em>validation outcome</em> for the frozen corpus, verified by
 * {@code CrossPlatformVectorTest} (Java) and the Dart mirror.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class FixtureGenerator {

    static final String CONVERSATION = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";
    static final String OTHER_CONVERSATION = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
    static final String SYMKEY = "crossPlatformFixtureSymmetricKey_v1";
    static final String OTHER_SYMKEY = "crossPlatformFixtureOtherSymmetricKey_v1";
    static final Path ROOT = Path.of("src", "test", "resources", "cross-platform-vectors", "fixtures");

    private final ObjectMapper mapper = TkmTextUtils.getJacksonMapper();
    private InstanceWalletKeystoreInterface wallet;
    private String senderPk;
    private String parentSignature;

    @Test
    public void regenerateAll() throws Exception {
        wallet = new InstanceWalletKeyStoreBCED25519("cross_platform_fixture_wallet", "fixturePwd");
        senderPk = wallet.getPublicKeyAtIndexURL64(0);

        // A real parent message; its signature is a valid signature-format target.
        BasicMessageRequestBean parent = sign(CONVERSATION, SYMKEY,
                builder().textMessage("parent").clientProtocolVersion(MessageProtocolVersion.CURRENT).build());
        parentSignature = parent.getSignature();

        Files.createDirectories(ROOT);

        generateValid();
        generateInvalid();

        log.info("Fixtures regenerated under {}", ROOT.toAbsolutePath());
    }

    // ====================================================================
    // VALID fixtures (V01-V19)
    // ====================================================================

    private void generateValid() throws Exception {
        // V01 — legacy plain (no version, no features)
        writeValid("V01_plain_legacy", "Plain v1.0 legacy (no version field, no v1.1+ fields)",
                builder().textMessage("hello legacy").build(),
                ctxHints(false, null, null));

        // V02 — plain v1.1
        writeValid("V02_plain_v1_1", "Plain v1.1 (explicit version, no features)",
                builder().textMessage("hello v1.1").clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(false, null, null));

        // V03 — reply text only
        writeValid("V03_reply_text_only", "Reply (text only)",
                builder().textMessage("a reply").action(MessageAction.REPLY).targets(List.of(parentSignature)).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(true, senderPk, null));

        // V04 — reply + inline image (both Base64 encodings)
        writeValid("V04_reply_inline_image", "Reply (text + inline image — exercises both Base64 encodings)",
                builder().textMessage("look").action(MessageAction.REPLY).targets(List.of(parentSignature))
                        .attachedMedia(List.of(inlineImage("image/png", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="))).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(true, senderPk, null));

        // V05 — reaction (emoji as small png)
        writeValid("V05_reaction_emoji", "Reaction (emoji)",
                reaction("image/png", "aGVsbG8="),
                ctxHints(true, senderPk, null));

        // V06 — reaction (sticker as webp)
        writeValid("V06_reaction_sticker", "Reaction (sticker)",
                reaction("image/webp", "c3RpY2tlcg=="),
                ctxHints(true, senderPk, null));

        // V07 — reaction (inline image — both encodings)
        writeValid("V07_reaction_inline_image", "Reaction (inline image — exercises both Base64 encodings)",
                reaction("image/png", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="),
                ctxHints(true, senderPk, null));

        // V08 — reaction_remove
        writeValid("V08_reaction_remove", "Reaction_remove",
                builder().action(MessageAction.REACTION_REMOVE).targets(List.of(parentSignature)).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(true, senderPk, null));

        // V09 — edit (new text, same media)
        writeValid("V09_edit_text", "Edit (new text, same media)",
                builder().textMessage("edited text").action(MessageAction.EDIT).targets(List.of(parentSignature)).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(true, senderPk, null));

        // V10 — edit (new text, new media)
        writeValid("V10_edit_text_media", "Edit (new text, new media)",
                builder().textMessage("edited").action(MessageAction.EDIT).targets(List.of(parentSignature))
                        .attachedMedia(List.of(inlineImage("image/png", "aGVsbG8="))).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(true, senderPk, null));

        // V11 — redact (with reason)
        writeValid("V11_redact_reason", "Redact (with reason)",
                builder().textMessage("violates policy").action(MessageAction.REDACT).targets(List.of(parentSignature)).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(true, senderPk, null));

        // V12 — redact (no reason)
        writeValid("V12_redact_no_reason", "Redact (no reason)",
                builder().action(MessageAction.REDACT).targets(List.of(parentSignature)).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(true, senderPk, null));

        // V13 — pin (with reason) — sender is the conversation creator
        writeValid("V13_pin_reason", "Pin (with reason)",
                builder().textMessage("pinned note").action(MessageAction.PIN).targets(List.of(parentSignature)).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(true, senderPk, senderPk));

        // V14 — unpin — sender is the conversation creator
        writeValid("V14_unpin", "Unpin",
                builder().action(MessageAction.UNPIN).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(false, null, senderPk));

        // V15 — forward depth 1, attributed
        writeValid("V15_forward_attributed", "Forward, depth 1, attributed",
                builder().textMessage("fyi").action(MessageAction.FORWARD).targets(List.of(senderPk))
                        .fwContent(builder().textMessage("forwarded body").build()).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(false, null, null));

        // V16 — forward depth 1, anonymous (targets=[])
        writeValid("V16_forward_anonymous", "Forward, depth 1, anonymous (targets=[])",
                builder().textMessage("fyi").action(MessageAction.FORWARD).targets(List.of())
                        .fwContent(builder().textMessage("forwarded body").build()).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(false, null, null));

        // V17 — forward depth 3
        writeValid("V17_forward_depth_3", "Forward, depth 3 (recursive fw_content)",
                builder().textMessage("fyi").action(MessageAction.FORWARD).targets(List.of())
                        .fwContent(forwardChain(3)).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(false, null, null));

        // V18 — share_history first-share (re_shared absent)
        BasicMessageRequestBean originalForV18 = sign(CONVERSATION, SYMKEY,
                builder().textMessage("the original message").clientProtocolVersion(MessageProtocolVersion.CURRENT).build());
        writeValid("V18_share_history_first", "Share_history, first-share (re_shared absent)",
                builder().textMessage("sharing history").action(MessageAction.SHARE_HISTORY)
                        .originalMessage(originalForV18).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(false, null, null));

        // V19 — share_history re-share (re_shared=true)
        BasicMessageRequestBean originalForV19 = sign(CONVERSATION, SYMKEY,
                builder().textMessage("the original message 2").clientProtocolVersion(MessageProtocolVersion.CURRENT).build());
        writeValid("V19_share_history_reshare", "Share_history, re-share (re_shared=true)",
                builder().textMessage("re-sharing").action(MessageAction.SHARE_HISTORY)
                        .originalMessage(originalForV19).reShared(Boolean.TRUE).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(false, null, null));
    }

    // ====================================================================
    // INVALID fixtures (I01-I18)
    // ====================================================================

    private void generateInvalid() throws Exception {
        // I01 — version 1.0.0 (patch level) -> H1
        writeThrows("I01_version_patch_level", "client_protocol_version 1.0.0 (patch level)",
                builder().textMessage("x").clientProtocolVersion("1.0.0").build(),
                "MalformedProtocolVersionException", "INVALID_PROTOCOL_VERSION_MALFORMED",
                ctxHints(false, null, null));

        // I02 — version v1.0 (prefix) -> H1
        writeThrows("I02_version_prefix", "client_protocol_version v1.0 (prefix)",
                builder().textMessage("x").clientProtocolVersion("v1.0").build(),
                "MalformedProtocolVersionException", "INVALID_PROTOCOL_VERSION_MALFORMED",
                ctxHints(false, null, null));

        // I03 — version 2.0 (incompatible MAJOR) -> H2
        writeThrows("I03_incompatible_major", "client_protocol_version 2.0 (incompatible MAJOR)",
                builder().textMessage("x").clientProtocolVersion("2.0").build(),
                "IncompatibleMajorVersionException", "INVALID_PROTOCOL_VERSION_INCOMPATIBLE_MAJOR",
                ctxHints(false, null, null));

        // I04 — no version + populated action -> H3
        writeThrows("I04_missing_version_with_features", "No version + populated action field",
                builder().action(MessageAction.REPLY).targets(List.of(parentSignature)).build(),
                "MissingVersionWithFeaturesException", "INVALID_PROTOCOL_VERSION_MISSING_WITH_FEATURES",
                ctxHints(true, senderPk, null));

        // I05 — version 1.0 + populated action -> H4
        writeThrows("I05_legacy_version_with_features", "client_protocol_version 1.0 + populated action field",
                builder().action(MessageAction.REPLY).targets(List.of(parentSignature)).clientProtocolVersion("1.0").build(),
                "LegacyVersionWithFeaturesException", "INVALID_PROTOCOL_VERSION_LEGACY_WITH_FEATURES",
                ctxHints(true, senderPk, null));

        // I06 — unknown action -> H5
        writeThrows("I06_unknown_action", "action __test_unknown_action__",
                builder().action("__test_unknown_action__").clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                "UnknownActionException", "INVALID_ACTION",
                ctxHints(false, null, null));

        // I07 — share_history with corrupted inner signature -> H6
        BasicMessageRequestBean validOriginal = sign(CONVERSATION, SYMKEY,
                builder().textMessage("genuine original").clientProtocolVersion(MessageProtocolVersion.CURRENT).build());
        BasicMessageRequestBean corruptedOriginal = new BasicMessageRequestBean(
                validOriginal.getBasicMessageSignedContentBean(),
                validOriginal.getFrom(),
                flipFirstChar(validOriginal.getSignature()),
                validOriginal.getMessageType(),
                validOriginal.getSignatureType());
        writeThrows("I07_share_history_bad_inner_signature", "Share_history with corrupted inner signature",
                builder().textMessage("relayed").action(MessageAction.SHARE_HISTORY)
                        .originalMessage(corruptedOriginal).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                "InnerSignatureFailureException", "INNER_SIGNATURE_FAILURE",
                ctxHints(false, null, null));

        // I08 — reply with targets.size==2 -> WARN
        writeResult("I08_reply_cardinality", "Reply with two targets",
                builder().textMessage("r").action(MessageAction.REPLY)
                        .targets(List.of(parentSignature, parentSignature)).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(true, senderPk, null),
                true, dec("INVALID_REPLY_MALFORMED_TARGETS", "WARN"));

        // I09 — reply with bad signature-format target -> WARN
        writeResult("I09_reply_bad_signature_format", "Reply target not matching signature regex",
                builder().textMessage("r").action(MessageAction.REPLY)
                        .targets(List.of("not-a-signature")).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(true, senderPk, null),
                true, dec("INVALID_REPLY_BAD_SIGNATURE_FORMAT", "WARN"));

        // I10 — forward with bad public-key-format target -> WARN
        writeResult("I10_forward_bad_public_key_format", "Forward target not matching public-key regex",
                builder().textMessage("f").action(MessageAction.FORWARD)
                        .targets(List.of("not-a-public-key"))
                        .fwContent(builder().textMessage("body").build()).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(false, null, null),
                true, dec("INVALID_FORWARD_BAD_PUBLIC_KEY_FORMAT", "WARN"));

        // I11 — reply targeting a signature not in local cache -> INFO
        writeResult("I11_reply_broken_reference", "Reply targeting a signature not in local cache",
                builder().textMessage("r").action(MessageAction.REPLY)
                        .targets(List.of(parentSignature)).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(false, null, null), // targetInCache=false
                true, dec("BROKEN_REFERENCE_REPLY", "INFO"));

        // I12 — edit signed by non-author -> WARN
        writeResult("I12_edit_unauthorized", "Edit signed by non-author",
                builder().textMessage("hacked edit").action(MessageAction.EDIT)
                        .targets(List.of(parentSignature)).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(true, "SomeOtherAuthorPublicKey.", null), // author != sender
                true, dec("UNAUTHORIZED_EDIT", "WARN"));

        // I13 — pin signed by non-creator -> WARN
        writeResult("I13_pin_unauthorized", "Pin signed by non-creator",
                builder().textMessage("pin").action(MessageAction.PIN)
                        .targets(List.of(parentSignature)).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(true, senderPk, "SomeCreatorPublicKey."), // creator != sender
                true, dec("UNAUTHORIZED_PIN", "WARN"));

        // I14 — share_history with cross-conversation inner hash -> ERROR
        BasicMessageRequestBean foreignOriginal = sign(OTHER_CONVERSATION, OTHER_SYMKEY,
                builder().textMessage("foreign original").clientProtocolVersion(MessageProtocolVersion.CURRENT).build());
        writeResult("I14_share_history_cross_conversation", "Share_history with cross-conversation inner hash",
                builder().textMessage("relayed").action(MessageAction.SHARE_HISTORY)
                        .originalMessage(foreignOriginal).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(false, null, null),
                false, dec("INVALID_SHARE_HISTORY_CROSS_CONVERSATION", "ERROR"));

        // I15 — nested share_history -> WARN
        BasicMessageRequestBean nestedOriginal = sign(CONVERSATION, SYMKEY,
                builder().textMessage("inner share").action(MessageAction.SHARE_HISTORY)
                        .originalMessage(sign(CONVERSATION, SYMKEY, builder().textMessage("deep").clientProtocolVersion(MessageProtocolVersion.CURRENT).build()))
                        .clientProtocolVersion(MessageProtocolVersion.CURRENT).build());
        Map<String, Object> nestedHints = ctxHints(false, null, null);
        nestedHints.put("embeddedIsShareHistory", true);
        writeResult("I15_share_history_nested", "Nested share_history",
                builder().textMessage("relayed").action(MessageAction.SHARE_HISTORY)
                        .originalMessage(nestedOriginal).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                nestedHints,
                true, dec("INVALID_SHARE_HISTORY_NESTED", "WARN"));

        // I16 — forward depth 11 (over cap) -> INFO + truncated
        writeResult("I16_forward_depth_exceeded", "Forward, depth 11 (over cap)",
                builder().textMessage("f").action(MessageAction.FORWARD).targets(List.of())
                        .fwContent(forwardChain(11)).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(false, null, null),
                true, dec("FORWARD_DEPTH_EXCEEDED", "INFO"));

        // I17 — reaction with image/tiff MIME -> WARN
        writeResult("I17_reaction_mime_violation", "Reaction with image/tiff MIME",
                builder().action(MessageAction.REACTION).targets(List.of(parentSignature))
                        .attachedMedia(List.of(inlineRaw("image/tiff", "dGlmZg=="))).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(true, senderPk, null),
                true, dec("INLINE_MIME_VIOLATION", "WARN"));

        // I18 — inline content with wrong unencrypted_content_hash -> ERROR
        ChatMediaPlaceholderBean badHash = ChatMediaPlaceholderBean.builder()
                .mediaType("image/png").isTheObject(true).preview("aGVsbG8=")
                .unencryptedContentHash("definitely-the-wrong-hash").build();
        writeResult("I18_inline_hash_mismatch", "Inline content with wrong unencrypted_content_hash",
                builder().action(MessageAction.REACTION).targets(List.of(parentSignature))
                        .attachedMedia(List.of(badHash)).clientProtocolVersion(MessageProtocolVersion.CURRENT).build(),
                ctxHints(true, senderPk, null),
                false, dec("INLINE_HASH_MISMATCH", "ERROR"));
    }

    // ====================================================================
    // helpers
    // ====================================================================

    private BasicMessageEncryptedContentBean.BasicMessageEncryptedContentBeanBuilder builder() {
        return BasicMessageEncryptedContentBean.builder();
    }

    private BasicMessageRequestBean sign(String conversation, String symkey,
            BasicMessageEncryptedContentBean inner) throws Exception {
        return ChatCryptoUtils.getBasicMessageBean(wallet, 0, conversation, symkey, null, inner);
    }

    private ChatMediaPlaceholderBean inlineImage(String mime, String previewB64) throws Exception {
        return ChatMediaPlaceholderBean.builder()
                .mediaType(mime).isTheObject(true).preview(previewB64)
                .unencryptedContentHash(TkmSignUtils.Hash256B64URL(previewB64))
                .build();
    }

    private ChatMediaPlaceholderBean inlineRaw(String mime, String previewB64) {
        return ChatMediaPlaceholderBean.builder()
                .mediaType(mime).isTheObject(true).preview(previewB64).build();
    }

    private BasicMessageEncryptedContentBean reaction(String mime, String previewB64) throws Exception {
        return builder().action(MessageAction.REACTION).targets(List.of(parentSignature))
                .attachedMedia(List.of(inlineImage(mime, previewB64))).clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
    }

    private BasicMessageEncryptedContentBean forwardChain(int links) {
        BasicMessageEncryptedContentBean node = builder().textMessage("leaf").build();
        for (int i = 0; i < links - 1; i++) {
            node = builder().textMessage("n" + i).fwContent(node).build();
        }
        return node;
    }

    private Map<String, Object> ctxHints(boolean targetInCache, String targetAuthor, String conversationCreatorPk) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symmetricKey", SYMKEY);
        m.put("conversationHashName", CONVERSATION);
        m.put("conversationCreatorPk", conversationCreatorPk);
        m.put("targetInCache", targetInCache);
        m.put("targetAuthor", targetAuthor);
        m.put("embeddedIsShareHistory", false);
        return m;
    }

    private Map<String, Object> dec(String code, String severity) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("severity", severity);
        return m;
    }

    private void writeValid(String id, String description,
            BasicMessageEncryptedContentBean inner, Map<String, Object> hints) throws Exception {
        writeResult(id, description, inner, hints, true /* overallValid */);
    }

    @SafeVarargs
    private void writeResult(String id, String description,
            BasicMessageEncryptedContentBean inner, Map<String, Object> hints,
            boolean overallValid, Map<String, Object>... decorations) throws Exception {
        BasicMessageRequestBean wire = sign(CONVERSATION, SYMKEY, inner);
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("type", "result");
        expected.put("overallValid", overallValid);
        expected.put("decorations", List.of(decorations));
        write(id, description, hints, wire, inner, expected);
    }

    private void writeThrows(String id, String description,
            BasicMessageEncryptedContentBean inner, String exceptionSimpleName, String code,
            Map<String, Object> hints) throws Exception {
        BasicMessageRequestBean wire = sign(CONVERSATION, SYMKEY, inner);
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("type", "throws");
        expected.put("exception", exceptionSimpleName);
        expected.put("code", code);
        write(id, description, hints, wire, inner, expected);
    }

    private void write(String id, String description, Map<String, Object> hints,
            BasicMessageRequestBean wire, BasicMessageEncryptedContentBean inner,
            Map<String, Object> expected) throws IOException {
        Path dir = ROOT.resolve(id);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("description.md"), "# " + id + "\n\n" + description + "\n");
        Files.writeString(dir.resolve("input.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(hints));
        Files.writeString(dir.resolve("wire_format.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wire));
        Files.writeString(dir.resolve("expected_inner_plaintext.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(inner));
        Files.writeString(dir.resolve("expected_validation.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(expected));
        log.info("wrote fixture {}", id);
    }

    private static String flipFirstChar(String s) {
        char[] c = s.toCharArray();
        c[0] = (c[0] == 'A') ? 'B' : 'A';
        return new String(c);
    }
}
