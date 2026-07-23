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
package io.takamaka.messages.chat.fcm;

import io.takamaka.messages.chat.core.NonceResponseBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.utils.CHAT_MESSAGE_TYPES;
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
 * Round-trips the FCM token-registration signed request through the
 * sign &rarr; serialize &rarr; parse &rarr; verify path and asserts the
 * canonical-JSON signature holds (and that tampering breaks it). This is the
 * Java reference vector that the Flutter client's canonicalization must match.
 *
 * @author Iris Dimni iris.dimni@takamaka.io
 */
@Slf4j
public class FcmTokenRegistrationSignVerifyTest {

    public static InstanceWalletKeystoreInterface iwkED;
    public static final String PASSWORD = "superSecretPassword";

    @BeforeAll
    public static void setUpClass() throws WalletException {
        iwkED = new InstanceWalletKeyStoreBCED25519("fcm_token_test_wallet", PASSWORD);
    }

    private static NonceResponseBean freshNonce() {
        return new NonceResponseBean(UUID.randomUUID().toString(), System.currentTimeMillis(), 60_000L);
    }

    private static String toJson(Object bean) throws Exception {
        return TkmTextUtils.getJacksonMapper().writeValueAsString(bean);
    }

    @Test
    public void fcmTokenRegistration_roundTrip_verifies() throws Exception {
        FcmTokenRegistrationRequestBean original = ChatCryptoUtils.getSignedFcmTokenRegistrationRequest(
                freshNonce(),
                "fake-fcm-token-abc123",
                "android",
                "device-42",
                iwkED, 0);

        String json = toJson(original);

        FcmTokenRegistrationRequestBean parsed = ChatUtils.fromJsonToFcmTokenRegistrationRequestBean(json);
        assertEquals(original.getSignature(), parsed.getSignature());
        assertEquals(CHAT_MESSAGE_TYPES.FCM_TOKEN_REGISTRATION.name(), parsed.getMessageType());
        assertEquals("fake-fcm-token-abc123", parsed.getFcmTokenRegistrationSignedContentBean().getFcmToken());
        assertEquals("android", parsed.getFcmTokenRegistrationSignedContentBean().getPlatform());
        assertEquals("device-42", parsed.getFcmTokenRegistrationSignedContentBean().getDeviceId());

        // verify the envelope (returns the typed bean, throws if invalid)
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
    }

    @Test
    public void fcmTokenRegistration_nullDeviceId_verifies() throws Exception {
        FcmTokenRegistrationRequestBean original = ChatCryptoUtils.getSignedFcmTokenRegistrationRequest(
                freshNonce(),
                "token-no-device",
                "ios",
                null,
                iwkED, 0);
        String json = toJson(original);
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
    }

    @Test
    public void fcmTokenRegistration_tamperedToken_rejected() throws Exception {
        FcmTokenRegistrationRequestBean original = ChatCryptoUtils.getSignedFcmTokenRegistrationRequest(
                freshNonce(),
                "original-token",
                "android",
                null,
                iwkED, 0);

        // flip the signed FCM token after signing -> signature must no longer verify
        original.getFcmTokenRegistrationSignedContentBean().setFcmToken("swapped-token");
        String tampered = toJson(original);

        assertThrows(ChatMessageException.class,
                () -> ChatCryptoUtils.verifySignedMessage(tampered));
    }
}
