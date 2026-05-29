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

import io.takamaka.messages.chat.attachment.ChatMediaPlaceholderBean;
import io.takamaka.messages.chat.message.BasicMessageEncryptedContentBean;
import io.takamaka.messages.chat.message.BasicMessageRequestBean;
import io.takamaka.messages.chat.message.MessageAction;
import io.takamaka.messages.chat.message.MessageProtocolVersion;
import io.takamaka.messages.exception.ChatCryptoConstructionException;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.exception.ForwardDepthExceededException;
import io.takamaka.messages.exception.InlineContentViolationException;
import io.takamaka.messages.exception.InvalidEmbeddedEnvelopeException;
import io.takamaka.messages.exception.MalformedTargetException;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.exceptions.WalletException;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Phase 1 §3.4 {@code ChatCryptoUtils.get*MessageBean}
 * helpers.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class ChatCryptoUtilsActionsTest {

    private static final String CONVERSATION = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";
    private static final String OTHER_CONVERSATION = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
    private static final String SYMKEY = "actionsHelperTestSymmetricKey";
    private static final String OTHER_SYMKEY = "actionsHelperOtherSymmetricKey";

    private static InstanceWalletKeystoreInterface wallet;
    private static SendContext ctx;
    private static String parentSignature;
    private static String validForwardPk;

    @BeforeAll
    public static void setUp() throws WalletException, ChatCryptoConstructionException {
        wallet = new InstanceWalletKeyStoreBCED25519("actions_test_wallet", "actionsPwd");
        ctx = new SendContext(wallet, 0, CONVERSATION, SYMKEY);
        validForwardPk = wallet.getPublicKeyAtIndexURL64(0);
        // a real signature to use as a parent target
        BasicMessageRequestBean parent = ChatCryptoUtils.getPlainMessageBean(ctx, "parent", null, null);
        parentSignature = parent.getSignature();
    }

    private static BasicMessageEncryptedContentBean decryptInner(BasicMessageRequestBean req)
            throws ChatMessageException {
        return ChatCryptoUtils.decryptBasicMessageEncryptedContentBeanWithScope(
                req.getBasicMessageSignedContentBean().getEncryptedContent(), SYMKEY,
                CHAT_MESSAGE_TYPES.TOPIC_MESSAGE);
    }

    // ---- happy paths ----------------------------------------------------

    @Test
    public void plainHappyPath() throws Exception {
        BasicMessageRequestBean r = ChatCryptoUtils.getPlainMessageBean(ctx, "hi", null, null);
        BasicMessageEncryptedContentBean inner = decryptInner(r);
        assertEquals("hi", inner.getTextMessage());
        assertNull(inner.getAction());
        assertEquals(MessageProtocolVersion.CURRENT, inner.getClientProtocolVersion());
    }

    @Test
    public void replyHappyPath() throws Exception {
        BasicMessageRequestBean r = ChatCryptoUtils.getReplyMessageBean(ctx, parentSignature, "re", null, null);
        BasicMessageEncryptedContentBean inner = decryptInner(r);
        assertEquals(MessageAction.REPLY, inner.getAction());
        assertEquals(List.of(parentSignature), inner.getTargets());
        assertEquals("re", inner.getTextMessage());
    }

    @Test
    public void reactionHappyPath() throws Exception {
        ChatMediaPlaceholderBean payload = ChatMediaPlaceholderBean.builder()
                .mediaType("image/png").isTheObject(true).preview("aGVsbG8=").build();
        BasicMessageRequestBean r = ChatCryptoUtils.getReactionMessageBean(ctx, parentSignature, payload, null);
        BasicMessageEncryptedContentBean inner = decryptInner(r);
        assertEquals(MessageAction.REACTION, inner.getAction());
        assertEquals(1, inner.getAttachedMedia().size());
        assertNull(inner.getTextMessage(), "reaction should not carry text");
    }

    @Test
    public void reactionRemoveHappyPath() throws Exception {
        BasicMessageRequestBean r = ChatCryptoUtils.getReactionRemoveMessageBean(ctx, parentSignature);
        assertEquals(MessageAction.REACTION_REMOVE, decryptInner(r).getAction());
    }

    @Test
    public void editHappyPath() throws Exception {
        BasicMessageRequestBean r = ChatCryptoUtils.getEditMessageBean(ctx, parentSignature, "new", null, null);
        BasicMessageEncryptedContentBean inner = decryptInner(r);
        assertEquals(MessageAction.EDIT, inner.getAction());
        assertEquals("new", inner.getTextMessage());
    }

    @Test
    public void redactHappyPath() throws Exception {
        BasicMessageRequestBean r = ChatCryptoUtils.getRedactMessageBean(ctx, parentSignature, "spam");
        assertEquals(MessageAction.REDACT, decryptInner(r).getAction());
    }

    @Test
    public void pinHappyPath() throws Exception {
        BasicMessageRequestBean r = ChatCryptoUtils.getPinMessageBean(ctx, parentSignature, "important");
        BasicMessageEncryptedContentBean inner = decryptInner(r);
        assertEquals(MessageAction.PIN, inner.getAction());
        assertEquals(List.of(parentSignature), inner.getTargets());
    }

    @Test
    public void unpinHappyPath() throws Exception {
        BasicMessageRequestBean r = ChatCryptoUtils.getUnpinMessageBean(ctx);
        BasicMessageEncryptedContentBean inner = decryptInner(r);
        assertEquals(MessageAction.UNPIN, inner.getAction());
        assertNull(inner.getTargets());
    }

    @Test
    public void forwardHappyPath() throws Exception {
        BasicMessageEncryptedContentBean toForward = BasicMessageEncryptedContentBean.builder()
                .textMessage("original to forward").build();
        BasicMessageRequestBean r = ChatCryptoUtils.getForwardMessageBean(ctx, toForward, "fyi", validForwardPk);
        BasicMessageEncryptedContentBean inner = decryptInner(r);
        assertEquals(MessageAction.FORWARD, inner.getAction());
        assertEquals(List.of(validForwardPk), inner.getTargets());
        assertNotNull(inner.getFwContent());
    }

    @Test
    public void forwardAnonymousHappyPath() throws Exception {
        BasicMessageEncryptedContentBean toForward = BasicMessageEncryptedContentBean.builder()
                .textMessage("anon").build();
        BasicMessageRequestBean r = ChatCryptoUtils.getForwardMessageBean(ctx, toForward, "fyi", null);
        BasicMessageEncryptedContentBean inner = decryptInner(r);
        assertEquals(MessageAction.FORWARD, inner.getAction());
        assertTrue(inner.getTargets() == null || inner.getTargets().isEmpty());
    }

    @Test
    public void shareHistoryHappyPath() throws Exception {
        BasicMessageRequestBean original = ChatCryptoUtils.getPlainMessageBean(ctx, "original", null, null);
        BasicMessageRequestBean r = ChatCryptoUtils.getShareHistoryMessageBean(ctx, original, "relaying", false);
        BasicMessageEncryptedContentBean inner = decryptInner(r);
        assertEquals(MessageAction.SHARE_HISTORY, inner.getAction());
        assertNotNull(inner.getOriginalMessage());
        assertNull(inner.getReShared(), "first-share must omit re_shared");
    }

    @Test
    public void shareHistoryReSharedSetsFlag() throws Exception {
        BasicMessageRequestBean original = ChatCryptoUtils.getPlainMessageBean(ctx, "original", null, null);
        BasicMessageRequestBean r = ChatCryptoUtils.getShareHistoryMessageBean(ctx, original, "re-relaying", true);
        assertEquals(Boolean.TRUE, decryptInner(r).getReShared());
    }

    // ---- auto-stamp -----------------------------------------------------

    @Test
    public void everyHelperAutoStampsCurrentVersion() throws Exception {
        BasicMessageRequestBean original = ChatCryptoUtils.getPlainMessageBean(ctx, "o", null, null);
        ChatMediaPlaceholderBean payload = ChatMediaPlaceholderBean.builder()
                .mediaType("image/png").isTheObject(true).preview("aGVsbG8=").build();
        BasicMessageEncryptedContentBean fwd = BasicMessageEncryptedContentBean.builder().textMessage("f").build();

        List<BasicMessageRequestBean> all = List.of(
                ChatCryptoUtils.getPlainMessageBean(ctx, "t", null, null),
                ChatCryptoUtils.getReplyMessageBean(ctx, parentSignature, "t", null, null),
                ChatCryptoUtils.getReactionMessageBean(ctx, parentSignature, payload, null),
                ChatCryptoUtils.getReactionRemoveMessageBean(ctx, parentSignature),
                ChatCryptoUtils.getEditMessageBean(ctx, parentSignature, "t", null, null),
                ChatCryptoUtils.getRedactMessageBean(ctx, parentSignature, null),
                ChatCryptoUtils.getPinMessageBean(ctx, parentSignature, null),
                ChatCryptoUtils.getUnpinMessageBean(ctx),
                ChatCryptoUtils.getForwardMessageBean(ctx, fwd, "t", null),
                ChatCryptoUtils.getShareHistoryMessageBean(ctx, original, "t", false));
        for (BasicMessageRequestBean r : all) {
            assertEquals(MessageProtocolVersion.CURRENT, decryptInner(r).getClientProtocolVersion());
        }
    }

    // ---- bad inputs -----------------------------------------------------

    @Test
    public void replyMalformedSignature() {
        assertThrows(MalformedTargetException.class,
                () -> ChatCryptoUtils.getReplyMessageBean(ctx, "not-a-sig", "x", null, null));
    }

    @Test
    public void nullContextRejected() {
        assertThrows(ChatCryptoConstructionException.class,
                () -> ChatCryptoUtils.getPlainMessageBean(null, "x", null, null));
    }

    @Test
    public void reactionNullPayloadRejected() {
        ChatCryptoConstructionException ex = assertThrows(ChatCryptoConstructionException.class,
                () -> ChatCryptoUtils.getReactionMessageBean(ctx, parentSignature, null, null));
        assertEquals(ChatCryptoConstructionException.MISSING_REACTION_PAYLOAD, ex.getCode());
    }

    @Test
    public void reactionDisallowedMimeRejected() {
        ChatMediaPlaceholderBean tiff = ChatMediaPlaceholderBean.builder()
                .mediaType("image/tiff").isTheObject(true).preview("aGVsbG8=").build();
        InlineContentViolationException ex = assertThrows(InlineContentViolationException.class,
                () -> ChatCryptoUtils.getReactionMessageBean(ctx, parentSignature, tiff, null));
        assertEquals(ChatCryptoConstructionException.REACTION_MIME_NOT_ALLOWED, ex.getCode());
    }

    @Test
    public void forwardDepthExceeded() {
        BasicMessageEncryptedContentBean tooDeep = buildChain(10); // 1 + 10 = 11 > MAX(10)
        ForwardDepthExceededException ex = assertThrows(ForwardDepthExceededException.class,
                () -> ChatCryptoUtils.getForwardMessageBean(ctx, tooDeep, "x", null));
        assertEquals(11, ex.getActualDepth());
        assertEquals(10, ex.getMaxDepth());
    }

    @Test
    public void forwardMalformedClaimedOriginPk() {
        BasicMessageEncryptedContentBean toForward = BasicMessageEncryptedContentBean.builder()
                .textMessage("o").build();
        assertThrows(MalformedTargetException.class,
                () -> ChatCryptoUtils.getForwardMessageBean(ctx, toForward, "x", "not-a-pk"));
    }

    @Test
    public void pinMalformedTargetSignature() {
        assertThrows(MalformedTargetException.class,
                () -> ChatCryptoUtils.getPinMessageBean(ctx, "bad", "reason"));
    }

    @Test
    public void shareHistoryNullOriginalRejected() {
        InvalidEmbeddedEnvelopeException ex = assertThrows(InvalidEmbeddedEnvelopeException.class,
                () -> ChatCryptoUtils.getShareHistoryMessageBean(ctx, null, "x", false));
        assertEquals(ChatCryptoConstructionException.MISSING_ORIGINAL_ENVELOPE, ex.getCode());
    }

    @Test
    public void shareHistoryCrossConversationRejected() throws Exception {
        SendContext otherCtx = new SendContext(wallet, 0, OTHER_CONVERSATION, OTHER_SYMKEY);
        BasicMessageRequestBean foreign = ChatCryptoUtils.getPlainMessageBean(otherCtx, "foreign", null, null);
        InvalidEmbeddedEnvelopeException ex = assertThrows(InvalidEmbeddedEnvelopeException.class,
                () -> ChatCryptoUtils.getShareHistoryMessageBean(ctx, foreign, "x", false));
        assertEquals(ChatCryptoConstructionException.EMBEDDED_INNER_CONVERSATION_MISMATCH, ex.getCode());
    }

    // ---- registry completeness + builder parity ------------------------

    @Test
    public void everyKnownActionHasAHelper() {
        for (String action : MessageAction.KNOWN) {
            String methodName = "get" + toCamel(action) + "MessageBean";
            assertTrue(hasMethod(methodName), "missing helper for action '" + action + "': " + methodName);
        }
    }

    @Test
    public void builderEqualsAllArgsConstructor() {
        BasicMessageEncryptedContentBean built = BasicMessageEncryptedContentBean.builder()
                .textMessage("x").build();
        BasicMessageEncryptedContentBean ctor = new BasicMessageEncryptedContentBean(
                "x", null, null, null, null, null, null, null);
        assertEquals(ctor, built);
    }

    // ---- helpers --------------------------------------------------------

    private static boolean hasMethod(String name) {
        return Arrays.stream(ChatCryptoUtils.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals(name));
    }

    private static String toCamel(String snake) {
        StringBuilder sb = new StringBuilder();
        for (String part : snake.split("_")) {
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    /** Builds a bean whose fw_content chain has {@code links} nodes. */
    private static BasicMessageEncryptedContentBean buildChain(int links) {
        BasicMessageEncryptedContentBean node = BasicMessageEncryptedContentBean.builder()
                .textMessage("leaf").build();
        for (int i = 0; i < links - 1; i++) {
            node = BasicMessageEncryptedContentBean.builder().textMessage("n" + i).fwContent(node).build();
        }
        return BasicMessageEncryptedContentBean.builder().textMessage("head").fwContent(node).build();
    }
}
