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

/**
 * A single soft-violation finding attached to a {@link ValidationResult}.
 *
 * @param code the canonical code (see {@link ValidationDecorationCodes})
 * @param humanReadableMessage a UI-facing description
 * @param severity canonical severity (see {@link DecorationSeverity})
 * @param affectedField optional pointer to the offending bean field
 *                      (e.g. {@code "targets[0]"}); may be {@code null}
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public record Decoration(
        String code,
        String humanReadableMessage,
        String severity,
        String affectedField) {

}
