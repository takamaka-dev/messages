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
package io.takamaka.messages.chat.typing;

import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.messages.utils.ChatUtils;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.TkmTextUtils;
import lombok.extern.slf4j.Slf4j;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Typing-indicator bean + signed-subscribe tests (Messages).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class TypingTest {

    static InstanceWalletKeystoreInterface iwkED;
    static final String CONV = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";

    @BeforeAll
    static void setUp() throws WalletException {
        iwkED = new InstanceWalletKeyStoreBCED25519("typing_test_wallet", "pw");
    }

    private static String toJson(Object bean) throws Exception {
        return TkmTextUtils.getJacksonMapper().writeValueAsString(bean);
    }

    @Test
    void subscribe_signVerifyRoundTrip() throws Exception {
        TypingSubscribeBean sub = ChatCryptoUtils.getTypingSubscribeBean(iwkED, 0);
        assertEquals("TYPING_SUBSCRIBE", sub.getMessageType());
        String json = toJson(sub);
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));   // verifies, returns typed bean
        TypingSubscribeBean parsed = ChatUtils.fromJsonToTypingSubscribeBean(json);
        assertEquals(sub.getSignature(), parsed.getSignature());
        assertEquals("1.0", parsed.getPl().getProtocolVersion());
    }

    @Test
    void subscribe_tampered_rejected() throws Exception {
        TypingSubscribeBean sub = ChatCryptoUtils.getTypingSubscribeBean(iwkED, 0);
        sub.getPl().setClientTimestamp(System.currentTimeMillis() + 999_999L); // flip signed content
        assertThrows(ChatMessageException.class,
                () -> ChatCryptoUtils.verifySignedMessage(toJson(sub)));
    }

    @Test
    void emitFrame_carriesNoFrom() throws Exception {
        TypingEmitBean emit = new TypingEmitBean(CONV, "1.0");
        String json = toJson(emit);
        // the plain emit frame must NOT carry a from field (server attributes from the connection)
        assertTrue(json.contains("\"conv\""));
        assertTrue(!json.contains("\"from\""));
        TypingEmitBean parsed = ChatUtils.fromJsonToTypingEmitBean(json);
        assertEquals(CONV, parsed.getConversationHashName());
    }

    @Test
    void signal_perIdentity_and_aggregate_roundTrip() throws Exception {
        // per-identity
        TypingSignalBean perId = new TypingSignalBean(CONV, iwkED.getPublicKeyAtIndexURL64(0), "1.0", true);
        TypingSignalBean p = ChatUtils.fromJsonToTypingSignalBean(toJson(perId));
        assertEquals(iwkED.getPublicKeyAtIndexURL64(0), p.getSenderPublicKey());
        assertTrue(p.isActive());
        // fuzzy aggregate: from == null
        TypingSignalBean agg = new TypingSignalBean(CONV, null, "1.0", false);
        TypingSignalBean a = ChatUtils.fromJsonToTypingSignalBean(toJson(agg));
        assertNull(a.getSenderPublicKey(), "aggregate signal carries no identity");
        assertEquals(false, a.isActive());
    }
}
