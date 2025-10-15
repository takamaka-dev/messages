package io.takamaka.messages.chat.mention;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Renders mentions in message text for display purposes.
 *
 * <p>Abbreviates long public keys (qTesla) while preserving shorter keys.
 * Handles both @ (public) and # (private) mention syntax.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * String displayText = MentionRenderer.renderForDisplay(
 *     "Hey @7tVZ...czms check @aaaaa...aaaaa"
 * );
 * // Returns: "Hey @7tVZ...czms check @f548...bc5e"
 * // (qTesla key abbreviated to 96-char hex hash)
 * }</pre>
 *
 * @since 1.3.0
 */
public class MentionRenderer {

    // Regex for all supported key formats (longest first to avoid partial matches)
    private static final String KEY_PATTERN =
        "([a-zA-Z0-9-_.]{19840}|[a-zA-Z0-9]{96}|[a-zA-Z0-9-_.]{64}|[a-zA-Z0-9-_.]{44})";

    private static final Pattern PUBLIC_MENTION = Pattern.compile("@" + KEY_PATTERN);
    private static final Pattern PRIVATE_MENTION = Pattern.compile("#" + KEY_PATTERN);

    /**
     * Renders message text with abbreviated mentions for display.
     *
     * <p>Behavior:</p>
     * <ul>
     *   <li>Ed25519/Short keys: Display in full (readable length)</li>
     *   <li>qTesla keys: Display as 96-char hex hash (SHA3-384)</li>
     *   <li>Both @ and # mentions are processed</li>
     * </ul>
     *
     * @param messageText Raw message text with mentions
     * @return Text with abbreviated mentions suitable for display
     */
    public static String renderForDisplay(String messageText) {
        if (messageText == null || messageText.isEmpty()) {
            return messageText;
        }

        String result = messageText;

        // Abbreviate @ mentions
        result = abbreviateMentions(result, PUBLIC_MENTION);

        // Abbreviate # mentions
        result = abbreviateMentions(result, PRIVATE_MENTION);

        return result;
    }

    /**
     * Abbreviates mentions matching the given pattern.
     *
     * @param text Message text
     * @param pattern Mention pattern (public or private)
     * @return Text with abbreviated mentions
     */
    private static String abbreviateMentions(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String fullKey = matcher.group(1);
            String abbreviated = AddressValidation.abbreviateForDisplay(fullKey);

            // Escape special regex characters in the replacement
            String replacement = Matcher.quoteReplacement(
                matcher.group(0).charAt(0) + abbreviated
            );

            matcher.appendReplacement(result, replacement);
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Renders a single public key for display.
     *
     * <p>Convenience method for displaying individual keys without mention syntax.</p>
     *
     * @param publicKey Full public key
     * @return Abbreviated key suitable for display
     */
    public static String renderKey(String publicKey) {
        return AddressValidation.abbreviateForDisplay(publicKey);
    }
}
