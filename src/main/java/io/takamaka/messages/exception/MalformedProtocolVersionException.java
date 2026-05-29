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
 * H1 — the {@code client_protocol_version} field is present but does not
 * match the strict {@code MAJOR.MINOR} format.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public class MalformedProtocolVersionException extends HardProtocolViolationException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "INVALID_PROTOCOL_VERSION_MALFORMED";

    public MalformedProtocolVersionException(String offendingValue, String conversationHash, String senderPk) {
        super(CODE, "malformed client_protocol_version: " + offendingValue, offendingValue, conversationHash, senderPk);
    }

    public MalformedProtocolVersionException(String offendingValue) {
        this(offendingValue, null, null);
    }
}
