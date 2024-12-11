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
package io.takamaka.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.takamaka.messages.chat.BasicMessageEncryptedContentBean;
import io.takamaka.messages.chat.ChatMediaBean;
import io.takamaka.messages.chat.responses.NonceResponseBean;
import io.takamaka.messages.chat.SignedMessageBean;
import io.takamaka.messages.chat.requests.BasicMessageRequestBean;
import io.takamaka.messages.chat.requests.RegisterUserRequestBean;
import io.takamaka.messages.chat.requests.RegisterUserRequestSignedContentBean;
import io.takamaka.messages.chat.requests.SignedTimestampRequestBean;
import io.takamaka.messages.chat.requests.UserNotificationRequestBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.exception.InvalidChatMessageSignatureException;
import io.takamaka.messages.exception.MessageException;
import io.takamaka.messages.exception.UnsupportedSignatureCypherException;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.messages.utils.ChatUtils;
import io.takamaka.messages.utils.SimpleRequestHelper;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeyStoreBCRSA4096ENC;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.TkmCypherProviderBCRSA4096ENC;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.log4j.BasicConfigurator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class MessagesTest {

    public static InstanceWalletKeystoreInterface iwkED;
    public static InstanceWalletKeystoreInterface iwkRSA;
    public static final String password = "superSecretPassword";

    public MessagesTest() {
        BasicConfigurator.configure();
    }

    @BeforeAll
    public static void setUpClass() throws WalletException {

        iwkED = new InstanceWalletKeyStoreBCED25519("chat_messages_wallet_test", password);
        iwkRSA = new InstanceWalletKeyStoreBCRSA4096ENC("chat_messages_wallet_test", password);
//        iwkED = new InstanceWalletKeyStore("chat_messages_wallet_test", password);
    }

    @Test
    public void testRegisterUserRequestBean() throws Exception {
        RegisterUserRequestBean signedRegisteredUserRequests
                = ChatCryptoUtils.getSignedRegisteredUserRequests(iwkED,
                        0,
                        new RegisterUserRequestSignedContentBean(
                                new NonceResponseBean(
                                        UUID.randomUUID().toString(),
                                        Long.MIN_VALUE,
                                        Long.MIN_VALUE
                                ),
                                iwkRSA.getPublicKeyAtIndexURL64(0),
                                iwkRSA.getWalletCypher().name()
                        )
                );
        log.info("uud signed message {} ", ChatUtils.getObjectJsonPretty(signedRegisteredUserRequests));
        String sigReqJson = ChatUtils.getObjectJsonPretty(signedRegisteredUserRequests);
        RegisterUserRequestBean fromJsonRegisterUserRequestBean = ChatUtils.fromJsonToRegisterUserRequestBean(sigReqJson);
        assertEquals(fromJsonRegisterUserRequestBean, signedRegisteredUserRequests);
        SignedMessageBean fromJsonToSignedMessageBean = ChatUtils.fromJsonToSignedMessageBean(sigReqJson);
        log.info(fromJsonToSignedMessageBean.toString());
        SignedMessageBean verifySignedMessage = ChatCryptoUtils.verifySignedMessage(sigReqJson);
        assert (verifySignedMessage != null);
        RegisterUserRequestBean rurb = (RegisterUserRequestBean) verifySignedMessage;
        log.info(rurb.toString());
        assertNotNull(rurb.getRegisterUserRequestSignedContentBean().getNonce());

    }

    @Test
    public void testRegisterUserRequestBean_withEmptyKey() throws Exception {
        RegisterUserRequestBean signedRegisteredUserRequests
                = ChatCryptoUtils.getSignedRegisteredUserRequests(
                        iwkED,
                        0,
                        new RegisterUserRequestSignedContentBean(
                                new NonceResponseBean(
                                        UUID.randomUUID().toString(),
                                        Long.MIN_VALUE,
                                        Long.MIN_VALUE
                                ),
                                null,
                                iwkRSA.getWalletCypher().name()
                        )
                );
        log.info("uud signed message {} ", ChatUtils.getObjectJsonPretty(signedRegisteredUserRequests));
        String jsonReqJson = ChatUtils.getObjectJsonPretty(signedRegisteredUserRequests);
        RegisterUserRequestBean fromSignedRegisteredUserRequests = ChatUtils.fromJsonToRegisterUserRequestBean(jsonReqJson);
        assertEquals(signedRegisteredUserRequests, fromSignedRegisteredUserRequests);
        log.info(fromSignedRegisteredUserRequests.toString());
        SignedMessageBean verifySignedMessage = ChatCryptoUtils.verifySignedMessage(jsonReqJson);
        assert (verifySignedMessage != null);
        RegisterUserRequestBean rurb = (RegisterUserRequestBean) verifySignedMessage;
        log.info(rurb.toString());
        assertNotNull(rurb.getRegisterUserRequestSignedContentBean().getNonce());

    }

    @Test
    public void testRegisterUserRequestBean_withIncorrectKeyFormat() throws Exception {
        RegisterUserRequestBean signedRegisteredUserRequests
                = ChatCryptoUtils.getSignedRegisteredUserRequests(
                        iwkED,
                        0,
                        new RegisterUserRequestSignedContentBean(
                                new NonceResponseBean(
                                        UUID.randomUUID().toString(),
                                        Long.MIN_VALUE,
                                        Long.MIN_VALUE
                                ),
                                "pollo",
                                iwkRSA.getWalletCypher().name()
                        )
                );
        log.info("uud signed message {} ", ChatUtils.getObjectJsonPretty(signedRegisteredUserRequests));
        String jsonReqJson = ChatUtils.getObjectJsonPretty(signedRegisteredUserRequests);
        Exception ex = assertThrows(InvalidChatMessageSignatureException.class, () -> ChatCryptoUtils.verifySignedMessage(jsonReqJson, "pollo"));
        assertEquals(ex.getMessage(), "invalid message signature");
    }
    
    @Test
    public void testRegisterUserRequestBean_withIncorrectKey() throws Exception {
        RegisterUserRequestBean signedRegisteredUserRequests
                = ChatCryptoUtils.getSignedRegisteredUserRequests(
                        iwkED,
                        0,
                        new RegisterUserRequestSignedContentBean(
                                new NonceResponseBean(
                                        UUID.randomUUID().toString(),
                                        Long.MIN_VALUE,
                                        Long.MIN_VALUE
                                ),
                                iwkRSA.getPublicKeyAtIndexURL64(1),
                                iwkRSA.getWalletCypher().name()
                        )
                );
        log.info("uud signed message {} ", ChatUtils.getObjectJsonPretty(signedRegisteredUserRequests));
        String jsonReqJson = ChatUtils.getObjectJsonPretty(signedRegisteredUserRequests);
        Exception ex = assertThrows(InvalidChatMessageSignatureException.class, () -> ChatCryptoUtils.verifySignedMessage(jsonReqJson, iwkRSA.getPublicKeyAtIndexURL64(1)));
        assertEquals(ex.getMessage(), "invalid message signature");
    }

    @Test
    public void testRegisterUserRequestBean_withInvalidParams() throws Exception {
        String[] from = new String [] {"pollo", "prova"};
        RegisterUserRequestBean signedRegisteredUserRequests
                = ChatCryptoUtils.getSignedRegisteredUserRequests(
                        iwkED,
                        0,
                        new RegisterUserRequestSignedContentBean(
                                new NonceResponseBean(
                                        UUID.randomUUID().toString(),
                                        Long.MIN_VALUE,
                                        Long.MIN_VALUE
                                ),
                                "pollo",
                                iwkRSA.getWalletCypher().name()
                        )
                );
        log.info("uud signed message {} ", ChatUtils.getObjectJsonPretty(signedRegisteredUserRequests));
        String jsonReqJson = ChatUtils.getObjectJsonPretty(signedRegisteredUserRequests);
        Exception ex = assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(jsonReqJson, "pollo", "prova"));
        String exMsg = "invalid parameters number, expected 0..1 got " + Arrays.toString(from);
        assertEquals(ex.getMessage(), exMsg);

    }
    
    @Test
    public void testMessageEncConversation() throws ChatMessageException, JsonProcessingException, MessageException{
        
        BasicMessageRequestBean basicMessageBean 
                = ChatCryptoUtils.getBasicMessageBean(
                        iwkED, 
                        0, 
                        "placehoder", 
                        "pollo", 
                        new ArrayList<String>(), 
                        new BasicMessageEncryptedContentBean(
                                "sticazzi", 
                                new ArrayList<ChatMediaBean>()));
        
        String basicMessageJson = TkmTextUtils.getJacksonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(basicMessageBean);
        
        SignedMessageBean verifySignedMessage = ChatCryptoUtils.verifySignedMessage(basicMessageJson);
        
        assertNotNull(verifySignedMessage.getSignature());
        
        
    }
    
    @Test
    public void testUserNotification() throws ChatMessageException, JsonProcessingException, MessageException{
        
        UserNotificationRequestBean userNotificationRequestBean = ChatCryptoUtils.getUserNotificationRequestBean(new Date().getTime(), true, iwkED, 0);
        
        String requestJson = TkmTextUtils.getJacksonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(userNotificationRequestBean);
        SignedMessageBean verifySignedMessage = ChatCryptoUtils.verifySignedMessage(requestJson);
        
        
        assertNotNull(verifySignedMessage.getSignature());
        log.info("signed message signature: {} ", verifySignedMessage.getSignature());
        log.info("signed message from: {} ", verifySignedMessage.getFrom());
        
        
    }
    
    @Test
    public void testSignedTimestamp() throws ChatMessageException, JsonProcessingException, MessageException{
        
        SignedTimestampRequestBean signedTimestampRequest = ChatCryptoUtils.getSignedTimestampRequest(iwkED, 0);
        
        SignedTimestampRequestBean signedTimestampRequest1 = ChatCryptoUtils.getSignedTimestampRequest(new Date().getTime(), iwkED, 1);
        
        
        String writeValueAsString = TkmTextUtils.getJacksonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(signedTimestampRequest);
        String writeValueAsString1 = TkmTextUtils.getJacksonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(signedTimestampRequest1);
        
        SignedMessageBean verifySignedMessage = ChatCryptoUtils.verifySignedMessage(writeValueAsString);
        SignedMessageBean verifySignedMessage1 = ChatCryptoUtils.verifySignedMessage(writeValueAsString1);
        
        
        assertNotNull(verifySignedMessage.getSignature());
        assertNotNull(verifySignedMessage1.getSignature());
        log.info("signed message signature: {} ", verifySignedMessage.getSignature());
        log.info("signed message from: {} ", verifySignedMessage.getFrom());
        log.info("signed message signature: {} ", verifySignedMessage1.getSignature());
        log.info("signed message from: {} ", verifySignedMessage1.getFrom());
        
        
    }

}
