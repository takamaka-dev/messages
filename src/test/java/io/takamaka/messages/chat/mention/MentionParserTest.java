package io.takamaka.messages.chat.mention;

import io.takamaka.messages.chat.mention.MentionParser.ParseResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MentionParser.
 *
 * @since 1.3.0
 */
class MentionParserTest {

    // Sample keys for testing
    private static final String ED25519_KEY_1 = "7tVZucubIlMg2eWX6CzOOOU1-GMPQmNFK-R4i1tczms.";
    private static final String ED25519_KEY_2 = "abc123XYZ_-.def456GHI789jkl012MNO345pqr678ST";
    private static final String SHORT_B64_KEY = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6A7B8C9D0E1F2";
    private static final String SHORT_HEX_KEY = "f54848b4c119be770f87bc5ef54848b4c119be770f87bc5ef54848b4c119be770f87bc5ef54848b4c119be770f87bc5e";
    private static final String QTESLA_KEY = "a".repeat(19840);

    private static final List<String> CONVERSATION_MEMBERS = Arrays.asList(
        ED25519_KEY_1,
        ED25519_KEY_2,
        SHORT_B64_KEY,
        SHORT_HEX_KEY,
        QTESLA_KEY
    );

    @Test
    void testSinglePublicMention() throws InvalidMentionException {
        String text = "Hey @" + ED25519_KEY_1 + " check this out";

        ParseResult result = MentionParser.parseMessage(text, CONVERSATION_MEMBERS);

        assertEquals(text, result.getProtocolText());
        assertEquals(1, result.getPublicMentions().size());
        assertEquals(ED25519_KEY_1, result.getPublicMentions().get(0));
        assertEquals(0, result.getPrivateMentions().size());
    }

    @Test
    void testSinglePrivateMention() throws InvalidMentionException {
        String text = "Secret note for #" + ED25519_KEY_1;

        ParseResult result = MentionParser.parseMessage(text, CONVERSATION_MEMBERS);

        assertEquals(text, result.getProtocolText());
        assertEquals(0, result.getPublicMentions().size());
        assertEquals(1, result.getPrivateMentions().size());
        assertEquals(ED25519_KEY_1, result.getPrivateMentions().get(0));
    }

    @Test
    void testMultiplePublicMentions() throws InvalidMentionException {
        String text = "Hey @" + ED25519_KEY_1 + " and @" + ED25519_KEY_2 + " check this";

        ParseResult result = MentionParser.parseMessage(text, CONVERSATION_MEMBERS);

        assertEquals(2, result.getPublicMentions().size());
        assertTrue(result.getPublicMentions().contains(ED25519_KEY_1));
        assertTrue(result.getPublicMentions().contains(ED25519_KEY_2));
        assertEquals(0, result.getPrivateMentions().size());
    }

    @Test
    void testMultiplePrivateMentions() throws InvalidMentionException {
        String text = "Note for #" + ED25519_KEY_1 + " and #" + ED25519_KEY_2;

        ParseResult result = MentionParser.parseMessage(text, CONVERSATION_MEMBERS);

        assertEquals(0, result.getPublicMentions().size());
        assertEquals(2, result.getPrivateMentions().size());
        assertTrue(result.getPrivateMentions().contains(ED25519_KEY_1));
        assertTrue(result.getPrivateMentions().contains(ED25519_KEY_2));
    }

    @Test
    void testMixedPublicAndPrivateMentions() throws InvalidMentionException {
        String text = "Public @" + ED25519_KEY_1 + " and private #" + ED25519_KEY_2;

        ParseResult result = MentionParser.parseMessage(text, CONVERSATION_MEMBERS);

        assertEquals(1, result.getPublicMentions().size());
        assertEquals(ED25519_KEY_1, result.getPublicMentions().get(0));
        assertEquals(1, result.getPrivateMentions().size());
        assertEquals(ED25519_KEY_2, result.getPrivateMentions().get(0));
    }

    @Test
    void testDuplicatePublicMentions() throws InvalidMentionException {
        String text = "Hey @" + ED25519_KEY_1 + " and @" + ED25519_KEY_1 + " again";

        ParseResult result = MentionParser.parseMessage(text, CONVERSATION_MEMBERS);

        // Should deduplicate
        assertEquals(1, result.getPublicMentions().size());
        assertEquals(ED25519_KEY_1, result.getPublicMentions().get(0));
    }

    @Test
    void testDuplicatePrivateMentions() throws InvalidMentionException {
        String text = "Note #" + ED25519_KEY_1 + " and #" + ED25519_KEY_1;

        ParseResult result = MentionParser.parseMessage(text, CONVERSATION_MEMBERS);

        // Should deduplicate
        assertEquals(1, result.getPrivateMentions().size());
        assertEquals(ED25519_KEY_1, result.getPrivateMentions().get(0));
    }

    @Test
    void testAllKeyFormats() throws InvalidMentionException {
        String text = "Ed25519: @" + ED25519_KEY_1 +
                      " ShortB64: @" + SHORT_B64_KEY +
                      " ShortHex: @" + SHORT_HEX_KEY +
                      " qTesla: @" + QTESLA_KEY;

        ParseResult result = MentionParser.parseMessage(text, CONVERSATION_MEMBERS);

        assertEquals(4, result.getPublicMentions().size());
        assertTrue(result.getPublicMentions().contains(ED25519_KEY_1));
        assertTrue(result.getPublicMentions().contains(SHORT_B64_KEY));
        assertTrue(result.getPublicMentions().contains(SHORT_HEX_KEY));
        assertTrue(result.getPublicMentions().contains(QTESLA_KEY));
    }

    @Test
    void testNoMentions() throws InvalidMentionException {
        String text = "Just a regular message with no mentions";

        ParseResult result = MentionParser.parseMessage(text, CONVERSATION_MEMBERS);

        assertEquals(text, result.getProtocolText());
        assertEquals(0, result.getPublicMentions().size());
        assertEquals(0, result.getPrivateMentions().size());
    }

    @Test
    void testInvalidPublicKeyFormat() throws InvalidMentionException {
        // Keys with invalid characters won't match the regex, so no exception is thrown
        // They're simply ignored as not being valid mention syntax
        String invalidKey = "invalid!@#$%";
        String text = "Hey @" + invalidKey + " there";

        ParseResult result = MentionParser.parseMessage(text, CONVERSATION_MEMBERS);

        // No mentions should be extracted since the key doesn't match the pattern
        assertEquals(0, result.getPublicMentions().size());
        assertEquals(0, result.getPrivateMentions().size());
    }

    @Test
    void testNonMemberMention() {
        // Valid 44-char Ed25519 format key but not in conversation members
        String nonMemberKey = "1234567890123456789012345678901234567890ABCD";
        String text = "Hey @" + nonMemberKey;

        InvalidMentionException exception = assertThrows(
            InvalidMentionException.class,
            () -> MentionParser.parseMessage(text, CONVERSATION_MEMBERS)
        );

        assertTrue(exception.getMessage().contains("User not in conversation"));
    }

    @Test
    void testNullMessageText() {
        InvalidMentionException exception = assertThrows(
            InvalidMentionException.class,
            () -> MentionParser.parseMessage(null, CONVERSATION_MEMBERS)
        );

        assertTrue(exception.getMessage().contains("Message text cannot be null"));
    }

    @Test
    void testNullConversationMembers() {
        String text = "Hey @" + ED25519_KEY_1;

        InvalidMentionException exception = assertThrows(
            InvalidMentionException.class,
            () -> MentionParser.parseMessage(text, null)
        );

        assertTrue(exception.getMessage().contains("Conversation members list cannot be empty"));
    }

    @Test
    void testEmptyConversationMembers() {
        String text = "Hey @" + ED25519_KEY_1;

        InvalidMentionException exception = assertThrows(
            InvalidMentionException.class,
            () -> MentionParser.parseMessage(text, Arrays.asList())
        );

        assertTrue(exception.getMessage().contains("Conversation members list cannot be empty"));
    }

    @Test
    void testMentionAtStartOfMessage() throws InvalidMentionException {
        String text = "@" + ED25519_KEY_1 + " hello there";

        ParseResult result = MentionParser.parseMessage(text, CONVERSATION_MEMBERS);

        assertEquals(1, result.getPublicMentions().size());
        assertEquals(ED25519_KEY_1, result.getPublicMentions().get(0));
    }

    @Test
    void testMentionAtEndOfMessage() throws InvalidMentionException {
        String text = "Hello there @" + ED25519_KEY_1;

        ParseResult result = MentionParser.parseMessage(text, CONVERSATION_MEMBERS);

        assertEquals(1, result.getPublicMentions().size());
        assertEquals(ED25519_KEY_1, result.getPublicMentions().get(0));
    }

    @Test
    void testMultilineMessageWithMentions() throws InvalidMentionException {
        String text = "First line with @" + ED25519_KEY_1 + "\n" +
                      "Second line with #" + ED25519_KEY_2 + "\n" +
                      "Third line without mentions";

        ParseResult result = MentionParser.parseMessage(text, CONVERSATION_MEMBERS);

        assertEquals(1, result.getPublicMentions().size());
        assertEquals(ED25519_KEY_1, result.getPublicMentions().get(0));
        assertEquals(1, result.getPrivateMentions().size());
        assertEquals(ED25519_KEY_2, result.getPrivateMentions().get(0));
    }
}
