package io.takamaka.messages.chat.mention;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Parses mentions from message text.
 *
 * <p>Distinguishes between:</p>
 * <ul>
 *   <li>@ mentions (public): Added to citedUsers field, server sees</li>
 *   <li># mentions (private): Stay encrypted, server doesn't see</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * ParseResult result = MentionParser.parseMessage(
 *     "Hey @7tVZ...czms check #f548...bc5e",
 *     conversationMembers
 * );
 *
 * // Use result.publicMentions for citedUsers field
 * // result.privateMentions for logging only
 * }</pre>
 *
 * @since 1.3.0
 */
public class MentionParser {

    /**
     * Result of parsing mentions from message text.
     */
    @Data
    @AllArgsConstructor
    public static class ParseResult {
        /**
         * Message text with any transformations applied.
         * In current implementation, same as input (no nickname translation).
         */
        private String protocolText;

        /**
         * List of public keys from @ mentions.
         * Use this for BasicMessageRequestBean.citedUsers field.
         */
        private List<String> publicMentions;

        /**
         * List of public keys from # mentions.
         * For logging/validation only, NOT for citedUsers field.
         */
        private List<String> privateMentions;
    }

    // Regex for all supported key formats (longest first to avoid partial matches)
    private static final String KEY_PATTERN =
        "([a-zA-Z0-9-_.]{19840}|[a-zA-Z0-9]{96}|[a-zA-Z0-9-_.]{64}|[a-zA-Z0-9-_.]{44})";

    private static final Pattern PUBLIC_MENTION = Pattern.compile("@" + KEY_PATTERN);
    private static final Pattern PRIVATE_MENTION = Pattern.compile("#" + KEY_PATTERN);

    /**
     * Parses message text and extracts mentions.
     *
     * @param userText Message text with mentions
     * @param conversationMembers List of valid public keys in conversation
     * @return ParseResult with extracted mentions
     * @throws InvalidMentionException if mention is invalid or non-member
     */
    public static ParseResult parseMessage(String userText, List<String> conversationMembers)
            throws InvalidMentionException {

        if (userText == null) {
            throw new InvalidMentionException("Message text cannot be null");
        }

        if (conversationMembers == null || conversationMembers.isEmpty()) {
            throw new InvalidMentionException("Conversation members list cannot be empty");
        }

        List<String> publicMentions = new ArrayList<>();
        List<String> privateMentions = new ArrayList<>();

        // Extract @ mentions
        Matcher publicMatcher = PUBLIC_MENTION.matcher(userText);
        while (publicMatcher.find()) {
            String publicKey = publicMatcher.group(1);
            validateMention(publicKey, conversationMembers);
            if (!publicMentions.contains(publicKey)) {
                publicMentions.add(publicKey);
            }
        }

        // Extract # mentions
        Matcher privateMatcher = PRIVATE_MENTION.matcher(userText);
        while (privateMatcher.find()) {
            String publicKey = privateMatcher.group(1);
            validateMention(publicKey, conversationMembers);
            if (!privateMentions.contains(publicKey)) {
                privateMentions.add(publicKey);
            }
        }

        return new ParseResult(userText, publicMentions, privateMentions);
    }

    /**
     * Validates a mentioned public key.
     *
     * @param publicKey Public key from mention
     * @param conversationMembers Valid members list
     * @throws InvalidMentionException if validation fails
     */
    private static void validateMention(String publicKey, List<String> conversationMembers)
            throws InvalidMentionException {

        // Validate format
        if (!AddressValidation.isValidPublicKey(publicKey)) {
            String abbreviated = publicKey.length() > 20
                ? publicKey.substring(0, 20) + "..."
                : publicKey;
            throw new InvalidMentionException(
                "Invalid public key format: " + abbreviated
            );
        }

        // Validate membership
        if (!conversationMembers.contains(publicKey)) {
            String abbreviated = AddressValidation.abbreviateForDisplay(publicKey);
            throw new InvalidMentionException(
                "User not in conversation: " + abbreviated
            );
        }
    }
}
