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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TopicTitleKeyBean {

    @JsonProperty("topic_title")
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
