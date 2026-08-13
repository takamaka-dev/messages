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

    /**
     * Build a blob placeholder — an arbitrarily large OBJECT living on the server —
     * carrying {@code previewBytes} of decoded preview, or none when negative.
     */
    private static BasicMessageEncryptedContentBean withBlob(int previewBytes) {
        var b = ChatMediaPlaceholderBean.builder()
                .mediaType("image/jpeg").isTheObject(false)
                .encryptedFileHash("aa4529146d71")
                .size(4L * 1024 * 1024 * 1024) // a 4 GB object: the OBJECT is unconstrained
                .fileName("huge.jpg");
        if (previewBytes >= 0) {
            b.preview(b64Of(previewBytes));
        }
        return BasicMessageEncryptedContentBean.builder()
                .textMessage("a file")
                .attachedMedia(List.of(b.build()))
                .build();
    }

    @Test
    @DisplayName("⭐ a blob's OBJECT is unconstrained — nothing is ever forced inline")
    void blobObjectSizeIsNeverConstrained() throws Exception {
        // The rule is one-directional by ruling: nothing is ever forced inline,
        // which is what lets forward / share-history pass placeholders through.
        // A 4 GB object with a conformant 6 KB preview must build.
        assertDoesNotThrow(() -> ChatCryptoUtils.getBasicMessageBean(
                wallet(), 0, CONV, KEY, List.of(), withBlob(6337)));
    }

    @Test
    @DisplayName("a blob may carry NO preview at all — absent is legal, not a defect")
    void blobWithoutPreviewIsAccepted() throws Exception {
        assertDoesNotThrow(() -> ChatCryptoUtils.getBasicMessageBean(
                wallet(), 0, CONV, KEY, List.of(), withBlob(-1)));
    }

    @Test
    @DisplayName("⭐ W1: a blob's PREVIEW is bounded by the same byte rule as inline content")
    void oversizedBlobPreviewIsRefused() throws Exception {
        // This assertion is the whole of §PREVIEW-CONFORMANCE W1. Until 2026-08-13
        // rejectOversizedInlineContent `continue`d on every isTheObject == false
        // placeholder, so MAX_INLINE_BYTES had never once applied to a blob's
        // preview — measured previews ran to 1.9x the file they previewed.
        //
        // The object may be 4 GB (asserted above). The THUMBNAIL may not, because
        // it rides inside every envelope, fan-out and history fetch.
        ChatMessageException ex = assertThrows(ChatMessageException.class, ()
                -> ChatCryptoUtils.getBasicMessageBean(wallet(), 0, CONV, KEY, List.of(),
                        withBlob(InlineContentLimits.MAX_INLINE_BYTES + 1)));

        assertTrue(ex.getMessage().contains("huge.jpg"), ex.getMessage());
        assertTrue(ex.getMessage().contains(String.valueOf(InlineContentLimits.MAX_INLINE_BYTES)),
                ex.getMessage());
        // The remedy differs from the inline one: an oversized THUMBNAIL is not
        // fixed by "send it as an attachment" — it already IS an attachment.
        assertTrue(ex.getMessage().toLowerCase().contains("smaller thumbnail"),
                "it must point at the remedy that applies to a preview: " + ex.getMessage());
    }

    @Test
    @DisplayName("a blob preview at exactly the limit builds — boundary is inclusive")
    void blobPreviewAtLimitIsAccepted() throws Exception {
        assertDoesNotThrow(() -> ChatCryptoUtils.getBasicMessageBean(
                wallet(), 0, CONV, KEY, List.of(),
                withBlob(InlineContentLimits.MAX_INLINE_BYTES)));
    }

    @Test
    @DisplayName("⭐ W1: attached_media length is capped — a per-item rule bounds nothing otherwise")
    void oversizedMediaListIsRefused() throws Exception {
        List<ChatMediaPlaceholderBean> many = new java.util.ArrayList<>();
        for (int i = 0; i <= InlineContentLimits.MAX_ATTACHED_MEDIA; i++) {
            many.add(ChatMediaPlaceholderBean.builder()
                    .mediaType("image/jpeg").isTheObject(false)
                    .encryptedFileHash("aa4529146d7" + i)
                    .fileName("f" + i + ".jpg").build());
        }
        ChatMessageException ex = assertThrows(ChatMessageException.class, ()
                -> ChatCryptoUtils.getBasicMessageBean(wallet(), 0, CONV, KEY, List.of(),
                        BasicMessageEncryptedContentBean.builder().attachedMedia(many).build()));
        assertTrue(ex.getMessage().contains(String.valueOf(InlineContentLimits.MAX_ATTACHED_MEDIA)),
                ex.getMessage());
    }

    @Test
    @DisplayName("⭐ W1: the AGGREGATE preview budget binds before the count cap does")
    void aggregatePreviewBudgetIsEnforced() throws Exception {
        // Positive control that this is not the count cap firing: the list is
        // deliberately SHORT enough to pass MAX_ATTACHED_MEDIA, and every single
        // placeholder is individually legal (exactly MAX_INLINE_BYTES). Only the
        // sum is illegal — which is the case a per-item limit cannot catch.
        int perItem = InlineContentLimits.MAX_INLINE_BYTES;
        int count = (InlineContentLimits.MAX_TOTAL_PREVIEW_BYTES / perItem) + 1;
        assertTrue(count <= InlineContentLimits.MAX_ATTACHED_MEDIA,
                "fixture must not trip the count cap instead: " + count);

        List<ChatMediaPlaceholderBean> many = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            many.add(ChatMediaPlaceholderBean.builder()
                    .mediaType("image/jpeg").isTheObject(false)
                    .encryptedFileHash("aa4529146d7" + i)
                    .preview(b64Of(perItem))
                    .fileName("f" + i + ".jpg").build());
        }
        ChatMessageException ex = assertThrows(ChatMessageException.class, ()
                -> ChatCryptoUtils.getBasicMessageBean(wallet(), 0, CONV, KEY, List.of(),
                        BasicMessageEncryptedContentBean.builder().attachedMedia(many).build()));
        assertTrue(ex.getMessage().contains("aggregate"), ex.getMessage());
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
