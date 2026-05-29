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
 * H3 — no {@code client_protocol_version} is declared, yet one or more
 * v1.1+ feature fields ({@code action}, {@code targets}, {@code fw_content},
 * {@code original_message}, {@code re_shared}) are populated. An honest
 * legacy v1.0 sender emits none of these, so this is an incoherent (or
 * spoofed) legacy claim.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public class MissingVersionWithFeaturesException extends HardProtocolViolationException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "INVALID_PROTOCOL_VERSION_MISSING_WITH_FEATURES";

    public MissingVersionWithFeaturesException(String offendingValue, String conversationHash, String senderPk) {
        super(CODE, "missing client_protocol_version with v1.1+ features populated", offendingValue, conversationHash, senderPk);
    }

    public MissingVersionWithFeaturesException(String offendingValue) {
        this(offendingValue, null, null);
    }
}
