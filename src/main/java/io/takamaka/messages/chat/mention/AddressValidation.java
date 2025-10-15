package io.takamaka.messages.chat.mention;

import io.takamaka.wallet.utils.TkmSignUtils;

/**
 * Validates public key formats for mention syntax.
 *
 * Supports Ed25519, qTesla, Short B64, and Short Hex formats per AddressRegex.md.
 * Used by MentionParser to validate mentions before message creation.
 *
 * @since 1.3.0
 */
public class AddressValidation {

    /**
     * Supported public key types with their characteristics.
     */
    public enum KeyType {
        ED25519(44, "^[a-zA-Z0-9-_.]{44}$"),
        SHORT_B64(64, "^[a-zA-Z0-9-_.]{64}$"),
        SHORT_HEX(96, "^[a-zA-Z0-9]{96}$"),
        QTESLA(19840, "^[a-zA-Z0-9-_.]{19840}$"),
        INVALID(0, "");

        private final int length;
        private final String regex;

        KeyType(int length, String regex) {
            this.length = length;
            this.regex = regex;
        }

        public int getLength() {
            return length;
        }

        public String getRegex() {
            return regex;
        }
    }

    /**
     * Validates if string is a valid public key format.
     *
     * @param key Public key string to validate
     * @return true if valid format, false otherwise
     */
    public static boolean isValidPublicKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }

        for (KeyType type : KeyType.values()) {
            if (type == KeyType.INVALID) {
                continue;
            }
            if (key.matches(type.getRegex())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines the key type from public key string.
     *
     * @param key Public key string
     * @return KeyType enum (ED25519, QTESLA, SHORT_B64, SHORT_HEX, or INVALID)
     */
    public static KeyType getKeyType(String key) {
        if (key == null || key.isEmpty()) {
            return KeyType.INVALID;
        }

        for (KeyType type : KeyType.values()) {
            if (type == KeyType.INVALID) {
                continue;
            }
            if (key.matches(type.getRegex())) {
                return type;
            }
        }

        return KeyType.INVALID;
    }

    /**
     * Abbreviates public key for display.
     *
     * <p>Behavior:</p>
     * <ul>
     *   <li>Ed25519/Short formats: Returns full key (readable length)</li>
     *   <li>qTesla (19,840 chars): Returns 96-char hex hash (SHA3-384)</li>
     * </ul>
     *
     * @param key Full public key
     * @return Abbreviated string suitable for display
     */
    public static String abbreviateForDisplay(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        KeyType type = getKeyType(key);

        if (type == KeyType.QTESLA) {
            try {
                // Compute SHA3-384 hash and convert to hex (96 chars)
                String b64Hash = TkmSignUtils.Hash384B64URL(key);
                return TkmSignUtils.fromB64UrlToHEX(b64Hash);
            } catch (Exception e) {
                // Fallback: truncate with ellipsis
                return key.substring(0, 20) + "..." + key.substring(key.length() - 20);
            }
        }

        // Ed25519, Short B64, Short Hex: return full key
        return key;
    }
}
