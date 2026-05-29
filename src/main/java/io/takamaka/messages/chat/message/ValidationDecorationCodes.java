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

import java.util.Set;

/**
 * Canonical soft-violation decoration codes (Phase 1 plan §3.3.3).
 *
 * <p>These codes appear in a {@link Decoration} attached to a
 * {@link ValidationResult}; the message body still renders. Hard-violation
 * codes ({@code INVALID_PROTOCOL_VERSION_*}, {@code INVALID_ACTION},
 * {@code INNER_SIGNATURE_FAILURE}) are intentionally absent here — they live
 * on the {@code HardProtocolViolationException} subclasses, since a hard
 * violation never produces a {@code ValidationResult}.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public final class ValidationDecorationCodes {

    // Cardinality / target count
    public static final String INVALID_REPLY_MALFORMED_TARGETS = "INVALID_REPLY_MALFORMED_TARGETS";
    public static final String INVALID_REACTION_MALFORMED_TARGETS = "INVALID_REACTION_MALFORMED_TARGETS";
    public static final String INVALID_REACTION_REMOVE_MALFORMED_TARGETS = "INVALID_REACTION_REMOVE_MALFORMED_TARGETS";
    public static final String INVALID_EDIT_MALFORMED_TARGETS = "INVALID_EDIT_MALFORMED_TARGETS";
    public static final String INVALID_REDACT_MALFORMED_TARGETS = "INVALID_REDACT_MALFORMED_TARGETS";
    public static final String INVALID_PIN_MALFORMED_TARGETS = "INVALID_PIN_MALFORMED_TARGETS";
    public static final String INVALID_UNPIN_MALFORMED_TARGETS = "INVALID_UNPIN_MALFORMED_TARGETS";
    public static final String INVALID_FORWARD_MALFORMED_TARGETS = "INVALID_FORWARD_MALFORMED_TARGETS";
    public static final String INVALID_SHARE_HISTORY_MALFORMED_TARGETS = "INVALID_SHARE_HISTORY_MALFORMED_TARGETS";

    // Target format — signature-typed
    public static final String INVALID_REPLY_BAD_SIGNATURE_FORMAT = "INVALID_REPLY_BAD_SIGNATURE_FORMAT";
    public static final String INVALID_REACTION_BAD_SIGNATURE_FORMAT = "INVALID_REACTION_BAD_SIGNATURE_FORMAT";
    public static final String INVALID_REACTION_REMOVE_BAD_SIGNATURE_FORMAT = "INVALID_REACTION_REMOVE_BAD_SIGNATURE_FORMAT";
    public static final String INVALID_EDIT_BAD_SIGNATURE_FORMAT = "INVALID_EDIT_BAD_SIGNATURE_FORMAT";
    public static final String INVALID_REDACT_BAD_SIGNATURE_FORMAT = "INVALID_REDACT_BAD_SIGNATURE_FORMAT";
    public static final String INVALID_PIN_BAD_SIGNATURE_FORMAT = "INVALID_PIN_BAD_SIGNATURE_FORMAT";

    // Target format — public-key-typed
    public static final String INVALID_FORWARD_BAD_PUBLIC_KEY_FORMAT = "INVALID_FORWARD_BAD_PUBLIC_KEY_FORMAT";

    // Broken references (target not in local cache)
    public static final String BROKEN_REFERENCE_REPLY = "BROKEN_REFERENCE_REPLY";
    public static final String BROKEN_REFERENCE_REACTION = "BROKEN_REFERENCE_REACTION";
    public static final String BROKEN_REFERENCE_PIN = "BROKEN_REFERENCE_PIN";

    // Authorization failures
    public static final String UNAUTHORIZED_EDIT = "UNAUTHORIZED_EDIT";
    public static final String UNAUTHORIZED_REDACT = "UNAUTHORIZED_REDACT";
    public static final String UNAUTHORIZED_REACTION_REMOVE = "UNAUTHORIZED_REACTION_REMOVE";
    public static final String UNAUTHORIZED_PIN = "UNAUTHORIZED_PIN";
    public static final String UNAUTHORIZED_UNPIN = "UNAUTHORIZED_UNPIN";

    // share_history structural soft cases
    public static final String INVALID_SHARE_HISTORY_MISSING_ORIGINAL_MESSAGE = "INVALID_SHARE_HISTORY_MISSING_ORIGINAL_MESSAGE";
    public static final String INVALID_SHARE_HISTORY_CROSS_CONVERSATION = "INVALID_SHARE_HISTORY_CROSS_CONVERSATION"; // severity ERROR
    public static final String INVALID_SHARE_HISTORY_NESTED = "INVALID_SHARE_HISTORY_NESTED";

    // Forward
    public static final String FORWARD_DEPTH_EXCEEDED = "FORWARD_DEPTH_EXCEEDED";

    // Inline content
    public static final String INLINE_DECODE_ERROR = "INLINE_DECODE_ERROR";
    public static final String INLINE_SIZE_VIOLATION = "INLINE_SIZE_VIOLATION";
    public static final String INLINE_HASH_MISMATCH = "INLINE_HASH_MISMATCH"; // severity ERROR
    public static final String INLINE_DIMENSION_VIOLATION = "INLINE_DIMENSION_VIOLATION";
    public static final String INLINE_SIZE_MISMATCH = "INLINE_SIZE_MISMATCH";
    public static final String INLINE_FIELD_VIOLATION = "INLINE_FIELD_VIOLATION";
    public static final String INLINE_MIME_VIOLATION = "INLINE_MIME_VIOLATION";

    // Reaction SHOULD-rules
    public static final String REACTION_TEXT_NOT_EMPTY = "REACTION_TEXT_NOT_EMPTY";

    /** Closed set for completeness tests. */
    public static final Set<String> ALL = Set.of(
            INVALID_REPLY_MALFORMED_TARGETS,
            INVALID_REACTION_MALFORMED_TARGETS,
            INVALID_REACTION_REMOVE_MALFORMED_TARGETS,
            INVALID_EDIT_MALFORMED_TARGETS,
            INVALID_REDACT_MALFORMED_TARGETS,
            INVALID_PIN_MALFORMED_TARGETS,
            INVALID_UNPIN_MALFORMED_TARGETS,
            INVALID_FORWARD_MALFORMED_TARGETS,
            INVALID_SHARE_HISTORY_MALFORMED_TARGETS,
            INVALID_REPLY_BAD_SIGNATURE_FORMAT,
            INVALID_REACTION_BAD_SIGNATURE_FORMAT,
            INVALID_REACTION_REMOVE_BAD_SIGNATURE_FORMAT,
            INVALID_EDIT_BAD_SIGNATURE_FORMAT,
            INVALID_REDACT_BAD_SIGNATURE_FORMAT,
            INVALID_PIN_BAD_SIGNATURE_FORMAT,
            INVALID_FORWARD_BAD_PUBLIC_KEY_FORMAT,
            BROKEN_REFERENCE_REPLY,
            BROKEN_REFERENCE_REACTION,
            BROKEN_REFERENCE_PIN,
            UNAUTHORIZED_EDIT,
            UNAUTHORIZED_REDACT,
            UNAUTHORIZED_REACTION_REMOVE,
            UNAUTHORIZED_PIN,
            UNAUTHORIZED_UNPIN,
            INVALID_SHARE_HISTORY_MISSING_ORIGINAL_MESSAGE,
            INVALID_SHARE_HISTORY_CROSS_CONVERSATION,
            INVALID_SHARE_HISTORY_NESTED,
            FORWARD_DEPTH_EXCEEDED,
            INLINE_DECODE_ERROR,
            INLINE_SIZE_VIOLATION,
            INLINE_HASH_MISMATCH,
            INLINE_DIMENSION_VIOLATION,
            INLINE_SIZE_MISMATCH,
            INLINE_FIELD_VIOLATION,
            INLINE_MIME_VIOLATION,
            REACTION_TEXT_NOT_EMPTY
    );

    private ValidationDecorationCodes() {
    }
}
