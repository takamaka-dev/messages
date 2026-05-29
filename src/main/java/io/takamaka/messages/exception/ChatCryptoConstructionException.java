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
package io.takamaka.messages.exception;

/**
 * Base type for sender-side ({@code ChatCryptoUtils.get*MessageBean})
 * construction errors. Each instance carries a canonical {@link #getCode()
 * code} string (see the public {@code public static final String} constants)
 * so callers and UIs can branch on the failure without string-matching the
 * message.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public class ChatCryptoConstructionException extends ChatMessageException {

    private static final long serialVersionUID = 1L;

    // Missing required inputs
    public static final String MISSING_PARENT_SIGNATURE = "MISSING_PARENT_SIGNATURE";
    public static final String MISSING_ORIGINAL_ENVELOPE = "MISSING_ORIGINAL_ENVELOPE";
    public static final String MISSING_TARGET_MESSAGE_SIGNATURE = "MISSING_TARGET_MESSAGE_SIGNATURE";
    public static final String MISSING_REACTION_PAYLOAD = "MISSING_REACTION_PAYLOAD";

    // Malformed targets
    public static final String MALFORMED_PARENT_SIGNATURE = "MALFORMED_PARENT_SIGNATURE";
    public static final String MALFORMED_CLAIMED_ORIGIN_PK = "MALFORMED_CLAIMED_ORIGIN_PK";
    public static final String MALFORMED_TARGET_MESSAGE_SIGNATURE = "MALFORMED_TARGET_MESSAGE_SIGNATURE";

    // Forward
    public static final String FORWARD_DEPTH_EXCEEDED = "FORWARD_DEPTH_EXCEEDED";

    // share_history embedding
    public static final String EMBEDDED_INNER_SIGNATURE_INVALID = "EMBEDDED_INNER_SIGNATURE_INVALID";
    public static final String EMBEDDED_INNER_CONVERSATION_MISMATCH = "EMBEDDED_INNER_CONVERSATION_MISMATCH";
    public static final String NESTED_SHARE_HISTORY = "NESTED_SHARE_HISTORY";

    // Inline content
    public static final String INLINE_CONTENT_TOO_LARGE = "INLINE_CONTENT_TOO_LARGE";
    public static final String INLINE_CONTENT_DIMENSION_VIOLATION = "INLINE_CONTENT_DIMENSION_VIOLATION";
    public static final String INLINE_DECODE_FAILURE = "INLINE_DECODE_FAILURE";
    public static final String REACTION_MIME_NOT_ALLOWED = "REACTION_MIME_NOT_ALLOWED";

    // Warnings (not thrown)
    public static final String PIN_REASON_TOO_LONG = "PIN_REASON_TOO_LONG";

    // Catch-all
    public static final String INCOHERENT_BEAN_CONSTRUCTION = "INCOHERENT_BEAN_CONSTRUCTION";

    private final String code;

    public ChatCryptoConstructionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ChatCryptoConstructionException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
