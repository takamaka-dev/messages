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

import java.util.Locale;
import java.util.Set;

/**
 * Canonical severity strings for a soft-violation {@link Decoration}.
 *
 * <p>String-typed (not an enum) to stay consistent with the cross-platform
 * stance described in the Phase 1 plan §2.7: wire-adjacent vocabulary is
 * expressed as constants so non-Java ports do not have to map Java enum
 * semantics.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public final class DecorationSeverity {

    public static final String INFO = "INFO";
    public static final String WARN = "WARN";
    public static final String ERROR = "ERROR";

    public static final Set<String> KNOWN = Set.of(INFO, WARN, ERROR);

    public static String normalize(String raw) {
        return raw == null ? null : raw.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isKnown(String raw) {
        String n = normalize(raw);
        return n != null && KNOWN.contains(n);
    }

    private DecorationSeverity() {
    }
}
