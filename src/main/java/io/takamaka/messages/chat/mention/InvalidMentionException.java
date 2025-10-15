package io.takamaka.messages.chat.mention;

/**
 * Thrown when a mention is invalid or malformed.
 *
 * <p>Possible causes:</p>
 * <ul>
 *   <li>Invalid public key format</li>
 *   <li>Mentioned user not in conversation</li>
 *   <li>Malformed mention syntax</li>
 *   <li>Null or empty input</li>
 * </ul>
 *
 * @since 1.3.0
 */
public class InvalidMentionException extends Exception {

    /**
     * Constructs a new InvalidMentionException with the specified detail message.
     *
     * @param message the detail message
     */
    public InvalidMentionException(String message) {
        super(message);
    }

    /**
     * Constructs a new InvalidMentionException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public InvalidMentionException(String message, Throwable cause) {
        super(message, cause);
    }
}
