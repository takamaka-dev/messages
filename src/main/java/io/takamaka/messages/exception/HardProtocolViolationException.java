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
 * Base type for the six "hard" message-action protocol violations (H1-H6).
 *
 * <p>A hard violation means the receiver cannot trust the bean's identity
 * at all: the message MUST be dropped (not rendered). This is distinct from
 * the soft-violation path, where the body still renders with attached
 * decorations (see {@code ValidationResult} / {@code Decoration}).</p>
 *
 * <p>Each subclass exposes a canonical {@link #getCode() code} string (the
 * H-list code). The exception also carries the offending value and optional
 * routing context (conversation hash, sender public key) for logging and
 * telemetry. These codes are deliberately NOT part of
 * {@code ValidationDecorationCodes} — they never appear in a
 * {@code ValidationResult}.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public class HardProtocolViolationException extends ChatMessageException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final String offendingValue;
    private final String conversationHash;
    private final String senderPk;

    public HardProtocolViolationException(String code, String message,
            String offendingValue, String conversationHash, String senderPk) {
        super(message);
        this.code = code;
        this.offendingValue = offendingValue;
        this.conversationHash = conversationHash;
        this.senderPk = senderPk;
    }

    public HardProtocolViolationException(String code, String message, String offendingValue) {
        this(code, message, offendingValue, null, null);
    }

    public String getCode() {
        return code;
    }

    public String getOffendingValue() {
        return offendingValue;
    }

    public String getConversationHash() {
        return conversationHash;
    }

    public String getSenderPk() {
        return senderPk;
    }
}
