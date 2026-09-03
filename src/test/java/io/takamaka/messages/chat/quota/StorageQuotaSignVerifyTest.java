/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.chat.quota;

import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.utils.CHAT_MESSAGE_TYPES;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.messages.utils.ChatUtils;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.TkmTextUtils;
import lombok.extern.slf4j.Slf4j;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@code getstoragequota} envelope: signs, round-trips through JSON, verifies, and refuses tampering
 * — the "three things" (enum arm, verify arm, parser) wired together.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class StorageQuotaSignVerifyTest {

    public static InstanceWalletKeystoreInterface iwkED;
    public static final String PASSWORD = "superSecretPassword";

    @BeforeAll
    public static void setUpClass() throws WalletException {
        iwkED = new InstanceWalletKeyStoreBCED25519("storage_quota_test_wallet", PASSWORD);
    }

    private static String toJson(Object bean) throws Exception {
        return TkmTextUtils.getJacksonMapper().writeValueAsString(bean);
    }

    @Test
    public void getStorageQuota_roundTrip_verifies() throws Exception {
        final long cts = System.currentTimeMillis();
        GetStorageQuotaRequestBean original = ChatCryptoUtils.getSignedGetStorageQuotaRequest(cts, iwkED, 0);
        assertEquals(CHAT_MESSAGE_TYPES.GET_STORAGE_QUOTA.name(), original.getMessageType());
        assertEquals(iwkED.getPublicKeyAtIndexURL64(0), original.getFrom());
        String json = toJson(original);
        GetStorageQuotaRequestBean parsed = ChatUtils.fromJsonToGetStorageQuotaRequestBean(json);
        assertEquals(original.getSignature(), parsed.getSignature());
        assertEquals(Long.valueOf(cts), parsed.getPl().getClientTimestamp());
        Object verified = ChatCryptoUtils.verifySignedMessage(json);
        assertNotNull(verified);
        assertTrue("verify must return the typed quota bean", verified instanceof GetStorageQuotaRequestBean);
    }

    @Test
    public void getStorageQuota_tamperedTimestamp_rejected() throws Exception {
        GetStorageQuotaRequestBean original = ChatCryptoUtils.getSignedGetStorageQuotaRequest(1_000L, iwkED, 0);
        original.getPl().setClientTimestamp(2_000L);
        String tampered = toJson(original);
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(tampered));
    }

    @Test
    public void getStorageQuota_substitutedFrom_rejected() throws Exception {
        // The server answers for `from`; a signature must not transfer to another identity.
        GetStorageQuotaRequestBean original = ChatCryptoUtils.getSignedGetStorageQuotaRequest(System.currentTimeMillis(), iwkED, 0);
        original.setFrom(iwkED.getPublicKeyAtIndexURL64(1));
        String forged = toJson(original);
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(forged));
    }

    @Test
    public void statusResponse_roundTrip_keepsEveryField() throws Exception {
        StorageQuotaStatusResponseBean r = new StorageQuotaStatusResponseBean(
                "user", 2_068_026L, 2, 21_474_836_480L, 21_472_768_454L, 1_700_000_000_000L, true, true, 1_700_000_000_001L);
        String json = toJson(r);
        StorageQuotaStatusResponseBean back = TkmTextUtils.getJacksonMapper().readValue(json, StorageQuotaStatusResponseBean.class);
        assertEquals(r, back);
        assertTrue(json.contains("\"available_bytes\""));
        assertTrue(json.contains("\"initialized\""));
    }
}
