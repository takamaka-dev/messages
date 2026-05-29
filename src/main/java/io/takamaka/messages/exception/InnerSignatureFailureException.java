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
 * H6 — a {@code share_history} message embeds an {@code original_message}
 * whose inner Ed25519 signature fails verification. Because share_history
 * delivers a cryptographically verifiable original (unlike forward, which
 * carries an unverifiable claim), a broken inner signature is a hard
 * data-integrity failure and the message is dropped (Phase 1 plan §3.3.5,
 * spec §13 decision 11).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public class InnerSignatureFailureException extends HardProtocolViolationException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "INNER_SIGNATURE_FAILURE";

    public InnerSignatureFailureException(String offendingValue, String conversationHash, String senderPk) {
        super(CODE, "embedded original_message inner signature failed verification", offendingValue, conversationHash, senderPk);
    }

    public InnerSignatureFailureException(String offendingValue) {
        this(offendingValue, null, null);
    }
}
