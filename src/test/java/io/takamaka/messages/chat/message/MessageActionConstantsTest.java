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

import io.takamaka.messages.chat.message.MessageActionMeta.ActionSpec;
import io.takamaka.messages.chat.message.MessageActionMeta.AuthPattern;
import io.takamaka.messages.chat.message.MessageActionMeta.Cardinality;
import io.takamaka.messages.chat.message.MessageActionMeta.TargetFormat;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MessageAction}, {@link MessageActionMeta} and
 * {@link MessageProtocolVersion} — the string-typed wire constants from
 * Phase 1 §3.2.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class MessageActionConstantsTest {

    @Test
    public void canonicalWireStrings() {
        assertEquals("reply", MessageAction.REPLY);
        assertEquals("reaction", MessageAction.REACTION);
        assertEquals("reaction_remove", MessageAction.REACTION_REMOVE);
        assertEquals("edit", MessageAction.EDIT);
        assertEquals("redact", MessageAction.REDACT);
        assertEquals("pin", MessageAction.PIN);
        assertEquals("unpin", MessageAction.UNPIN);
        assertEquals("forward", MessageAction.FORWARD);
        assertEquals("share_history", MessageAction.SHARE_HISTORY);
        assertEquals(9, MessageAction.KNOWN.size());
    }

    @Test
    public void isKnown_caseInsensitiveAndTrimmed() {
        assertTrue(MessageAction.isKnown("reply"));
        assertTrue(MessageAction.isKnown("Reply"));
        assertTrue(MessageAction.isKnown("REPLY"));
        assertTrue(MessageAction.isKnown("  Reply  "));
        assertFalse(MessageAction.isKnown("__test_unknown_action__"));
        assertFalse(MessageAction.isKnown(null));
        assertFalse(MessageAction.isKnown(""));
        assertFalse(MessageAction.isKnown("   "));
    }

    @Test
    public void normalize_absentForms() {
        assertNull(MessageAction.normalize(null));
        assertNull(MessageAction.normalize(""));
        assertNull(MessageAction.normalize("   "));
        assertEquals("reply", MessageAction.normalize("Reply"));
        assertEquals("reply", MessageAction.normalize("  REPLY  "));
        assertEquals("share_history", MessageAction.normalize("Share_History"));
    }

    @Test
    public void meta_lookupCaseInsensitive() {
        Optional<ActionSpec> forward = MessageActionMeta.lookup("FORWARD");
        assertTrue(forward.isPresent());
        assertEquals(Cardinality.ZERO_OR_ONE, forward.get().cardinality());
        assertEquals(TargetFormat.PUBLIC_KEY, forward.get().targetFormat());
        assertEquals(AuthPattern.NO_CHECK, forward.get().authPattern());

        Optional<ActionSpec> pin = MessageActionMeta.lookup("pin");
        assertTrue(pin.isPresent());
        assertEquals(Cardinality.ONE, pin.get().cardinality());
        assertEquals(TargetFormat.SIGNATURE, pin.get().targetFormat());
        assertEquals(AuthPattern.CONVERSATION_CREATOR, pin.get().authPattern());
    }

    @Test
    public void meta_lookupAbsentAndUnknown() {
        assertTrue(MessageActionMeta.lookup(null).isEmpty());
        assertTrue(MessageActionMeta.lookup("").isEmpty());
        assertTrue(MessageActionMeta.lookup("   ").isEmpty());
        assertTrue(MessageActionMeta.lookup("nonsense").isEmpty());
    }

    @Test
    public void registryCompleteness() {
        assertEquals(MessageAction.KNOWN, MessageActionMeta.registeredActions(),
                "Every known action must have registered metadata");
    }

    @Test
    public void protocolVersion_parseValid() {
        assertTrue(MessageProtocolVersion.parse("1.1").isPresent());
        assertTrue(MessageProtocolVersion.parse("1.0").isPresent());
        assertTrue(MessageProtocolVersion.parse("100.0").isPresent());
        assertEquals(1, MessageProtocolVersion.parse("1.1").get().major());
        assertEquals(1, MessageProtocolVersion.parse("1.1").get().minor());
        assertTrue(MessageProtocolVersion.parse("1.1").get().isCompatibleMajor());
        assertFalse(MessageProtocolVersion.parse("2.0").get().isCompatibleMajor());
    }

    @Test
    public void protocolVersion_parseInvalid() {
        assertTrue(MessageProtocolVersion.parse("1").isEmpty());
        assertTrue(MessageProtocolVersion.parse("1.0.0").isEmpty());
        assertTrue(MessageProtocolVersion.parse("v1.0").isEmpty());
        assertTrue(MessageProtocolVersion.parse("1.0 a").isEmpty());
        assertTrue(MessageProtocolVersion.parse(null).isEmpty());
    }

    @Test
    public void protocolVersion_isAbsent() {
        assertTrue(MessageProtocolVersion.isAbsent(null));
        assertTrue(MessageProtocolVersion.isAbsent(""));
        assertTrue(MessageProtocolVersion.isAbsent("   "));
        assertFalse(MessageProtocolVersion.isAbsent("1.0"));
        assertFalse(MessageProtocolVersion.isAbsent("1.1"));
    }

    @Test
    public void protocolVersion_currentConstants() {
        assertEquals("1.1", MessageProtocolVersion.CURRENT);
        assertEquals(1, MessageProtocolVersion.CURRENT_MAJOR);
        assertEquals(1, MessageProtocolVersion.CURRENT_MINOR);
    }
}
