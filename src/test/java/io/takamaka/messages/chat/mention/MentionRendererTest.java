package io.takamaka.messages.chat.mention;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MentionRenderer.
 *
 * @since 1.3.0
 */
class MentionRendererTest {

    // Sample keys for testing
    private static final String ED25519_KEY_1 = "7tVZucubIlMg2eWX6CzOOOU1-GMPQmNFK-R4i1tczms.";
    private static final String ED25519_KEY_2 = "abc123XYZ_-.def456GHI789jkl012MNO345pqr678ST";
    private static final String SHORT_B64_KEY = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6A7B8C9D0E1F2";
    private static final String SHORT_HEX_KEY = "f54848b4c119be770f87bc5ef54848b4c119be770f87bc5ef54848b4c119be770f87bc5ef54848b4c119be770f87bc5e";
    private static final String QTESLA_KEY = "a".repeat(19840);

    @Test
    void testRenderEd25519PublicMention() {
        String text = "Hey @" + ED25519_KEY_1 + " check this";

        String result = MentionRenderer.renderForDisplay(text);

        // Ed25519 keys should remain unchanged
        assertEquals(text, result);
        assertTrue(result.contains("@" + ED25519_KEY_1));
    }

    @Test
    void testRenderEd25519PrivateMention() {
        String text = "Secret note for #" + ED25519_KEY_1;

        String result = MentionRenderer.renderForDisplay(text);

        // Ed25519 keys should remain unchanged
        assertEquals(text, result);
        assertTrue(result.contains("#" + ED25519_KEY_1));
    }

    @Test
    void testRenderShortB64Mention() {
        String text = "Message to @" + SHORT_B64_KEY;

        String result = MentionRenderer.renderForDisplay(text);

        // Short B64 keys should remain unchanged
        assertEquals(text, result);
        assertTrue(result.contains("@" + SHORT_B64_KEY));
    }

    @Test
    void testRenderShortHexMention() {
        String text = "Message to @" + SHORT_HEX_KEY;

        String result = MentionRenderer.renderForDisplay(text);

        // Short Hex keys should remain unchanged
        assertEquals(text, result);
        assertTrue(result.contains("@" + SHORT_HEX_KEY));
    }

    @Test
    void testRenderQTeslaPublicMention() {
        String text = "Hey @" + QTESLA_KEY + " check this";

        String result = MentionRenderer.renderForDisplay(text);

        // qTesla key should be abbreviated
        assertNotEquals(text, result);
        assertFalse(result.contains(QTESLA_KEY));

        // Should contain abbreviated 96-char hex hash
        assertTrue(result.startsWith("Hey @"));
        assertTrue(result.endsWith(" check this"));

        // Extract the abbreviated key
        String abbreviated = result.substring(5, result.indexOf(" check this"));
        assertEquals(96, abbreviated.length(), "qTesla key should be abbreviated to 96-char hex");

        // Verify it's hex (only alphanumeric)
        assertTrue(abbreviated.matches("[a-zA-Z0-9]{96}"));
    }

    @Test
    void testRenderQTeslaPrivateMention() {
        String text = "Secret note for #" + QTESLA_KEY;

        String result = MentionRenderer.renderForDisplay(text);

        // qTesla key should be abbreviated
        assertNotEquals(text, result);
        assertFalse(result.contains(QTESLA_KEY));

        // Should contain abbreviated 96-char hex hash
        assertTrue(result.startsWith("Secret note for #"));

        // Extract the abbreviated key (17 chars before the #, plus 1 for the #)
        String abbreviated = result.substring(17);
        assertEquals(96, abbreviated.length(), "qTesla key should be abbreviated to 96-char hex");

        // Verify it's hex (only alphanumeric)
        assertTrue(abbreviated.matches("[a-zA-Z0-9]{96}"));
    }

    @Test
    void testRenderMultipleMentions() {
        String text = "Hey @" + ED25519_KEY_1 + " and @" + ED25519_KEY_2 + " check this";

        String result = MentionRenderer.renderForDisplay(text);

        // Both Ed25519 keys should remain unchanged
        assertEquals(text, result);
        assertTrue(result.contains("@" + ED25519_KEY_1));
        assertTrue(result.contains("@" + ED25519_KEY_2));
    }

    @Test
    void testRenderMixedPublicAndPrivateMentions() {
        String text = "Public @" + ED25519_KEY_1 + " and private #" + ED25519_KEY_2;

        String result = MentionRenderer.renderForDisplay(text);

        // Both keys should remain unchanged
        assertEquals(text, result);
        assertTrue(result.contains("@" + ED25519_KEY_1));
        assertTrue(result.contains("#" + ED25519_KEY_2));
    }

    @Test
    void testRenderMixedKeyTypes() {
        String text = "Ed25519: @" + ED25519_KEY_1 +
                      " ShortB64: @" + SHORT_B64_KEY +
                      " ShortHex: @" + SHORT_HEX_KEY;

        String result = MentionRenderer.renderForDisplay(text);

        // All shorter keys should remain unchanged
        assertEquals(text, result);
        assertTrue(result.contains("@" + ED25519_KEY_1));
        assertTrue(result.contains("@" + SHORT_B64_KEY));
        assertTrue(result.contains("@" + SHORT_HEX_KEY));
    }

    @Test
    void testRenderMixedQTeslaAndOthers() {
        String text = "Hey @" + ED25519_KEY_1 + " and @" + QTESLA_KEY;

        String result = MentionRenderer.renderForDisplay(text);

        // Ed25519 should remain, qTesla should be abbreviated
        assertTrue(result.contains("@" + ED25519_KEY_1));
        assertFalse(result.contains(QTESLA_KEY));

        // Should have two @ mentions
        long atCount = result.chars().filter(ch -> ch == '@').count();
        assertEquals(2, atCount);
    }

    @Test
    void testRenderNoMentions() {
        String text = "Just a regular message with no mentions";

        String result = MentionRenderer.renderForDisplay(text);

        // Text should remain unchanged
        assertEquals(text, result);
    }

    @Test
    void testRenderNullText() {
        String result = MentionRenderer.renderForDisplay(null);
        assertNull(result);
    }

    @Test
    void testRenderEmptyText() {
        String result = MentionRenderer.renderForDisplay("");
        assertEquals("", result);
    }

    @Test
    void testRenderKeyEd25519() {
        String result = MentionRenderer.renderKey(ED25519_KEY_1);

        // Ed25519 should remain unchanged
        assertEquals(ED25519_KEY_1, result);
    }

    @Test
    void testRenderKeyQTesla() {
        String result = MentionRenderer.renderKey(QTESLA_KEY);

        // qTesla should be abbreviated to 96-char hex
        assertNotEquals(QTESLA_KEY, result);
        assertEquals(96, result.length());
        assertTrue(result.matches("[a-zA-Z0-9]{96}"));
    }

    @Test
    void testRenderKeyShortB64() {
        String result = MentionRenderer.renderKey(SHORT_B64_KEY);

        // Short B64 should remain unchanged
        assertEquals(SHORT_B64_KEY, result);
    }

    @Test
    void testRenderKeyShortHex() {
        String result = MentionRenderer.renderKey(SHORT_HEX_KEY);

        // Short Hex should remain unchanged
        assertEquals(SHORT_HEX_KEY, result);
    }

    @Test
    void testRenderKeyNull() {
        String result = MentionRenderer.renderKey(null);
        assertEquals("", result);
    }

    @Test
    void testRenderKeyEmpty() {
        String result = MentionRenderer.renderKey("");
        assertEquals("", result);
    }

    @Test
    void testRenderMentionAtStartOfMessage() {
        String text = "@" + ED25519_KEY_1 + " hello there";

        String result = MentionRenderer.renderForDisplay(text);

        assertEquals(text, result);
        assertTrue(result.contains("@" + ED25519_KEY_1));
    }

    @Test
    void testRenderMentionAtEndOfMessage() {
        String text = "Hello there @" + ED25519_KEY_1;

        String result = MentionRenderer.renderForDisplay(text);

        assertEquals(text, result);
        assertTrue(result.contains("@" + ED25519_KEY_1));
    }

    @Test
    void testRenderMultilineMessageWithMentions() {
        String text = "First line with @" + ED25519_KEY_1 + "\n" +
                      "Second line with #" + ED25519_KEY_2 + "\n" +
                      "Third line without mentions";

        String result = MentionRenderer.renderForDisplay(text);

        assertEquals(text, result);
        assertTrue(result.contains("@" + ED25519_KEY_1));
        assertTrue(result.contains("#" + ED25519_KEY_2));
    }

    @Test
    void testConsistentQTeslaAbbreviation() {
        // Same qTesla key should always produce same abbreviated form
        String text1 = "@" + QTESLA_KEY;
        String text2 = "#" + QTESLA_KEY;

        String result1 = MentionRenderer.renderForDisplay(text1);
        String result2 = MentionRenderer.renderForDisplay(text2);

        // Extract abbreviated keys
        String abbreviated1 = result1.substring(1);
        String abbreviated2 = result2.substring(1);

        // Should be identical
        assertEquals(abbreviated1, abbreviated2);
    }
}
