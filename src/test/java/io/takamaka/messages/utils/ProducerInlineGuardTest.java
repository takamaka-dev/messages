package io.takamaka.messages.utils;

import io.takamaka.messages.chat.attachment.ChatMediaPlaceholderBean;
import io.takamaka.messages.chat.attachment.InlineContentLimits;
import io.takamaka.messages.chat.message.BasicMessageEncryptedContentBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The producer guard: a message carrying oversized INLINE content cannot be
 * built, so it can never be sent.
 *
 * <p>The guard sits in {@link ChatCryptoUtils#getBasicMessageBean} — the single
 * choke point every Java producer passes through (plain messages, attachments,
 * reactions, forwards) — rather than in a bean factory, because
 * {@link ChatMediaPlaceholderBean} has a public Lombok builder and a
 * constructor-level check would be trivially bypassable.
 *
 * <p>Why it matters: inline content is copied into every encrypted message body,
 * every notification fan-out and every history fetch. Measured 2026-08-12, the
 * envelope runs ~1.8x the inline payload, so an unbounded inline path bloats
 * storage and bandwidth for every member of a conversation, forever.
 */
class ProducerInlineGuardTest {

    private static final String CONV = "189e41eff236159170fb58a1e34bb2c8482c3cc439d1c7a5ba5180b9f29ac30d";
    private static final String KEY = "0123456789abcdef0123456789abcdef";

    private static InstanceWalletKeystoreInterface wallet() throws Exception {
        return new InstanceWalletKeyStoreBCED25519("inline_guard_test", "testpassword");
    }

    private static String b64Of(int bytes) {
        return Base64.getEncoder().encodeToString(new byte[bytes]);
    }

    private static BasicMessageEncryptedContentBean withInline(int payloadBytes) {
        return BasicMessageEncryptedContentBean.builder()
                .textMessage("look at this")
                .attachedMedia(List.of(ChatMediaPlaceholderBean.builder()
                        .mediaType("image/png").isTheObject(true)
                        .preview(b64Of(payloadBytes)).fileName("big.png").build()))
                .build();
    }

    @Test
    @DisplayName("⭐ oversized inline content CANNOT be built into a message")
    void oversizedInlineIsRefused() throws Exception {
        ChatMessageException ex = assertThrows(ChatMessageException.class, ()
                -> ChatCryptoUtils.getBasicMessageBean(wallet(), 0, CONV, KEY, List.of(),
                        withInline(InlineContentLimits.MAX_INLINE_BYTES + 1)));

        // The message must name the file and the limit — a producer-side refusal
        // that does not say why just looks like a bug to whoever hit it.
        assertTrue(ex.getMessage().contains("big.png"), ex.getMessage());
        assertTrue(ex.getMessage().contains(String.valueOf(InlineContentLimits.MAX_INLINE_BYTES)),
                ex.getMessage());
        assertTrue(ex.getMessage().toLowerCase().contains("regular attachment"),
                "it must point at the path the sender should have used: " + ex.getMessage());
    }

    @Test
    @DisplayName("inline content at or under the limit builds normally")
    void legalInlineIsAccepted() throws Exception {
        assertDoesNotThrow(() -> ChatCryptoUtils.getBasicMessageBean(
                wallet(), 0, CONV, KEY, List.of(),
                withInline(InlineContentLimits.MAX_INLINE_BYTES)));
    }

    @Test
    @DisplayName("⭐ a BLOB placeholder of any size builds — small-via-blob is legal")
    void blobPathIsNeverConstrained() throws Exception {
        // The rule is one-directional by ruling: nothing is ever forced inline,
        // which is what lets forward / share-history pass placeholders through.
        BasicMessageEncryptedContentBean blob = BasicMessageEncryptedContentBean.builder()
                .textMessage("a file")
                .attachedMedia(List.of(ChatMediaPlaceholderBean.builder()
                        .mediaType("image/jpeg").isTheObject(false)
                        .encryptedFileHash("aa4529146d71")
                        .preview(b64Of(InlineContentLimits.MAX_INLINE_BYTES * 2))
                        .fileName("huge.jpg").build()))
                .build();

        assertDoesNotThrow(() -> ChatCryptoUtils.getBasicMessageBean(
                wallet(), 0, CONV, KEY, List.of(), blob));
    }

    @Test
    @DisplayName("an inline placeholder with undecodable content is refused too")
    void undecodableInlineIsRefused() throws Exception {
        BasicMessageEncryptedContentBean bad = BasicMessageEncryptedContentBean.builder()
                .attachedMedia(List.of(ChatMediaPlaceholderBean.builder()
                        .mediaType("image/png").isTheObject(true)
                        .preview("!!!not base64!!!").fileName("broken.png").build()))
                .build();

        // Sending it would produce a message no receiver can render.
        ChatMessageException ex = assertThrows(ChatMessageException.class, ()
                -> ChatCryptoUtils.getBasicMessageBean(wallet(), 0, CONV, KEY, List.of(), bad));
        assertTrue(ex.getMessage().contains("broken.png"), ex.getMessage());
    }

    @Test
    @DisplayName("a plain text message with no media is unaffected")
    void plainTextIsUnaffected() throws Exception {
        assertDoesNotThrow(() -> ChatCryptoUtils.getBasicMessageBean(
                wallet(), 0, CONV, KEY, List.of(),
                BasicMessageEncryptedContentBean.builder().textMessage("hello").build()));
    }
}
