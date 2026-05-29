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
 * H4 — the message declares a legacy ({@code 1.0}) protocol version yet
 * populates one or more v1.1+ feature fields. A legacy sender cannot, by
 * definition, emit v1.1 features; the combination is incoherent.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public class LegacyVersionWithFeaturesException extends HardProtocolViolationException {

    private static final long serialVersionUID = 1L;

    public static final String CODE = "INVALID_PROTOCOL_VERSION_LEGACY_WITH_FEATURES";

    public LegacyVersionWithFeaturesException(String offendingValue, String conversationHash, String senderPk) {
        super(CODE, "legacy version " + offendingValue + " declared with v1.1+ features populated", offendingValue, conversationHash, senderPk);
    }

    public LegacyVersionWithFeaturesException(String offendingValue) {
        this(offendingValue, null, null);
    }
}
