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

import java.util.List;

/**
 * Outcome of a soft (non-fatal) validation pass.
 *
 * <p>{@code overallValid} is derived: it is {@code true} iff
 * {@code decorations} carries no {@code ERROR}-severity entry. Callers may
 * either branch on {@code overallValid} or iterate {@code decorations} for
 * fine-grained handling (logging, telemetry, per-decoration UI).</p>
 *
 * @param overallValid {@code true} iff no decoration has severity ERROR
 * @param decorations possibly-empty, immutable list of findings
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public record ValidationResult(
        boolean overallValid,
        List<Decoration> decorations) {

    private static final ValidationResult EMPTY = new ValidationResult(true, List.of());

    /**
     * @return a valid result with no decorations (plain-message path)
     */
    public static ValidationResult empty() {
        return EMPTY;
    }

    public boolean hasErrors() {
        return decorations.stream()
                .anyMatch(d -> DecorationSeverity.ERROR.equals(d.severity()));
    }
}
