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
 * Closed registry of message-action wire-strings.
 *
 * <p>This is a <strong>constants holder, not an enum</strong>. The action
 * value travels on the wire as a plain lowercase string; Java enums
 * interoperate poorly with non-Java ports (Dart, Python, Rust, Go,
 * JavaScript all have divergent enum semantics), so the canonical form is
 * a {@code public static final String} on every platform. See the
 * message-actions spec §2.7 and the Phase 1 plan §2.7 for the rationale.</p>
 *
 * <p><strong>Canonical wire form:</strong> lowercase strings. Inbound
 * values are normalized with {@code trim() + Locale.ROOT.toLowerCase()}
 * before comparison against {@link #KNOWN}; outbound values are emitted in
 * the canonical lowercase form verbatim.</p>
 *
 * <p>Cross-platform note: the Dart port mirrors these with
 * {@code static const String reply = 'reply'} etc. Dart's
 * {@code String.toLowerCase()} is locale-independent by default, so it is
 * safe to use without a {@code Locale.ROOT} equivalent.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public final class MessageAction {

    public static final String REPLY = "reply";
    public static final String REACTION = "reaction";
    public static final String REACTION_REMOVE = "reaction_remove";
    public static final String EDIT = "edit";
    public static final String REDACT = "redact";
    public static final String PIN = "pin";
    public static final String UNPIN = "unpin";
    public static final String FORWARD = "forward";
    public static final String SHARE_HISTORY = "share_history";

    /**
     * Closed registry of recognized actions.
     */
    public static final Set<String> KNOWN = Set.of(
            REPLY, REACTION, REACTION_REMOVE, EDIT, REDACT,
            PIN, UNPIN, FORWARD, SHARE_HISTORY
    );

    /**
     * Normalize an inbound action string for comparison.
     *
     * <p>Returns {@code null} for null, empty, or whitespace-only inputs
     * (per spec §12.7 "null is different from invalid" — three input forms
     * are equivalent to "no action set": null, {@code ""}, and
     * whitespace-only). Otherwise trims and lowercases via
     * {@link Locale#ROOT}.</p>
     *
     * @param raw the raw inbound action value; may be {@code null}
     * @return the normalized lowercase action, or {@code null} for absent
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * Case-insensitive membership check against the closed registry.
     *
     * @param raw the raw inbound action value; may be {@code null}
     * @return {@code true} iff {@code raw} normalizes to a recognized action
     */
    public static boolean isKnown(String raw) {
        String n = normalize(raw);
        return n != null && KNOWN.contains(n);
    }

    private MessageAction() {
    }
}
