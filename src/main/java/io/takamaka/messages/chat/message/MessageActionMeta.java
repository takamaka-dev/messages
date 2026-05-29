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

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Per-action metadata registry keyed by the lowercase action string.
 *
 * <p>Carries the validation-relevant shape of each action: how many
 * targets it expects, what format those targets take, and which
 * authorization pattern applies. {@link MessageActionValidator} dispatches
 * off this registry.</p>
 *
 * <p>The three small enums ({@link Cardinality}, {@link TargetFormat},
 * {@link AuthPattern}) are <strong>internal-only</strong> — they never
 * appear on the wire and never travel between language ports. Cross-platform
 * interop happens solely through the string action value
 * ({@link MessageAction}). Each port may represent these internal enums as
 * constants or sealed classes without protocol consequence.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public final class MessageActionMeta {

    public enum Cardinality {
        ZERO, ONE, ZERO_OR_ONE
    }

    public enum TargetFormat {
        NONE, SIGNATURE, PUBLIC_KEY
    }

    public enum AuthPattern {
        NO_CHECK, SELF_AUTHOR, CONVERSATION_CREATOR
    }

    public record ActionSpec(Cardinality cardinality,
            TargetFormat targetFormat,
            AuthPattern authPattern) {

    }

    private static final Map<String, ActionSpec> REGISTRY = Map.of(
            MessageAction.REPLY, new ActionSpec(Cardinality.ONE, TargetFormat.SIGNATURE, AuthPattern.NO_CHECK),
            MessageAction.REACTION, new ActionSpec(Cardinality.ONE, TargetFormat.SIGNATURE, AuthPattern.NO_CHECK),
            MessageAction.REACTION_REMOVE, new ActionSpec(Cardinality.ONE, TargetFormat.SIGNATURE, AuthPattern.SELF_AUTHOR),
            MessageAction.EDIT, new ActionSpec(Cardinality.ONE, TargetFormat.SIGNATURE, AuthPattern.SELF_AUTHOR),
            MessageAction.REDACT, new ActionSpec(Cardinality.ONE, TargetFormat.SIGNATURE, AuthPattern.SELF_AUTHOR),
            MessageAction.PIN, new ActionSpec(Cardinality.ONE, TargetFormat.SIGNATURE, AuthPattern.CONVERSATION_CREATOR),
            MessageAction.UNPIN, new ActionSpec(Cardinality.ZERO, TargetFormat.NONE, AuthPattern.CONVERSATION_CREATOR),
            MessageAction.FORWARD, new ActionSpec(Cardinality.ZERO_OR_ONE, TargetFormat.PUBLIC_KEY, AuthPattern.NO_CHECK),
            MessageAction.SHARE_HISTORY, new ActionSpec(Cardinality.ZERO, TargetFormat.NONE, AuthPattern.NO_CHECK)
    );

    /**
     * Lookup by case-insensitive (and whitespace-trimmed) action string.
     *
     * <p>Returns empty for null, empty, whitespace-only, or unknown action.
     * The validator's step 1 interprets:</p>
     * <ul>
     *   <li>{@code MessageAction.normalize(action) == null} → no action set
     *       (plain message; legacy path).</li>
     *   <li>{@code normalize(action) != null} but {@code Optional.empty()}
     *       → STRICT DROP (the validator throws
     *       {@code UnknownActionException} per spec §12.7).</li>
     * </ul>
     *
     * <p>Callers MUST distinguish these two cases via
     * {@link MessageAction#normalize(String)}.</p>
     *
     * @param action the raw inbound action value; may be {@code null}
     * @return the {@link ActionSpec} for a recognized action, else empty
     */
    public static Optional<ActionSpec> lookup(String action) {
        String n = MessageAction.normalize(action);
        return n == null ? Optional.empty() : Optional.ofNullable(REGISTRY.get(n));
    }

    /**
     * Closed registry view for completeness tests.
     *
     * @return the set of registered (lowercase) action strings
     */
    public static Set<String> registeredActions() {
        return REGISTRY.keySet();
    }

    private MessageActionMeta() {
    }
}
