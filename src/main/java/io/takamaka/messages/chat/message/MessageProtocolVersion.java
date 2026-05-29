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

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Constants holder for the message-management wire protocol version
 * (the {@code client_protocol_version} field).
 *
 * <p>Same string-typed pattern as {@link MessageAction}: no enum, a strict
 * {@code MAJOR.MINOR} format, and a tolerant parser that returns
 * {@link Optional#empty()} for anything malformed. See spec §3.4 and the
 * Phase 1 plan §3.2 / §4.2 step 0.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.5.0
 */
public final class MessageProtocolVersion {

    /** Legacy revision; also matches null / empty / whitespace-only. */
    public static final String V_1_0 = "1.0";
    /** Current revision. */
    public static final String V_1_1 = "1.1";

    public static final String CURRENT = V_1_1;
    public static final int CURRENT_MAJOR = 1;
    public static final int CURRENT_MINOR = 1;

    private static final Pattern FORMAT = Pattern.compile("^\\d+\\.\\d+$");

    public record Parsed(int major, int minor) {

        public boolean isCompatibleMajor() {
            return major == CURRENT_MAJOR;
        }
    }

    /**
     * Parse a version string.
     *
     * <p>Returns {@link Optional#empty()} for malformed or out-of-range
     * input. The strict regex {@code ^\d+\.\d+$} is applied after trimming
     * the outer whitespace — internal whitespace fails.</p>
     *
     * @param raw the raw version value; may be {@code null}
     * @return a populated {@link Parsed} for a well-formed version, else empty
     */
    public static Optional<Parsed> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (!FORMAT.matcher(trimmed).matches()) {
            return Optional.empty();
        }
        String[] parts = trimmed.split("\\.");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            if (major < 0 || minor < 0) {
                return Optional.empty();
            }
            return Optional.of(new Parsed(major, minor));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Quick check: is the input a legitimate "absent version" (null, empty,
     * or whitespace-only after trim)?
     *
     * <p>Used by the validator's step 0 to distinguish a legacy grandfather
     * message from one declaring an explicit version.</p>
     *
     * @param raw the raw version value; may be {@code null}
     * @return {@code true} iff the version is absent
     */
    public static boolean isAbsent(String raw) {
        return raw == null || raw.trim().isEmpty();
    }

    private MessageProtocolVersion() {
    }
}
