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
package io.takamaka.messages.chat.fcm;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * The capabilities an install declares for one FCM token at registration (DR-074 amendment 1, A-12 &hellip; A-16):
 * the signed member {@code cap} of {@link FcmTokenRegistrationSignedContentBean}. Pure functions; no state, no I/O.
 *
 * <p><b>The canonical form (A-12).</b> {@code cap} is ABSENT, or a list of 1 to {@value #MAX_VALUES} strings, each
 * matching {@link #VALUE_REGEX} (lower-case ASCII letters, digits, {@code -}; 1 to {@value #MAX_VALUE_LENGTH}
 * characters), in strictly ascending order by code point (hence unique). An empty list is the same value as absent
 * and is never sent (the field is {@code NON_EMPTY}). Any other spelling is refused with {@link #ERROR_INVALID_CAP}:
 * <b>nothing here sorts, de-duplicates, trims or lower-cases</b>. The signature covers the list in the order sent
 * (JCS keeps array order), so a server that normalised after verifying would store a value nobody signed.</p>
 *
 * <p><b>Regex mechanics (A-12.1).</b> Every element is checked with {@code VALUE_PATTERN.matcher(v).matches()}, never
 * {@code find()} (with {@code find()} an anchored {@code $} also matches before a final line terminator, so
 * {@code "calls\n"} would pass), and with no {@code toLowerCase} or other normalisation first: {@code "Calls"} is
 * refused, not lowered. The pattern accepts {@code -} and {@code --} as whole values; that is accepted as is (the
 * server refuses unknown values anyway). If the pattern is ever tightened, the storage CHECK follows in the same
 * change, because both come from {@link #VALUE_REGEX} through {@link #storageCheckRegex()}.</p>
 *
 * <p><b>{@code validate} never throws (A-15, Iris F4).</b> A {@code null} element (a hand-built {@code "cap":[null]}
 * survives the signature check), a non-matching element, too many values, a wrong order or a duplicate all return
 * {@link #ERROR_INVALID_CAP}. The broken rule goes to the log only, never into the return value: the code is the
 * contract, on the server, in the Java and Dart signers and in the shared vector file
 * ({@code java_fcm_cap_vectors.json}).</p>
 *
 * <p><b>{@link #CALLS} means</b> an S0 receiver (call protocol &ge; 1.1) that presents every call-type push correctly:
 * a call, a silent request, or nothing; never a stray alert. It does NOT mean «calls are available on this server»:
 * see {@code ServerInfoResponseBean#fcmTokenCapabilities} (A-17).</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public final class FcmCapabilities {

    private FcmCapabilities() {
    }

    /** The one capability value defined today: «this install presents every call-type push correctly». */
    public static final String CALLS = "calls";

    /** The {@code error_code} of {@link FcmTokenRegistrationResponseBean} for a refused {@code cap} (A-13). */
    public static final String ERROR_INVALID_CAP = "INVALID_CAP";

    /** At most this many values in one {@code cap}. */
    public static final int MAX_VALUES = 8;

    /** At most this many characters in one value. */
    public static final int MAX_VALUE_LENGTH = 32;

    /**
     * One value, without anchors: lower-case ASCII letters, digits and {@code -}, 1 to {@value #MAX_VALUE_LENGTH}
     * characters. The single source of {@link #VALUE_PATTERN} and of {@link #storageCheckRegex()}.
     */
    public static final String VALUE_REGEX = "[a-z0-9-]{1," + MAX_VALUE_LENGTH + "}";

    /** {@link #VALUE_REGEX} compiled, no flags. Use with {@code matcher(v).matches()} only (A-12.1). */
    public static final Pattern VALUE_PATTERN = Pattern.compile(VALUE_REGEX);

    /**
     * The shape check of A-12, and on the server also the «known values» check (Q-1: unknown values are REFUSED,
     * never dropped).
     *
     * @param cap the list as sent (or as about to be signed); {@code null} or empty = absent = valid
     * @param known the values this server advertises in {@code fcmTokenCapabilities}; {@code null} checks the
     * shape only (the signers)
     * @return {@code null} when valid, else {@link #ERROR_INVALID_CAP}; never throws
     */
    public static String validate(List<String> cap, Set<String> known) {
        String reason = refusalReason(cap, known);
        if (reason == null) {
            return null;
        }
        log.debug("cap refused: {}", reason);
        return ERROR_INVALID_CAP;
    }

    /**
     * The same check as {@link #validate}, returning the broken rule in prose (for a log line or the response's
     * {@code message}), or {@code null} when valid. The prose names the rule and the element's position, never the
     * element itself. Not a contract: compare {@link #validate}'s code, never this text. Never throws.
     */
    public static String refusalReason(List<String> cap, Set<String> known) {
        try {
            if (cap == null || cap.isEmpty()) {
                return null;
            }
            if (cap.size() > MAX_VALUES) {
                return "cap has more than " + MAX_VALUES + " values";
            }
            String previous = null;
            int i = 0;
            for (String v : cap) {
                if (v == null) {
                    return "cap[" + i + "] is null";
                }
                if (!VALUE_PATTERN.matcher(v).matches()) {
                    return "cap[" + i + "] does not match " + VALUE_REGEX;
                }
                // every value is ASCII here, so UTF-16 order is code-point order
                if (previous != null && previous.compareTo(v) >= 0) {
                    return "cap[" + i + "] is not strictly after cap[" + (i - 1) + "] (unsorted or duplicate)";
                }
                if (known != null && !known.contains(v)) {
                    return "cap[" + i + "] is not a value this server advertises";
                }
                previous = v;
                i++;
            }
            return null;
        } catch (RuntimeException ex) {
            // a belt: a hostile List or Set implementation; the rule is that this check never throws
            return "cap could not be checked (" + ex.getClass().getSimpleName() + ")";
        }
    }

    /**
     * The stored form (A-24): the values joined with {@code ,}, or {@code null} for an absent or empty {@code cap}
     * ({@code ''} is never stored). Called only on a list {@link #validate} passed.
     *
     * @throws IllegalArgumentException if {@code cap} does not pass the shape check (a caller that skipped
     * {@link #validate}: a programming error, never a client's input)
     */
    public static String join(List<String> cap) {
        if (cap == null || cap.isEmpty()) {
            return null;
        }
        if (validate(cap, null) != null) {
            throw new IllegalArgumentException("join called on a cap that does not pass validate");
        }
        return String.join(",", cap);
    }

    /**
     * Whether the stored form contains {@code value} as a WHOLE value (A-1.1): the stored string is split on
     * {@code ,} and each element compared for equality, never {@code String.contains} ({@code "calls-2"} does not
     * contain the value {@code calls}). {@code null} or {@code ''} stored &rArr; false. NULL-safe, never throws.
     */
    public static boolean hasValue(String stored, String value) {
        if (stored == null || stored.isEmpty() || value == null || value.isEmpty()) {
            return false;
        }
        for (String v : stored.split(",", -1)) {
            if (v.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The regex of the storage CHECK on {@code user_fcm_tokens.cap} (A-24, C-4), derived from {@link #VALUE_REGEX}
     * and {@link #MAX_VALUES}: {@code ^[a-z0-9-]{1,32}(,[a-z0-9-]{1,32}){0,7}$}. It is never STRICTER than
     * {@link #validate} (order and uniqueness are the validator's alone), so a validated registration never fails
     * on the CHECK. The migration file carries this exact string (T-7).
     */
    public static String storageCheckRegex() {
        return "^" + VALUE_REGEX + "(," + VALUE_REGEX + "){0," + (MAX_VALUES - 1) + "}$";
    }

    /**
     * The longest stored form, {@code MAX_VALUES × MAX_VALUE_LENGTH + MAX_VALUES − 1} = 263: the column's
     * {@code varchar} length (A-24).
     */
    public static int maxStoredLength() {
        return MAX_VALUES * MAX_VALUE_LENGTH + MAX_VALUES - 1;
    }
}
