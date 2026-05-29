/*
 * Copyright 2026 AiliA SA.
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
package io.takamaka.messages.chat.message;

import java.util.function.Function;

/**
 * Pure-data context the client supplies to {@link MessageActionValidator}.
 *
 * <p>All members are injected by the client so the validator stays free of
 * I/O and of any direct dependency on a message store or key vault.</p>
 *
 * @param conversationCreatorPk the conversation creator's public key, used
 *        for {@code CONVERSATION_CREATOR} authorization (pin / unpin); may be
 *        {@code null} when unknown (authorization checks are then skipped)
 * @param targetAuthorResolver maps a target message signature to the public
 *        key that authored it, or {@code null} when the target is not in the
 *        local cache; drives both broken-reference and {@code SELF_AUTHOR}
 *        authorization checks
 * @param embeddedDecryptor decrypts an embedded {@code original_message}
 *        envelope to its inner content bean, used only for the nested
 *        {@code share_history} check; may be {@code null} to skip that check
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public record ValidationContext(
        String conversationCreatorPk,
        Function<String, String> targetAuthorResolver,
        Function<io.takamaka.messages.chat.message.BasicMessageRequestBean, BasicMessageEncryptedContentBean> embeddedDecryptor) {

    /**
     * A context with no creator, no cache, and no embedded decryptor.
     * Suitable for plain messages and for tests that exercise only
     * format/cardinality checks.
     */
    public static ValidationContext empty() {
        return new ValidationContext(null, sig -> null, null);
    }
}
