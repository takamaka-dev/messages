/*
 * Copyright 2024 AiliA SA.
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Renders secret-bearing values for {@code toString()} without disclosing them.
 *
 * <p>A secret is replaced by a classification token that answers the questions a
 * developer actually asks of a log line — is it there, is it the right shape, is it
 * the same value as over there — without ever emitting the value itself:</p>
 *
 * <table border="1">
 * <caption>Classification tokens</caption>
 * <tr><th>token</th><th>meaning</th></tr>
 * <tr><td>{@code <null>}</td><td>value is null</td></tr>
 * <tr><td>{@code <empty>}</td><td>zero length</td></tr>
 * <tr><td>{@code <blank:N>}</td><td>N characters, all whitespace</td></tr>
 * <tr><td>{@code <malformed:N fp=xxxx>}</td><td>non-blank, but wrong length or charset</td></tr>
 * <tr><td>{@code <ok:N fp=xxxx>}</td><td>expected length and charset</td></tr>
 * </table>
 *
 * <p>{@code N} is the <strong>raw</strong> length, so {@code <blank:400>} states both that the
 * value was whitespace and that it was 400 characters of it — a trimmed length alone would hide
 * the second half. Every anomaly is one grep:
 * {@code grep -E '<(null|empty|blank|malformed)'}.</p>
 *
 * <p><strong>Why two methods and no generic {@code redact(String)}.</strong> The {@code fp}
 * fingerprint is 16 bits of SHA-256. Over a high-entropy secret (a 400-character CSPRNG key)
 * it is uninvertible and discloses nothing. Over a <em>guessable</em> value it is an offline
 * verifier: an attacker guesses, hashes, and compares, confirming a hit at a 1-in-65536 false
 * positive rate. That is precisely the attack {@code conversationSalt} exists to prevent
 * (see {@code ChatCryptoUtils#generateTopicKeyBean}). The split into
 * {@link #highEntropy(String, int, Pattern)} and {@link #opaque(String, int, Pattern)} makes
 * that distinction a property of the API rather than of the caller's memory: there is no way to
 * fingerprint a value without naming it high-entropy at the call site.</p>
 *
 * <p><strong>This never throws.</strong> It is called from {@code toString()}, which is called
 * from loggers; an exception there turns a diagnostic into an outage. A digest failure degrades
 * to {@code fp=??}.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @version 1.0.0
 * @since 1.8.1
 */
public final class Redact {

    /** Token for a null value. */
    public static final String NULL = "<null>";

    /** Token for a zero-length value. */
    public static final String EMPTY = "<empty>";

    /** Alphanumeric charset shared by conversation keys and salts. */
    public static final Pattern ALNUM = Pattern.compile("[a-zA-Z0-9]+");

    /** Pass as {@code expectedLength} when the value has no fixed length. */
    public static final int ANY_LENGTH = -1;

    private Redact() {
    }

    /**
     * Classify a high-entropy secret, emitting a 16-bit fingerprint.
     *
     * <p>Use ONLY for values that cannot be guessed — a CSPRNG key of substantial length.
     * The fingerprint lets two log lines (or two clients' logs) be correlated: the same
     * {@code fp} means the same value. At the scale of one session the collision rate is
     * negligible (~0.08% at 10 distinct values, ~1.9% at 50); a collision misleads a
     * debugger, it does not disclose anything.</p>
     *
     * @param value the secret, may be null
     * @param expectedLength the length a well-formed value has, or {@link #ANY_LENGTH}
     * @param allowedCharset the charset a well-formed value matches, or null to skip the check
     * @return a classification token, never the value
     */
    public static String highEntropy(String value, int expectedLength, Pattern allowedCharset) {
        return classify(value, expectedLength, allowedCharset, true);
    }

    /**
     * Classify a secret WITHOUT emitting a fingerprint.
     *
     * <p>The default for anything whose plaintext an attacker could enumerate, and for key
     * material where a stable cross-log identifier is itself unwanted.</p>
     *
     * @param value the secret, may be null
     * @param expectedLength the length a well-formed value has, or {@link #ANY_LENGTH}
     * @param allowedCharset the charset a well-formed value matches, or null to skip the check
     * @return a classification token, never the value
     */
    public static String opaque(String value, int expectedLength, Pattern allowedCharset) {
        return classify(value, expectedLength, allowedCharset, false);
    }

    /**
     * Classify a secret of no fixed shape, without a fingerprint.
     *
     * @param value the secret, may be null
     * @return a classification token, never the value
     */
    public static String opaque(String value) {
        return classify(value, ANY_LENGTH, null, false);
    }

    private static String classify(String value, int expectedLength, Pattern allowedCharset,
            boolean withFingerprint) {
        if (value == null) {
            return NULL;
        }
        final int len = value.length();
        if (len == 0) {
            return EMPTY;
        }
        // isBlank(), not isEmpty(): an all-whitespace value is the failure mode that survives
        // TopicTitleKeyBean#validate(), so it is exactly the one a log has to be able to state.
        if (value.isBlank()) {
            return "<blank:" + len + ">";
        }
        final boolean lengthOk = expectedLength == ANY_LENGTH || len == expectedLength;
        final boolean charsetOk = allowedCharset == null || allowedCharset.matcher(value).matches();
        final String fp = withFingerprint ? " fp=" + fingerprint(value) : "";
        return (lengthOk && charsetOk ? "<ok:" : "<malformed:") + len + fp + ">";
    }

    /**
     * First 16 bits of SHA-256, as 4 hex characters.
     *
     * @param value non-null value to fingerprint
     * @return 4 hex characters, or {@code ??} if no digest is available
     */
    private static String fingerprint(String value) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return String.format("%02x%02x", digest[0], digest[1]);
        } catch (Exception ex) {
            // toString() must never throw — a logger call is not a place to fail.
            return "??";
        }
    }
}
