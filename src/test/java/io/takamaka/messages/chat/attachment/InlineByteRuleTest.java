package io.takamaka.messages.chat.attachment;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The inline byte rule — the single normative boundary between the inline and
 * the regular attachment path.
 *
 * <p><b>What this replaces.</b> An audit on 2026-08-12 found the rule was
 * implemented once, partially, and by the wrong criteria: only chat-web-gui read
 * {@link InlineContentLimits} at all, it checked only the byte limit, and NO
 * receiver enforced anything. The measured consequence was that one 43 253-byte
 * file produced 78 KB / 161 KB / 208 KB of wire traffic depending on which
 * client sent it.
 *
 * <p>The rule is deliberately ONE-DIRECTIONAL (operator ruling 2026-08-12):
 * inline over the limit is a violation; sending a SMALL file through the regular
 * blob path is not. That asymmetry is what lets forward / share-history pass a
 * placeholder through untouched.
 *
 * <p>{@code MAX_THUMBNAIL_DIMENSION_PX} was removed the same day — enforcing it
 * meant decoding untrusted image data in every client, a decompression-bomb
 * surface traded for a cosmetic constraint that did not serve the class's own
 * byte-bloat rationale.
 */
class InlineByteRuleTest {

    private static String b64Of(int bytes) {
        return Base64.getEncoder().encodeToString(new byte[bytes]);
    }

    private static ChatMediaPlaceholderBean inline(String previewB64) {
        return ChatMediaPlaceholderBean.builder()
                .mediaType("image/png").isTheObject(true)
                .preview(previewB64).fileName("x.png").build();
    }

    @Test
    @DisplayName("exactly at the limit is legal; one byte over is not")
    void boundaryIsExact() {
        assertEquals(InlineContentLimits.InlineVerdict.OK,
                InlineContentLimits.checkInlinePayload(b64Of(InlineContentLimits.MAX_INLINE_BYTES)));
        assertEquals(InlineContentLimits.InlineVerdict.TOO_LARGE,
                InlineContentLimits.checkInlinePayload(b64Of(InlineContentLimits.MAX_INLINE_BYTES + 1)));
    }

    @Test
    @DisplayName("⭐ the limit is on DECODED bytes, not on the base64 text")
    void limitIsOnDecodedBytes() {
        // base64 is ~1.33x the content. Checking the STRING against a 51 200
        // budget would reject a legal 38 400-byte payload; checking the DECODED
        // bytes is the only reading that matches the spec.
        String legal = b64Of(InlineContentLimits.MAX_INLINE_BYTES);
        assertTrue(legal.length() > InlineContentLimits.MAX_INLINE_BYTES,
                "the base64 text is longer than the limit — that is the trap");
        assertEquals(InlineContentLimits.InlineVerdict.OK,
                InlineContentLimits.checkInlinePayload(legal));
    }

    @Test
    @DisplayName("⭐ an oversized payload is rejected WITHOUT being decoded")
    void hostilePayloadIsRejectedOnStringLengthFirst() {
        // A payload far over the limit must not be allocated just to measure it.
        // The pre-check is on the string, so this returns without decoding ~7 MB.
        StringBuilder huge = new StringBuilder();
        huge.append("A".repeat(InlineContentLimits.MAX_INLINE_PREVIEW_B64_CHARS + 1));
        assertEquals(InlineContentLimits.InlineVerdict.TOO_LARGE,
                InlineContentLimits.checkInlinePayload(huge.toString()),
                "must reject on length alone — note this string is not even valid base64, "
                + "so reaching the decoder would have produced UNDECODABLE instead");
    }

    @Test
    @DisplayName("undecodable is its own verdict — not silently 'fine'")
    void undecodableIsDistinct() {
        assertEquals(InlineContentLimits.InlineVerdict.UNDECODABLE,
                InlineContentLimits.checkInlinePayload("!!!not base64!!!"));
        assertEquals(InlineContentLimits.InlineVerdict.UNDECODABLE,
                InlineContentLimits.checkInlinePayload(null));
        assertEquals(InlineContentLimits.InlineVerdict.UNDECODABLE,
                InlineContentLimits.checkInlinePayload(""));
    }

    @Test
    @DisplayName("⭐ a BLOB placeholder is never constrained — small-via-blob is legal")
    void blobPlaceholdersAreUnconstrained() {
        // The rule is one-directional. A blob placeholder carrying a huge preview
        // thumbnail, or none at all, passes: it is not inline delivery.
        ChatMediaPlaceholderBean blob = ChatMediaPlaceholderBean.builder()
                .mediaType("image/jpeg").isTheObject(false)
                .preview(b64Of(InlineContentLimits.MAX_INLINE_BYTES * 3))
                .encryptedFileHash("aa4529").fileName("big.jpg").build();

        assertEquals(InlineContentLimits.InlineVerdict.OK,
                InlineContentLimits.checkReceivedInline(blob));
    }

    @Test
    @DisplayName("the receiver gate rejects an oversized inline placeholder")
    void receiverGateRejectsOversizedInline() {
        assertEquals(InlineContentLimits.InlineVerdict.TOO_LARGE,
                InlineContentLimits.checkReceivedInline(
                        inline(b64Of(InlineContentLimits.MAX_INLINE_BYTES + 1))));
        assertEquals(InlineContentLimits.InlineVerdict.OK,
                InlineContentLimits.checkReceivedInline(inline(b64Of(1024))));
    }

    @Test
    @DisplayName("⭐ the rejection reason names the limit AND the actual size")
    void rejectionReasonIsActionable() {
        String preview = b64Of(InlineContentLimits.MAX_INLINE_BYTES + 500);
        String reason = InlineContentLimits.rejectionReason(
                InlineContentLimits.InlineVerdict.TOO_LARGE, preview, "holiday.png");

        // "blocked" with no number is not actionable for a user or a support case.
        assertTrue(reason.contains("holiday.png"), reason);
        assertTrue(reason.contains(String.valueOf(InlineContentLimits.MAX_INLINE_BYTES)), reason);
        assertTrue(reason.contains(String.valueOf(InlineContentLimits.MAX_INLINE_BYTES + 500)), reason);
        assertNotEquals("", reason);
    }

    @Test
    @DisplayName("the MIME allowlist is untouched by the pixel-rule removal")
    void mimeAllowlistSurvives() {
        assertTrue(InlineContentLimits.isReactionImageMimeAllowed("image/png"));
        assertTrue(InlineContentLimits.isReactionImageMimeAllowed("IMAGE/WEBP"));
        assertTrue(!InlineContentLimits.isReactionImageMimeAllowed("image/bmp"));
    }
}
