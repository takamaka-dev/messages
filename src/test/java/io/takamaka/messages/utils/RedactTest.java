/*
 * Copyright 2024 AiliA SA.
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
package io.takamaka.messages.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Classification tokens emitted by {@link Redact}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.8.1
 */
public class RedactTest {

    private static String key(int len) {
        return "a".repeat(len);
    }

    @Test
    @DisplayName("null and empty are distinct tokens, not two spellings of zero length")
    public void nullAndEmptyAreDistinct() {
        assertEquals("<null>", Redact.highEntropy(null, 400, Redact.ALNUM));
        assertEquals("<empty>", Redact.highEntropy("", 400, Redact.ALNUM));
        assertNotEquals(Redact.NULL, Redact.EMPTY);
    }

    @Test
    @DisplayName("an all-whitespace value reports blank AND its raw length")
    public void blankKeepsRawLength() {
        // The failure mode validate() misses (it tests isEmpty(), not isBlank()), and the one a
        // trimmed-length-only rendering would report as "0" while hiding that 400 chars arrived.
        assertEquals("<blank:400>", Redact.highEntropy(" ".repeat(400), 400, Redact.ALNUM));
        assertEquals("<blank:3>", Redact.opaque("\t\n ", 32, Redact.ALNUM));
    }

    @Test
    @DisplayName("well-formed values classify ok; wrong length or charset classify malformed")
    public void shapeIsClassified() {
        assertTrue(Redact.highEntropy(key(400), 400, Redact.ALNUM).startsWith("<ok:400 fp="));
        assertTrue(Redact.highEntropy(key(17), 400, Redact.ALNUM).startsWith("<malformed:17 fp="));
        assertEquals("<ok:32>", Redact.opaque(key(32), 32, Redact.ALNUM));
        assertEquals("<malformed:32>", Redact.opaque("!".repeat(32), 32, Redact.ALNUM));
        assertEquals("<ok:5>", Redact.opaque("abcde", Redact.ANY_LENGTH, null));
    }

    @Test
    @DisplayName("no token ever contains the value")
    public void tokensNeverCarryTheValue() {
        final String secret = "S3cretKeyMaterialThatMustNeverAppear";
        assertFalse(Redact.highEntropy(secret, 400, Redact.ALNUM).contains(secret));
        assertFalse(Redact.opaque(secret, 32, Redact.ALNUM).contains(secret));
        assertFalse(Redact.opaque(secret).contains(secret));
    }

    @Test
    @DisplayName("fingerprint is 4 hex chars, stable per value, and only on the highEntropy path")
    public void fingerprintShapeAndScope() {
        final String a = key(400);
        final String b = "b".repeat(400);
        final String fpA = Redact.highEntropy(a, 400, Redact.ALNUM);

        assertEquals(fpA, Redact.highEntropy(a, 400, Redact.ALNUM), "same value, same fingerprint");
        assertNotEquals(fpA, Redact.highEntropy(b, 400, Redact.ALNUM), "different value, different fp");
        assertTrue(fpA.matches("<ok:400 fp=[0-9a-f]{4}>"), "4 hex chars: " + fpA);

        // opaque() must NEVER fingerprint: over a guessable value a 16-bit digest is an offline
        // verifier, which is the attack conversationSalt exists to prevent.
        assertFalse(Redact.opaque(a, 400, Redact.ALNUM).contains("fp="));
        assertFalse(Redact.opaque(a).contains("fp="));
    }

    @Test
    @DisplayName("every anomaly is reachable by one grep")
    public void anomaliesShareOneGrep() {
        final String pattern = "<(null|empty|blank|malformed).*";
        assertTrue(Redact.opaque(null).matches(pattern));
        assertTrue(Redact.opaque("").matches(pattern));
        assertTrue(Redact.opaque("   ", 32, Redact.ALNUM).matches(pattern));
        assertTrue(Redact.opaque("short", 32, Redact.ALNUM).matches(pattern));
        assertFalse(Redact.opaque(key(32), 32, Redact.ALNUM).matches(pattern), "healthy must not match");
    }
}
