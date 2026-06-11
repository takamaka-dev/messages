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
package io.takamaka.messages.chat.options;

import io.takamaka.messages.chat.core.NonceResponseBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.exception.MessageException;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.messages.utils.ChatUtils;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Round-trips the user-options signed requests through the
 * sign &rarr; serialize &rarr; parse &rarr; verify path and asserts the
 * canonical-JSON signature holds (and that tampering breaks it).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class UserOptionsSignVerifyTest {

    public static InstanceWalletKeystoreInterface iwkED;
    public static final String PASSWORD = "superSecretPassword";

    @BeforeAll
    public static void setUpClass() throws WalletException {
        iwkED = new InstanceWalletKeyStoreBCED25519("user_options_test_wallet", PASSWORD);
    }

    private static NonceResponseBean freshNonce() {
        return new NonceResponseBean(UUID.randomUUID().toString(), System.currentTimeMillis(), 60_000L);
    }

    private static String toJson(Object bean) throws Exception {
        return TkmTextUtils.getJacksonMapper().writeValueAsString(bean);
    }

    @Test
    public void setUserOption_roundTrip_verifies() throws Exception {
        SetUserOptionRequestBean original = ChatCryptoUtils.getSignedSetUserOptionRequest(
                freshNonce(),
                UserOptionsConstants.READ_NOTIFICATIONS,
                UserOptionsConstants.READ_NOTIFICATIONS_V1,
                "{\"enabled\":true}",
                System.currentTimeMillis(),
                iwkED, 0);

        String json = toJson(original);

        SetUserOptionRequestBean parsed = ChatUtils.fromJsonToSetUserOptionRequestBean(json);
        assertEquals(original.getSignature(), parsed.getSignature());
        assertEquals(UserOptionsConstants.READ_NOTIFICATIONS, parsed.getPl().getParameterName());
        assertEquals("{\"enabled\":true}", parsed.getPl().getParameterJson());

        // verify the envelope (returns the typed bean, throws if invalid)
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
    }

    @Test
    public void setUserOption_tamperedValue_rejected() throws Exception {
        SetUserOptionRequestBean original = ChatCryptoUtils.getSignedSetUserOptionRequest(
                freshNonce(),
                UserOptionsConstants.READ_NOTIFICATIONS,
                UserOptionsConstants.READ_NOTIFICATIONS_V1,
                "{\"enabled\":false}",
                System.currentTimeMillis(),
                iwkED, 0);

        // flip the signed value after signing -> signature must no longer verify
        original.getPl().setParameterJson("{\"enabled\":true}");
        String tampered = toJson(original);

        assertThrows(ChatMessageException.class,
                () -> ChatCryptoUtils.verifySignedMessage(tampered));
    }

    @Test
    public void resetUserOptions_roundTrip_verifies() throws Exception {
        ResetUserOptionsRequestBean original = ChatCryptoUtils.getSignedResetUserOptionsRequest(
                freshNonce(), iwkED, 0);
        String json = toJson(original);

        ResetUserOptionsRequestBean parsed = ChatUtils.fromJsonToResetUserOptionsRequestBean(json);
        assertEquals(original.getSignature(), parsed.getSignature());
        assertNotNull(parsed.getPl().getNonce());
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
    }

    @Test
    public void getUserOptions_roundTrip_verifies() throws Exception {
        GetUserOptionsRequestBean original = ChatCryptoUtils.getSignedGetUserOptionsRequest(
                UserOptionsConstants.READ_NOTIFICATIONS, System.currentTimeMillis(), iwkED, 0);
        String json = toJson(original);

        GetUserOptionsRequestBean parsed = ChatUtils.fromJsonToGetUserOptionsRequestBean(json);
        assertEquals(UserOptionsConstants.READ_NOTIFICATIONS, parsed.getPl().getParameterName());
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
    }

    @Test
    public void getUserOptions_allOptions_nullParam_verifies() throws Exception {
        GetUserOptionsRequestBean original = ChatCryptoUtils.getSignedGetUserOptionsRequest(
                null, System.currentTimeMillis(), iwkED, 0);
        String json = toJson(original);
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
    }

    @Test
    public void getUserOptionPeer_roundTrip_verifies() throws Exception {
        String targetPk = iwkED.getPublicKeyAtIndexURL64(1);
        GetUserOptionPeerRequestBean original = ChatCryptoUtils.getSignedGetUserOptionPeerRequest(
                targetPk, UserOptionsConstants.READ_NOTIFICATIONS, System.currentTimeMillis(), iwkED, 0);
        String json = toJson(original);

        GetUserOptionPeerRequestBean parsed = ChatUtils.fromJsonToGetUserOptionPeerRequestBean(json);
        assertEquals(targetPk, parsed.getPl().getTargetPublicKey());
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
    }
}
