package io.takamaka.messages.chat.message;

import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.utils.CHAT_MESSAGE_TYPES;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.TkmTextUtils;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * F277: the BACKWARDS history read is a new {@code message_type} over the existing signed payload —
 * no new JSON key. Signs, serialises and verifies like BY_SIGNATURE.
 */
public class RetrieveMessagesBeforeSignatureTest {

    static InstanceWalletKeystoreInterface iwk;

    @BeforeAll
    public static void setUpClass() throws WalletException {
        iwk = new InstanceWalletKeyStoreBCED25519("retrieve_before_signature_test_wallet", "superSecretPassword");
    }

    private static RetrieveMessageSignedRequestBean payload() {
        return new RetrieveMessageSignedRequestBean(1_758_000_000_000L, 50L,
                "189e41eff236159170fb58a1e34bb2c8482c3cc439d1c7a5ba5180b9f29ac30d", "cursorSignatureOfTheOldestRowSeen");
    }

    @Test
    public void beforeSignature_roundTrip_verifies() throws Exception {
        RetrieveMessageRequestBean req = ChatCryptoUtils.getRetrieveMessageRequestBeanBeforeSignature(iwk, 0, payload());
        assertEquals("RETRIEVE_MESSAGE_FROM_CONVERSATION_BEFORE_SIGNATURE", req.getMessageType());
        assertEquals(CHAT_MESSAGE_TYPES.RETRIEVE_MESSAGE_FROM_CONVERSATION_BEFORE_SIGNATURE.name(), req.getMessageType());
        String json = TkmTextUtils.getJacksonMapper().writeValueAsString(req);
        // the same keys as BY_SIGNATURE: nothing new for a port to mirror but the type string
        assertTrue(json.contains("\"signed_request\""));
        assertTrue(json.contains("\"last_message_signature\":\"cursorSignatureOfTheOldestRowSeen\""));
        assertTrue(json.contains("\"number_of_messages\":50"));
        assertFalse(json.contains("\"before"));
        Object verified = ChatCryptoUtils.verifySignedMessage(json);
        assertTrue(verified instanceof RetrieveMessageRequestBean);
    }

    @Test
    public void beforeSignature_tamperedCursor_rejected() throws Exception {
        // The type is outside the signed payload (as for every envelope); what the signature binds is the
        // cursor. A tampered cursor must not verify.
        RetrieveMessageRequestBean req = ChatCryptoUtils.getRetrieveMessageRequestBeanBeforeSignature(iwk, 0, payload());
        req.getRetrieveMessageSignedRequestBean().setLastMessageSignature("anotherCursor");
        String tampered = TkmTextUtils.getJacksonMapper().writeValueAsString(req);
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(tampered));
    }
}
