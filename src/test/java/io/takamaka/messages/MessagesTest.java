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

import io.takamaka.messages.chat.NonceBean;
import io.takamaka.messages.chat.requests.RegisterUserRequestBean;
import io.takamaka.messages.chat.requests.RegisterUserRequestSignedContentBean;
import io.takamaka.messages.utils.ChatUtils;
import io.takamaka.messages.utils.SimpleRequestHelper;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeyStoreBCRSA4096ENC;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.TkmCypherProviderBCRSA4096ENC;
import io.takamaka.wallet.exceptions.WalletException;
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
        RegisterUserRequestBean signedRegisteredUserRequests = ChatUtils.getSignedRegisteredUserRequests(iwkED, 0, new RegisterUserRequestSignedContentBean(new NonceBean(UUID.randomUUID().toString(), Long.MIN_VALUE, Long.MIN_VALUE), iwkRSA.getPublicKeyAtIndexURL64(0)));
        log.info("uud signed message {} ", ChatUtils.getObjectJsonPretty(signedRegisteredUserRequests));
        String sigReqJson = ChatUtils.getObjectJsonPretty(signedRegisteredUserRequests);
        RegisterUserRequestBean fromJsonRegisterUserRequestBean = ChatUtils.fromJsonToRegisterUserRequestBean(sigReqJson);
        assertEquals(fromJsonRegisterUserRequestBean, signedRegisteredUserRequests);
        
    }
    
}