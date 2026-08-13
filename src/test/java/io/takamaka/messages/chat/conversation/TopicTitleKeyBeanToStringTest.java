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
package io.takamaka.messages.chat.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import io.takamaka.messages.utils.Redact;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code TopicTitleKeyBean.toString()} must not disclose the conversation key or salt.
 *
 * <p>The sweep is written by REFLECTION rather than against a fixed expected string, so that a
 * secret field added to the bean tomorrow fails this test on the day it is added. A test pinned
 * to today's rendering would keep passing for the wrong reason.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 1.8.1
 */
public class TopicTitleKeyBeanToStringTest {

    /**
     * Fields deliberately printed in full. Anything NOT listed here is treated as a secret and
     * must not appear in {@code toString()} — the fail-safe direction for a field added later.
     */
    private static final Set<String> PRINTED_IN_FULL = Set.of("topicTitle");

    private static final String TITLE = "Board buyout Q3";
    private static final String KEY = "K".repeat(400);
    private static final String SALT = "S".repeat(32);

    private static TopicTitleKeyBean sample() {
        return new TopicTitleKeyBean(TITLE, KEY, SALT);
    }

    /** Field name to value, for every non-static declared field. */
    private static Map<String, String> fieldValues(TopicTitleKeyBean bean) throws Exception {
        final Map<String, String> values = new LinkedHashMap<>();
        for (Field f : TopicTitleKeyBean.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                continue;
            }
            f.setAccessible(true);
            final Object v = f.get(bean);
            if (v != null) {
                values.put(f.getName(), String.valueOf(v));
            }
        }
        return values;
    }

    /** The names of secret fields whose value is visible in {@code rendered}. */
    private static List<String> leakedIn(String rendered, Map<String, String> values) {
        final List<String> leaked = new ArrayList<>();
        values.forEach((name, value) -> {
            if (!PRINTED_IN_FULL.contains(name) && rendered.contains(value)) {
                leaked.add(name);
            }
        });
        return leaked;
    }

    @Test
    @DisplayName("no secret field value appears in toString(), by reflection over every field")
    public void noSecretFieldIsRendered() throws Exception {
        final TopicTitleKeyBean bean = sample();
        final Map<String, String> values = fieldValues(bean);

        assertEquals(3, values.size(),
                "bean gained or lost a field — review whether it is a secret, then update "
                + "PRINTED_IN_FULL deliberately: " + values.keySet());

        assertTrue(leakedIn(bean.toString(), values).isEmpty(),
                "toString() disclosed: " + leakedIn(bean.toString(), values));
    }

    @Test
    @DisplayName("POSITIVE CONTROL: the sweep does detect a leak when one is present")
    public void sweepDetectsALeak() throws Exception {
        // Reconstruct exactly what @Data used to generate — every field, value in full. If the
        // @ToString(onlyExplicitlyIncluded = true) guard were reverted, THIS is what toString()
        // would return, and the assertion below is what would then fail in the test above.
        // Without this control, a sweep that silently matched nothing would look like a pass.
        final TopicTitleKeyBean bean = sample();
        final Map<String, String> values = fieldValues(bean);

        final StringJoiner lombokDefault = new StringJoiner(", ", "TopicTitleKeyBean(", ")");
        values.forEach((name, value) -> lombokDefault.add(name + "=" + value));

        final List<String> leaked = leakedIn(lombokDefault.toString(), values);
        assertTrue(leaked.contains("symmetricKey"), "control must flag the key");
        assertTrue(leaked.contains("conversationSalt"), "control must flag the salt");
    }

    @Test
    @DisplayName("the title IS printed in full — redaction must not cost the debug value")
    public void titleIsPrintedInFull() {
        assertTrue(sample().toString().contains(TITLE),
                "the title is the only field that makes a chatMap entry legible; it is "
                + "confidential w.r.t. the SERVER, enforced by encryption, not by log hygiene");
    }

    @Test
    @DisplayName("secrets render as shape tokens, so a malformed value is still diagnosable")
    public void secretsRenderAsTokens() {
        final String rendered = sample().toString();
        assertTrue(rendered.matches(".*symmetricKey=<ok:400 fp=[0-9a-f]{4}>.*"), rendered);
        assertTrue(rendered.contains("conversationSalt=<ok:32>"), rendered);
        assertFalse(rendered.contains("conversationSalt=<ok:32 fp="),
                "the salt must never be fingerprinted — it would become an offline verifier "
                + "for the guessable title");
    }

    /**
     * Mirrors the bean's annotation setup and adds the realistic future mistake: a secret field
     * that nobody remembered to annotate.
     */
    @Data
    @ToString(onlyExplicitlyIncluded = true)
    @AllArgsConstructor
    static class OptIn {

        @ToString.Include
        private String topicTitle;
        private String symmetricKey;
        private String addedLater;

        @ToString.Include(name = "symmetricKey")
        private String redactedSymmetricKey() {
            return Redact.highEntropy(symmetricKey, 400, Redact.ALNUM);
        }
    }

    /** Identical, but WITHOUT the opt-in guard — the control. */
    @Data
    @AllArgsConstructor
    static class OptOut {

        private String topicTitle;
        private String symmetricKey;
        private String addedLater;

        @ToString.Include(name = "symmetricKey")
        private String redactedSymmetricKey() {
            return Redact.highEntropy(symmetricKey, 400, Redact.ALNUM);
        }
    }

    @Test
    @DisplayName("onlyExplicitlyIncluded is what protects a field added LATER, and it is load-bearing")
    public void aFieldAddedLaterIsSilentByDefault() {
        final String future = "SECRET_ADDED_NEXT_YEAR";

        // What the guard buys. Note it is NOT what redacts symmetricKey today: an
        // @ToString.Include(name = "symmetricKey") method shadows the same-named field on its
        // own. The guard's entire job is this — the field nobody annotated.
        assertFalse(new OptIn(TITLE, KEY, future).toString().contains(future),
                "a newly added field must be silent until someone opts it in");

        // Control: drop the guard and the same field leaks, which is how this bean leaked.
        assertTrue(new OptOut(TITLE, KEY, future).toString().contains(future),
                "control failed to leak — the test would be proving nothing");
    }

    @Test
    @DisplayName("the anomalies a log is read for are visible: null, empty, blank, short")
    public void anomaliesAreVisible() {
        assertTrue(new TopicTitleKeyBean(TITLE, null, SALT).toString()
                .contains("symmetricKey=<null>"));
        assertTrue(new TopicTitleKeyBean(TITLE, "", SALT).toString()
                .contains("symmetricKey=<empty>"));
        // Survives validate() (isEmpty(), not isBlank()) — so the log is the only place it shows.
        assertTrue(new TopicTitleKeyBean(TITLE, " ".repeat(400), SALT).toString()
                .contains("symmetricKey=<blank:400>"));
        assertTrue(new TopicTitleKeyBean(TITLE, "K".repeat(17), SALT).toString()
                .contains("symmetricKey=<malformed:17 fp="));
        assertTrue(new TopicTitleKeyBean(TITLE, KEY, "S".repeat(9)).toString()
                .contains("conversationSalt=<malformed:9>"));
    }
}
