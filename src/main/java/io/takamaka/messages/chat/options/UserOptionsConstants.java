/*
 * Copyright 2025 AiliA SA.
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
package io.takamaka.messages.chat.options;

/**
 * Shared literals for user options, mirroring the authoritative registry
 * ({@code rschat-docs/api-references/user-options-registry.md}). The server
 * validation matrix, the manifest, the client baseline, and the conformance
 * test all reference these constants rather than re-typing the wire strings.
 *
 * <p>The registry remains the source of truth; this class only avoids string
 * drift across modules. A conformance test asserts the runtime matrix/manifest
 * equal the registry.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class UserOptionsConstants {

    private UserOptionsConstants() {
    }

    // ---- visibility tiers (USER_OPTIONS_DESIGN.md D10 / registry §2.1) ----
    public static final String VISIBILITY_PROTECTED = "Protected";
    public static final String VISIBILITY_MEMBERS_ONLY = "MembersOnly";
    public static final String VISIBILITY_PUBLIC = "Public";

    // ---- READ_NOTIFICATIONS v1.0 (registry §4) ----
    /**
     * Governs the read-receipt opt-in. One symmetric {@code {enabled}} flag
     * controlling both broadcasting my reads and seeing others'.
     */
    public static final String READ_NOTIFICATIONS = "READ_NOTIFICATIONS";
    public static final String READ_NOTIFICATIONS_V1 = "v1.0";
    /**
     * Privacy-first default (opt-in): absent/below-watermark = OFF.
     */
    public static final String READ_NOTIFICATIONS_DEFAULT = "{\"enabled\":false}";
}
