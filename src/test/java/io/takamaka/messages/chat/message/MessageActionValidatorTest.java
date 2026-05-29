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
package io.takamaka.messages.chat.message;

import io.takamaka.messages.exception.IncompatibleMajorVersionException;
import io.takamaka.messages.exception.InnerSignatureFailureException;
import io.takamaka.messages.exception.LegacyVersionWithFeaturesException;
import io.takamaka.messages.exception.MalformedProtocolVersionException;
import io.takamaka.messages.exception.MissingVersionWithFeaturesException;
import io.takamaka.messages.exception.UnknownActionException;
import io.takamaka.messages.chat.attachment.ChatMediaPlaceholderBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.exception.MessageException;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.TkmSignUtils;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MessageActionValidator} — the hard/soft split of
 * Phase 1 §3.3.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class MessageActionValidatorTest {

    private static final String CONVERSATION = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";
    private static final String OTHER_CONVERSATION = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
    private static final String SYMKEY = "testSymmetricKeyForValidatorUnitTests";

    private static InstanceWalletKeystoreInterface wallet;
    private static String senderPk;
    private static String validSignature;     // a real Ed25519 signature, matches signature regex
    private static String validPublicKey;      // sender PK, matches public-key regex
    private static BasicMessageRequestBean signedOriginal; // valid signed envelope for share_history

    @BeforeAll
    public static void setUp() throws WalletException, ChatMessageException, MessageException {
        wallet = new InstanceWalletKeyStoreBCED25519("validator_test_wallet", "validatorPwd");
        senderPk = wallet.getPublicKeyAtIndexURL64(0);
        validPublicKey = senderPk;

        signedOriginal = ChatCryptoUtils.getBasicMessageBean(
                wallet, 0, CONVERSATION, SYMKEY, List.of(),
                BasicMessageEncryptedContentBean.builder()
                        .textMessage("original content")
                        .clientProtocolVersion(MessageProtocolVersion.CURRENT)
                        .build());
        validSignature = signedOriginal.getSignature();
    }

    private static BasicMessageRequestBean outer(String fromPk) {
        return new BasicMessageRequestBean(
                new BasicMessageSignedContentBean(CONVERSATION, null, null),
                fromPk, "outer-sig", "TOPIC_MESSAGE", "Ed25519BC");
    }

    // ---- formats are what we assume -----------------------------------

    @Test
    public void realSignatureAndKeyMatchRegexes() {
        assertTrue(MessageActionValidator.SIGNATURE_REGEX.matcher(validSignature).matches(),
                "real Ed25519 signature must match the signature target regex: " + validSignature);
        assertTrue(MessageActionValidator.PUBLIC_KEY_REGEX.matcher(validPublicKey).matches(),
                "real Ed25519 public key must match the public-key target regex: " + validPublicKey);
    }

    // ---- plain ----------------------------------------------------------

    @Test
    public void plainMessageNoAction() throws Exception {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .textMessage("hello")
                .clientProtocolVersion(MessageProtocolVersion.CURRENT)
                .build();
        ValidationResult r = MessageActionValidator.validate(bean, outer(senderPk), ValidationContext.empty());
        assertTrue(r.overallValid());
        assertTrue(r.decorations().isEmpty());
    }

    @Test
    public void legacyPlainMessageGrandfathered() throws Exception {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .textMessage("legacy hello")
                .build(); // no version, no features
        ValidationResult r = MessageActionValidator.validate(bean, outer(senderPk), ValidationContext.empty());
        assertTrue(r.overallValid());
        assertTrue(r.decorations().isEmpty());
    }

    // ---- hard violations (H1-H6) ---------------------------------------

    @Test
    public void h1_malformedVersion() {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .textMessage("x").clientProtocolVersion("1.0.0").build();
        MalformedProtocolVersionException ex = assertThrows(MalformedProtocolVersionException.class,
                () -> MessageActionValidator.validate(bean, outer(senderPk), ValidationContext.empty()));
        assertEquals("INVALID_PROTOCOL_VERSION_MALFORMED", ex.getCode());
    }

    @Test
    public void h1_prefixedVersion() {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .textMessage("x").clientProtocolVersion("v1.0").build();
        assertThrows(MalformedProtocolVersionException.class,
                () -> MessageActionValidator.validate(bean, outer(senderPk), ValidationContext.empty()));
    }

    @Test
    public void h2_incompatibleMajor() {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .textMessage("x").clientProtocolVersion("2.0").build();
        IncompatibleMajorVersionException ex = assertThrows(IncompatibleMajorVersionException.class,
                () -> MessageActionValidator.validate(bean, outer(senderPk), ValidationContext.empty()));
        assertEquals("INVALID_PROTOCOL_VERSION_INCOMPATIBLE_MAJOR", ex.getCode());
    }

    @Test
    public void h3_missingVersionWithFeatures() {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.REPLY).targets(List.of(validSignature)).build(); // no version
        MissingVersionWithFeaturesException ex = assertThrows(MissingVersionWithFeaturesException.class,
                () -> MessageActionValidator.validate(bean, outer(senderPk), ValidationContext.empty()));
        assertEquals("INVALID_PROTOCOL_VERSION_MISSING_WITH_FEATURES", ex.getCode());
    }

    @Test
    public void h4_legacyVersionWithFeatures() {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.REPLY).targets(List.of(validSignature))
                .clientProtocolVersion("1.0").build();
        LegacyVersionWithFeaturesException ex = assertThrows(LegacyVersionWithFeaturesException.class,
                () -> MessageActionValidator.validate(bean, outer(senderPk), ValidationContext.empty()));
        assertEquals("INVALID_PROTOCOL_VERSION_LEGACY_WITH_FEATURES", ex.getCode());
    }

    @Test
    public void h5_unknownAction() {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .action("__test_unknown_action__")
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        UnknownActionException ex = assertThrows(UnknownActionException.class,
                () -> MessageActionValidator.validate(bean, outer(senderPk), ValidationContext.empty()));
        assertEquals("INVALID_ACTION", ex.getCode());
        assertEquals("__test_unknown_action__", ex.getOffendingValue());
    }

    @Test
    public void h6_innerSignatureFailure() throws Exception {
        // Corrupt the embedded original's signature.
        BasicMessageRequestBean corrupted = new BasicMessageRequestBean(
                signedOriginal.getBasicMessageSignedContentBean(),
                signedOriginal.getFrom(),
                flipLastChar(signedOriginal.getSignature()),
                signedOriginal.getMessageType(),
                signedOriginal.getSignatureType());
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .textMessage("relayed")
                .action(MessageAction.SHARE_HISTORY)
                .originalMessage(corrupted)
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        InnerSignatureFailureException ex = assertThrows(InnerSignatureFailureException.class,
                () -> MessageActionValidator.validate(bean, outer(senderPk), ValidationContext.empty()));
        assertEquals("INNER_SIGNATURE_FAILURE", ex.getCode());
    }

    // ---- soft violations -----------------------------------------------

    @Test
    public void s_replyCardinality() throws Exception {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.REPLY)
                .targets(List.of(validSignature, validSignature))
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        ValidationResult r = MessageActionValidator.validate(bean, outer(senderPk), ValidationContext.empty());
        assertSingle(r, ValidationDecorationCodes.INVALID_REPLY_MALFORMED_TARGETS, DecorationSeverity.WARN);
    }

    @Test
    public void s_replyBadSignatureFormat() throws Exception {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.REPLY).targets(List.of("not-a-signature"))
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        ValidationResult r = MessageActionValidator.validate(bean, outer(senderPk), ValidationContext.empty());
        assertSingle(r, ValidationDecorationCodes.INVALID_REPLY_BAD_SIGNATURE_FORMAT, DecorationSeverity.WARN);
    }

    @Test
    public void s_forwardBadPublicKeyFormat() throws Exception {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.FORWARD).targets(List.of("not-a-public-key"))
                .fwContent(BasicMessageEncryptedContentBean.builder().textMessage("fwd").build())
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        ValidationResult r = MessageActionValidator.validate(bean, outer(senderPk), ValidationContext.empty());
        assertSingle(r, ValidationDecorationCodes.INVALID_FORWARD_BAD_PUBLIC_KEY_FORMAT, DecorationSeverity.WARN);
    }

    @Test
    public void s_replyBrokenReference() throws Exception {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.REPLY).targets(List.of(validSignature))
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        // resolver returns null -> not in cache
        ValidationContext ctx = new ValidationContext(null, sig -> null, null);
        ValidationResult r = MessageActionValidator.validate(bean, outer(senderPk), ctx);
        assertSingle(r, ValidationDecorationCodes.BROKEN_REFERENCE_REPLY, DecorationSeverity.INFO);
    }

    @Test
    public void s_unauthorizedEdit() throws Exception {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.EDIT).targets(List.of(validSignature))
                .textMessage("edited")
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        // target resolvable but authored by someone else
        Function<String, String> resolver = sig -> "SomeOtherAuthorPublicKey.";
        ValidationContext ctx = new ValidationContext(null, resolver, null);
        ValidationResult r = MessageActionValidator.validate(bean, outer(senderPk), ctx);
        assertSingle(r, ValidationDecorationCodes.UNAUTHORIZED_EDIT, DecorationSeverity.WARN);
    }

    @Test
    public void s_unauthorizedPin() throws Exception {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.PIN).targets(List.of(validSignature))
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        // target resolvable (in cache) but sender is not the conversation creator
        Function<String, String> resolver = sig -> senderPk;
        ValidationContext ctx = new ValidationContext("SomeCreatorPublicKey.", resolver, null);
        ValidationResult r = MessageActionValidator.validate(bean, outer(senderPk), ctx);
        assertSingle(r, ValidationDecorationCodes.UNAUTHORIZED_PIN, DecorationSeverity.WARN);
    }

    @Test
    public void s_shareHistoryMissingOriginal() throws Exception {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.SHARE_HISTORY).textMessage("relayed")
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        ValidationResult r = MessageActionValidator.validate(bean, outer(senderPk), ValidationContext.empty());
        assertSingle(r, ValidationDecorationCodes.INVALID_SHARE_HISTORY_MISSING_ORIGINAL_MESSAGE, DecorationSeverity.WARN);
    }

    @Test
    public void s_shareHistoryCrossConversation() throws Exception {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.SHARE_HISTORY).textMessage("relayed")
                .originalMessage(signedOriginal)
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        // outer conversation differs from the embedded original's conversation
        ValidationResult r = MessageActionValidator.validate(
                bean,
                new BasicMessageRequestBean(new BasicMessageSignedContentBean(OTHER_CONVERSATION, null, null),
                        senderPk, "outer-sig", "TOPIC_MESSAGE", "Ed25519BC"),
                ValidationContext.empty());
        assertSingle(r, ValidationDecorationCodes.INVALID_SHARE_HISTORY_CROSS_CONVERSATION, DecorationSeverity.ERROR);
        assertFalse(r.overallValid());
    }

    @Test
    public void s_shareHistoryNested() throws Exception {
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.SHARE_HISTORY).textMessage("relayed")
                .originalMessage(signedOriginal)
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        // embedded decryptor reports the inner is itself a share_history
        Function<BasicMessageRequestBean, BasicMessageEncryptedContentBean> decryptor
                = env -> BasicMessageEncryptedContentBean.builder().action(MessageAction.SHARE_HISTORY).build();
        ValidationContext ctx = new ValidationContext(null, sig -> null, decryptor);
        ValidationResult r = MessageActionValidator.validate(bean, outer(senderPk), ctx);
        assertSingle(r, ValidationDecorationCodes.INVALID_SHARE_HISTORY_NESTED, DecorationSeverity.WARN);
    }

    @Test
    public void s_forwardDepthExceeded() throws Exception {
        BasicMessageEncryptedContentBean deep = buildForwardChain(11);
        ValidationResult r = MessageActionValidator.validate(deep, outer(senderPk), ValidationContext.empty());
        assertSingle(r, ValidationDecorationCodes.FORWARD_DEPTH_EXCEEDED, DecorationSeverity.INFO);
    }

    @Test
    public void forwardDepthAtCapOk() throws Exception {
        BasicMessageEncryptedContentBean atCap = buildForwardChain(10);
        ValidationResult r = MessageActionValidator.validate(atCap, outer(senderPk), ValidationContext.empty());
        assertTrue(r.overallValid());
        assertTrue(r.decorations().isEmpty(), "depth exactly 10 must not be decorated");
    }

    @Test
    public void s_reactionMimeViolation() throws Exception {
        ChatMediaPlaceholderBean tiff = ChatMediaPlaceholderBean.builder()
                .mediaType("image/tiff").isTheObject(true).preview("aGVsbG8=").build();
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.REACTION).targets(List.of(validSignature))
                .attachedMedia(List.of(tiff))
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        ValidationContext ctx = new ValidationContext(null, sig -> senderPk, null);
        ValidationResult r = MessageActionValidator.validate(bean, outer(senderPk), ctx);
        assertSingle(r, ValidationDecorationCodes.INLINE_MIME_VIOLATION, DecorationSeverity.WARN);
    }

    @Test
    public void s_inlineHashMismatch() throws Exception {
        ChatMediaPlaceholderBean png = ChatMediaPlaceholderBean.builder()
                .mediaType("image/png").isTheObject(true).preview("aGVsbG8=")
                .unencryptedContentHash("definitely-the-wrong-hash").build();
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.REACTION).targets(List.of(validSignature))
                .attachedMedia(List.of(png))
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        ValidationContext ctx = new ValidationContext(null, sig -> senderPk, null);
        ValidationResult r = MessageActionValidator.validate(bean, outer(senderPk), ctx);
        assertSingle(r, ValidationDecorationCodes.INLINE_HASH_MISMATCH, DecorationSeverity.ERROR);
        assertFalse(r.overallValid());
    }

    @Test
    public void validInlineReactionPasses() throws Exception {
        String preview = "aGVsbG8="; // "hello"
        String correctHash = TkmSignUtils.Hash256B64URL(preview);
        ChatMediaPlaceholderBean png = ChatMediaPlaceholderBean.builder()
                .mediaType("image/png").isTheObject(true).preview(preview)
                .unencryptedContentHash(correctHash).build();
        BasicMessageEncryptedContentBean bean = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.REACTION).targets(List.of(validSignature))
                .attachedMedia(List.of(png))
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        ValidationContext ctx = new ValidationContext(null, sig -> senderPk, null);
        ValidationResult r = MessageActionValidator.validate(bean, outer(senderPk), ctx);
        assertTrue(r.overallValid());
        assertTrue(r.decorations().isEmpty());
    }

    @Test
    public void caseInsensitiveActionSameResult() throws Exception {
        BasicMessageEncryptedContentBean lower = BasicMessageEncryptedContentBean.builder()
                .action("reply").targets(List.of(validSignature))
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        BasicMessageEncryptedContentBean upper = BasicMessageEncryptedContentBean.builder()
                .action("REPLY").targets(List.of(validSignature))
                .clientProtocolVersion(MessageProtocolVersion.CURRENT).build();
        ValidationContext ctx = new ValidationContext(null, sig -> senderPk, null);
        ValidationResult rl = MessageActionValidator.validate(lower, outer(senderPk), ctx);
        ValidationResult ru = MessageActionValidator.validate(upper, outer(senderPk), ctx);
        assertEquals(rl.decorations().size(), ru.decorations().size());
        assertTrue(rl.overallValid() && ru.overallValid());
    }

    // ---- helpers --------------------------------------------------------

    private static void assertSingle(ValidationResult r, String code, String severity) {
        assertEquals(1, r.decorations().size(),
                "expected exactly one decoration but got " + r.decorations());
        Decoration d = r.decorations().get(0);
        assertEquals(code, d.code());
        assertEquals(severity, d.severity());
    }

    private static BasicMessageEncryptedContentBean buildForwardChain(int links) {
        BasicMessageEncryptedContentBean node = BasicMessageEncryptedContentBean.builder()
                .textMessage("leaf").build();
        for (int i = 0; i < links - 1; i++) {
            node = BasicMessageEncryptedContentBean.builder().textMessage("link" + i).fwContent(node).build();
        }
        // outermost is the forward action carrier
        return BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.FORWARD)
                .fwContent(node)
                .clientProtocolVersion(MessageProtocolVersion.CURRENT)
                .build();
    }

    private static String flipLastChar(String s) {
        char[] c = s.toCharArray();
        // signature ends with ".."; flip a char in the body to keep the format valid but break the signature
        c[0] = (c[0] == 'A') ? 'B' : 'A';
        return new String(c);
    }
}
