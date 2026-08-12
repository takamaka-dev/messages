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

import io.takamaka.messages.chat.constant.ChatServerEndpoints;
import io.takamaka.messages.chat.core.NonceResponseBean;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.messages.utils.ChatUtils;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Wire contract for the FCM token deletion routes: the response bean's snake_case
 * field names (clients read {@code deleted_count}) and the fact that the delete
 * routes reuse the registration envelope verbatim — including the delete-all case,
 * where the caller may have no live device token to put in {@code fcm_token}.
 *
 * @author Iris Dimni info@takamaka.io
 */
@Slf4j
public class FcmTokenDeletionContractTest {

    public static InstanceWalletKeystoreInterface iwkED;
    public static final String PASSWORD = "superSecretPassword";

    @BeforeAll
    public static void setUpClass() throws WalletException {
        iwkED = new InstanceWalletKeyStoreBCED25519("fcm_token_delete_test_wallet", PASSWORD);
    }

    private static NonceResponseBean freshNonce() {
        return new NonceResponseBean(UUID.randomUUID().toString(), System.currentTimeMillis(), 60_000L);
    }

    @Test
    public void deleteRoutes_areDistinctLowercasePaths() {
        assertEquals("deletefcmtoken", ChatServerEndpoints.DELETE_FCM_TOKEN);
        assertEquals("deleteallfcmtokens", ChatServerEndpoints.DELETE_ALL_FCM_TOKENS);
        assertFalse(ChatServerEndpoints.DELETE_FCM_TOKEN.equals(ChatServerEndpoints.UNREGISTER_FCM_TOKEN));
    }

    @Test
    public void deletionResponse_success_carriesSnakeCaseCount() throws Exception {
        String json = TkmTextUtils.getJacksonMapper()
                .writeValueAsString(FcmTokenDeletionResponseBean.success(3));

        assertTrue(json, json.contains("\"deleted_count\":3"));
        assertTrue(json, json.contains("\"success\":true"));

        FcmTokenDeletionResponseBean parsed = TkmTextUtils.getJacksonMapper()
                .readValue(json, FcmTokenDeletionResponseBean.class);
        assertEquals(Long.valueOf(3L), parsed.getDeletedCount());
        assertNull(parsed.getErrorCode());
    }

    /**
     * Deleting something that is not there is a success with a zero count, not an
     * error — a client retrying a delete must not have to special-case it.
     */
    @Test
    public void deletionResponse_nothingMatched_isSuccessWithZero() {
        FcmTokenDeletionResponseBean response = FcmTokenDeletionResponseBean.success(0);
        assertTrue(response.isSuccess());
        assertEquals(Long.valueOf(0L), response.getDeletedCount());
        assertNull(response.getErrorCode());
    }

    @Test
    public void deletionResponse_error_hasCodeAndNoCount() throws Exception {
        String json = TkmTextUtils.getJacksonMapper()
                .writeValueAsString(FcmTokenDeletionResponseBean.error("NONCE_INVALID", "nonce unknown or expired"));

        FcmTokenDeletionResponseBean parsed = TkmTextUtils.getJacksonMapper()
                .readValue(json, FcmTokenDeletionResponseBean.class);
        assertFalse(parsed.isSuccess());
        assertEquals("NONCE_INVALID", parsed.getErrorCode());
        assertNull(parsed.getDeletedCount());
    }

    /**
     * The delete-all route selects rows by the envelope's {@code from} key, so a
     * caller that no longer holds a device token can sign with a blank one. That
     * envelope must still verify — the signature covers the content bean as-is.
     */
    @Test
    public void deleteAll_blankFcmToken_stillVerifies() throws Exception {
        FcmTokenRegistrationRequestBean request = ChatCryptoUtils.getSignedFcmTokenRegistrationRequest(
                freshNonce(),
                "",
                "android",
                null,
                iwkED, 0);

        String json = TkmTextUtils.getJacksonMapper().writeValueAsString(request);

        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
        assertEquals("", ChatUtils.fromJsonToFcmTokenRegistrationRequestBean(json)
                .getFcmTokenRegistrationSignedContentBean().getFcmToken());
    }
}
