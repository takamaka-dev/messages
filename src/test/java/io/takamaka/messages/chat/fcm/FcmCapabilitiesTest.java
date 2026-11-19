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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FcmCapabilities}: the rules of DR-074 amendment 1 that the vector file cannot express by itself (A-1.1,
 * A-12.1, A-13.1, A-15, A-24).
 */
class FcmCapabilitiesTest {

    private static final String INVALID = FcmCapabilities.ERROR_INVALID_CAP;

    @Test
    void constants() {
        assertEquals("calls", FcmCapabilities.CALLS);
        assertEquals("INVALID_CAP", FcmCapabilities.ERROR_INVALID_CAP);
        assertEquals(8, FcmCapabilities.MAX_VALUES);
        assertEquals(32, FcmCapabilities.MAX_VALUE_LENGTH);
        assertEquals("[a-z0-9-]{1,32}", FcmCapabilities.VALUE_PATTERN.pattern());
        assertEquals(0, FcmCapabilities.VALUE_PATTERN.flags(), "no CASE_INSENSITIVE, no MULTILINE, no UNICODE flag");
    }

    @Test
    void absentAndEmptyAreValid_withOrWithoutAKnownSet() {
        assertNull(FcmCapabilities.validate(null, null));
        assertNull(FcmCapabilities.validate(List.of(), null));
        assertNull(FcmCapabilities.validate(null, Set.of()));
        assertNull(FcmCapabilities.validate(List.of(), Set.of("calls")));
    }

    @Test
    void theKnownSetRefusesUnknownValues_neverDropsThem() {
        assertNull(FcmCapabilities.validate(List.of("calls"), Set.of("calls")));
        assertEquals(INVALID, FcmCapabilities.validate(List.of("calls"), Set.of()));
        assertEquals(INVALID, FcmCapabilities.validate(List.of("calls", "calls-2"), Set.of("calls")));
        assertNull(FcmCapabilities.validate(List.of("calls", "calls-2"), Set.of("calls", "calls-2")));
    }

    /** A-12.1 / V13: the whole string is matched; {@code find()} would accept every one of these. */
    @Test
    void theWholeValueIsMatched_notASubstring() {
        for (String v : new String[]{"calls\n", "calls\r\n", "\ncalls", "calls ", "calls\u0085", " calls",
            "calls ", "calls\t", "xcalls!", "calls\u0000"}) {
            assertEquals(INVALID, FcmCapabilities.validate(Arrays.asList(v), null), v);
            // the trap itself: an anchored pattern with find() accepts "calls\n"
        }
        assertTrue(Pattern.compile("^" + FcmCapabilities.VALUE_REGEX + "$").matcher("calls\n").find(),
                "if this ever stops holding, the comment in FcmCapabilities about find() is stale");
    }

    /** A-12.1 / V7: the regex alone decides; nothing is lower-cased, whatever the default locale. */
    @Test
    void noNormalisation_inAnyLocale() {
        Locale saved = Locale.getDefault();
        try {
            for (Locale l : new Locale[]{Locale.ROOT, Locale.forLanguageTag("tr-TR"), Locale.forLanguageTag("az")}) {
                Locale.setDefault(l);
                assertNull(FcmCapabilities.validate(List.of("calls"), Set.of("calls")), l.toString());
                assertEquals(INVALID, FcmCapabilities.validate(List.of("Calls"), null), l.toString());
                assertEquals(INVALID, FcmCapabilities.validate(List.of("CALLS"), null), l.toString());
                assertEquals(INVALID, FcmCapabilities.validate(List.of("calıs"), null), l.toString());
                assertEquals(INVALID, FcmCapabilities.validate(List.of("İ"), null), l.toString());
            }
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void hyphensAreWellFormed_andTheBoundsAreExact() {
        assertNull(FcmCapabilities.validate(List.of("-"), null));
        assertNull(FcmCapabilities.validate(List.of("--"), null));
        assertNull(FcmCapabilities.validate(List.of("-", "--", "-a"), null));
        assertNull(FcmCapabilities.validate(List.of("a".repeat(32)), null));
        assertEquals(INVALID, FcmCapabilities.validate(List.of("a".repeat(33)), null));
        assertNull(FcmCapabilities.validate(List.of("a", "b", "c", "d", "e", "f", "g", "h"), null));
        assertEquals(INVALID, FcmCapabilities.validate(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i"), null));
        assertEquals(INVALID, FcmCapabilities.validate(List.of(""), null));
    }

    @Test
    void strictlyAscendingByCodePoint() {
        assertNull(FcmCapabilities.validate(List.of("0", "9", "a", "z"), null));
        assertNull(FcmCapabilities.validate(List.of("-", "0", "a"), null), "'-' (0x2D) < '0' (0x30) < 'a'");
        assertNull(FcmCapabilities.validate(List.of("calls", "calls-2"), null), "a prefix sorts first");
        assertEquals(INVALID, FcmCapabilities.validate(List.of("calls-2", "calls"), null));
        assertEquals(INVALID, FcmCapabilities.validate(List.of("b", "a"), null));
        assertEquals(INVALID, FcmCapabilities.validate(List.of("a", "a"), null));
        assertEquals(INVALID, FcmCapabilities.validate(List.of("a", "b", "a"), null));
    }

    /** Iris F4 / V12: a null element, a null-hostile known set, a hostile list: a code, never a throw. */
    @Test
    void validateNeverThrows() {
        assertEquals(INVALID, assertDoesNotThrow(() -> FcmCapabilities.validate(Arrays.asList((String) null), null)));
        // Set.of(...).contains(null) throws NPE: the null element must be refused before the known set is asked
        assertEquals(INVALID, assertDoesNotThrow(
                () -> FcmCapabilities.validate(Arrays.asList("calls", null), Set.of("calls"))));
        assertEquals(INVALID, assertDoesNotThrow(
                () -> FcmCapabilities.validate(Arrays.asList((String) null), Set.of("calls"))));
        List<String> hostile = new AbstractList<>() {
            @Override
            public String get(int index) {
                throw new IllegalStateException("boom");
            }

            @Override
            public int size() {
                return 1;
            }
        };
        assertEquals(INVALID, assertDoesNotThrow(() -> FcmCapabilities.validate(hostile, null)));
        assertTrue(FcmCapabilities.refusalReason(Arrays.asList((String) null), null).contains("null"));
        assertNull(FcmCapabilities.refusalReason(List.of("calls"), Set.of("calls")));
    }

    /** The prose names the rule and the position, never the value (a hand-built value is attacker text). */
    @Test
    void theRefusalReasonNeverEchoesTheValue() {
        String reason = FcmCapabilities.refusalReason(List.of("EVIL-PAYLOAD"), null);
        assertFalse(reason.contains("EVIL"), reason);
        reason = FcmCapabilities.refusalReason(List.of("zzz-unknown"), Set.of("calls"));
        assertFalse(reason.contains("zzz"), reason);
    }

    @Test
    void join_isTheStoredForm() {
        assertNull(FcmCapabilities.join(null));
        assertNull(FcmCapabilities.join(List.of()), "'' is never stored");
        assertEquals("calls", FcmCapabilities.join(List.of("calls")));
        assertEquals("a,calls", FcmCapabilities.join(List.of("a", "calls")));
        assertThrows(IllegalArgumentException.class, () -> FcmCapabilities.join(List.of("calls", "a")));
        assertThrows(IllegalArgumentException.class, () -> FcmCapabilities.join(Arrays.asList((String) null)));
    }

    /** A-1.1 / C-5: whole values, never {@code String.contains}. */
    @Test
    void hasValue_wholeValuesOnly() {
        assertTrue(FcmCapabilities.hasValue("calls", "calls"));
        assertTrue(FcmCapabilities.hasValue("a,calls", "calls"));
        assertTrue(FcmCapabilities.hasValue("calls,other", "calls"));
        assertFalse(FcmCapabilities.hasValue("calls-2", "calls"));
        assertFalse(FcmCapabilities.hasValue("xcalls", "calls"));
        assertFalse(FcmCapabilities.hasValue("calls-2,callsx", "calls"));
        assertFalse(FcmCapabilities.hasValue(null, "calls"));
        assertFalse(FcmCapabilities.hasValue("", "calls"));
        assertFalse(FcmCapabilities.hasValue("calls", null));
        assertFalse(FcmCapabilities.hasValue("calls,", ""), "an empty value never matches");
    }

    /** A-24 / C-4: the CHECK is derived, fits the longest stored form, and is never stricter than the validator. */
    @Test
    void storageCheckRegex_isDerivedAndNeverStricter() {
        assertEquals("^[a-z0-9-]{1,32}(,[a-z0-9-]{1,32}){0,7}$", FcmCapabilities.storageCheckRegex());
        assertEquals(263, FcmCapabilities.maxStoredLength());
        Pattern check = Pattern.compile(FcmCapabilities.storageCheckRegex());
        List<List<String>> valid = List.of(
                List.of("calls"), List.of("a"), List.of("-", "--"), List.of("calls", "calls-2"),
                List.of("a".repeat(32), "b".repeat(32), "c".repeat(32), "d".repeat(32),
                        "e".repeat(32), "f".repeat(32), "g".repeat(32), "h".repeat(32)));
        for (List<String> cap : valid) {
            assertNull(FcmCapabilities.validate(cap, null), cap.toString());
            String stored = FcmCapabilities.join(cap);
            assertTrue(stored.length() <= FcmCapabilities.maxStoredLength(), stored);
            assertTrue(check.matcher(stored).matches(), "the CHECK would refuse a validated value: " + stored);
        }
        assertEquals(263, FcmCapabilities.join(valid.get(4)).length());
        // the CHECK refuses what the regex can express (PostgreSQL's '$' is end-of-string only: rschat's T-7)
        for (String bad : new String[]{"", "Calls", "calls\n", "a,", ",a", "a,,b", "a".repeat(33),
            "a,b,c,d,e,f,g,h,i"}) {
            assertFalse(check.matcher(bad).matches(), bad);
        }
    }

    /** A-12.1, as a source guard: the check never normalises and never uses {@code find()}. */
    @Test
    void sourceGuard_noToLowerCase_noFind() throws Exception {
        String src = Files.readString(Paths.get("src/main/java/io/takamaka/messages/chat/fcm/FcmCapabilities.java"));
        String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
        assertFalse(code.contains("toLowerCase"), "no lower-casing before the check");
        assertFalse(code.contains("toUpperCase"), "no case mapping before the check");
        assertFalse(code.contains(".find("), "matches(), never find()");
        assertFalse(code.contains(".strip(") || code.contains(".trim("), "no trimming");
        assertFalse(code.contains(".sort(") || code.contains("sorted(") || code.contains("distinct("),
                "nothing is sorted or de-duplicated");
        assertTrue(code.contains(".matches()"));
    }
}
