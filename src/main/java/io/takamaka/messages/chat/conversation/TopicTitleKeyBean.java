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
package io.takamaka.messages.chat.conversation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.exception.InvalidParameterException;
import io.takamaka.messages.utils.Redact;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Bean containing conversation topic metadata with E2E encryption support.
 *
 * <p><strong>Security Note:</strong> The conversation_salt field is MANDATORY
 * for all new conversations (as of protocol v1.3). It prevents conversation
 * enumeration attacks by making topic hashes non-deterministic.</p>
 *
 * <p>This bean is encrypted and stored in SignedContentTopicBean.topicDescription.
 * Only conversation participants can decrypt it.</p>
 *
 * <p><strong>toString() is opt-in (since 1.2.1).</strong> This bean holds two secrets in
 * plaintext once decrypted, and Lombok's generated {@code toString()} printed both — a single
 * {@code log.info("{}", contextBean)} in the shell emitted the decrypted conversation key of
 * every cached conversation. Two mechanisms are at work, and it is worth knowing which does what:
 * the {@code @ToString.Include(name = "...")} methods below shadow the same-named fields (Lombok
 * drops a field when an explicitly included member claims its name), which is what redacts the
 * two secrets TODAY; {@code @ToString(onlyExplicitlyIncluded = true)} on the class is what
 * protects the field somebody adds TOMORROW — silent until opted in, rather than exposed by
 * default and relying on that person remembering to exclude it. Removing either one is a
 * regression, and {@code TopicTitleKeyBeanToStringTest} fails for both.
 * {@code symmetricKey} and {@code conversationSalt} render through {@link Redact};
 * {@code topicTitle} prints in full, matching the Dart port
 * ({@code topic_title_key_bean.dart}) and the shell's existing INFO/DEBUG conversation
 * listings.</p>
 *
 * <p>This affects {@code toString()} only. Jackson serialisation does not consult it, so the
 * wire format, the signed canonical JSON and everything stored server-side are unchanged.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @version 1.2.1
 * @since 1.0.0
 */
@Data
@ToString(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@NoArgsConstructor
public class TopicTitleKeyBean {

    /**
     * Length of a well-formed symmetric key: {@code ChatCryptoUtils#generateRandomSafeKey()}
     * default, matched by the Dart port's {@code defaultKeyLength}.
     *
     * @since 1.2.1
     */
    private static final int SYMMETRIC_KEY_LENGTH = 400;

    /**
     * Length of a well-formed conversation salt, as enforced by {@link #validate()}.
     *
     * @since 1.2.1
     */
    private static final int CONVERSATION_SALT_LENGTH = 32;

    /**
     * Conversation title, in cleartext once decrypted.
     *
     * <p>Printed in full by {@link #toString()}: it is confidential with respect to the SERVER
     * (which only ever holds {@code SHA3-256(topicTitle + conversationSalt)}), and that property
     * is enforced by encryption on the wire, not by log hygiene on a client that legitimately
     * knows its own titles. It is already logged at INFO/DEBUG by the conversation listings.</p>
     */
    @JsonProperty("topic_title")
    @ToString.Include
    private String topicTitle;

    @JsonProperty("symmetric_key")
    private String symmetricKey;

    /**
     * Cryptographically random salt for topic hash computation.
     *
     * <p><strong>Security Requirements:</strong></p>
     * <ul>
     *   <li>MANDATORY for all new conversations (prevents enumeration attacks)</li>
     *   <li>Generated client-side using SecureRandom</li>
     *   <li>32-character alphanumeric string [a-zA-Z0-9]</li>
     *   <li>Stored encrypted in topic description</li>
     *   <li>Never exposed to server in plaintext</li>
     * </ul>
     *
     * <p><strong>Usage:</strong> Combined with topic title to compute non-deterministic
     * topic hash: SHA3-256(topicTitle + conversationSalt)</p>
     *
     * @since 1.2.0
     */
    @JsonProperty("conversation_salt")
    private String conversationSalt;

    /**
     * Render {@code symmetricKey} for {@link #toString()} without disclosing it.
     *
     * <p>High-entropy (400 CSPRNG alphanumerics), so a 16-bit fingerprint is emitted: it lets
     * the same key be correlated across two clients' logs, which is the question worth asking
     * during cross-client testing, and cannot be inverted.</p>
     *
     * @return a {@link Redact} classification token
     * @since 1.2.1
     */
    @ToString.Include(name = "symmetricKey")
    private String redactedSymmetricKey() {
        return Redact.highEntropy(symmetricKey, SYMMETRIC_KEY_LENGTH, Redact.ALNUM);
    }

    /**
     * Render {@code conversationSalt} for {@link #toString()} without disclosing it.
     *
     * <p>No fingerprint. The salt's whole purpose is to stop an offline confirmation attack
     * against the guessable title, so it gets the conservative treatment; correlation is
     * already available from the key's fingerprint and from the conversation hash this bean
     * is normally mapped under.</p>
     *
     * @return a {@link Redact} classification token
     * @since 1.2.1
     */
    @ToString.Include(name = "conversationSalt")
    private String redactedConversationSalt() {
        return Redact.opaque(conversationSalt, CONVERSATION_SALT_LENGTH, Redact.ALNUM);
    }

    /**
     * Validate that this bean has all required security fields.
     *
     * <p>This method ensures that the bean meets E2E encryption protocol requirements.
     * Missing or invalid salt is considered a critical security bug that exposes
     * conversation titles to enumeration attacks.</p>
     *
     * @throws InvalidParameterException if validation fails
     * @since 1.2.0
     */
    public void validate() throws InvalidParameterException {
        if (topicTitle == null || topicTitle.isEmpty()) {
            throw new InvalidParameterException("topic_title is required");
        }
        if (symmetricKey == null || symmetricKey.isEmpty()) {
            throw new InvalidParameterException("symmetric_key is required");
        }
        if (conversationSalt == null || conversationSalt.isEmpty()) {
            throw new InvalidParameterException(
                "conversation_salt is required (security requirement - prevents enumeration attacks). " +
                "Missing salt indicates a critical implementation bug."
            );
        }
        if (conversationSalt.length() != 32) {
            throw new InvalidParameterException(
                "conversation_salt must be exactly 32 characters, got " + conversationSalt.length()
            );
        }
        // Validate salt format (alphanumeric only)
        if (!conversationSalt.matches("[a-zA-Z0-9]+")) {
            throw new InvalidParameterException(
                "conversation_salt must contain only alphanumeric characters [a-zA-Z0-9]"
            );
        }
    }
}
