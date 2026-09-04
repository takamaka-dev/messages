/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.chat.quota;

import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.utils.CHAT_MESSAGE_TYPES;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.messages.utils.ChatUtils;
import io.takamaka.messages.utils.SimpleRequestHelper;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** {@code withdrawattachment} envelope (DR-037): signs, round-trips, verifies, refuses tampering. */
public class WithdrawAttachmentSignVerifyTest {

    private static InstanceWalletKeystoreInterface iwkED;
    private static final String EFH_A = "9bd5e9d082fe92d83e970190c96773719e225d7b75c9c63cca63659f45ebea14";
    private static final String EFH_B = "462badf3f1f109aa44bba3369afb371f5d29bef14e929c1e4bda3f9ede6c7321";

    @BeforeAll
    public static void setUpClass() throws WalletException {
        iwkED = new InstanceWalletKeyStoreBCED25519("withdraw_attachment_test_wallet", "superSecretPassword");
    }

    private static String toJson(Object bean) throws Exception {
        return TkmTextUtils.getJacksonMapper().writeValueAsString(bean);
    }

    @Test
    public void withdraw_roundTrip_verifies_andTheSignUnitIsPinned() throws Exception {
        WithdrawAttachmentRequestBean original = ChatCryptoUtils.getSignedWithdrawAttachmentRequest(
                List.of(EFH_A, EFH_B), 1_788_451_528_307L, iwkED, 0);
        assertEquals(CHAT_MESSAGE_TYPES.WITHDRAW_ATTACHMENT.name(), original.getMessageType());
        // The sign unit: JCS of {cts, efh[]} — keys sorted, list order preserved.
        assertEquals("{\"cts\":1788451528307,\"efh\":[\"" + EFH_A + "\",\"" + EFH_B + "\"]}",
                SimpleRequestHelper.getCanonicalJson(original.getPl()));
        String json = toJson(original);
        WithdrawAttachmentRequestBean parsed = ChatUtils.fromJsonToWithdrawAttachmentRequestBean(json);
        assertEquals(original.getSignature(), parsed.getSignature());
        assertEquals(List.of(EFH_A, EFH_B), parsed.getPl().getEncryptedFileHashes());
        Object verified = ChatCryptoUtils.verifySignedMessage(json);
        assertTrue("verify must return the typed bean", verified instanceof WithdrawAttachmentRequestBean);
    }

    @Test
    public void withdraw_tamperedHashList_rejected() throws Exception {
        WithdrawAttachmentRequestBean original = ChatCryptoUtils.getSignedWithdrawAttachmentRequest(
                List.of(EFH_A), 1_000L, iwkED, 0);
        original.getPl().setEncryptedFileHashes(List.of(EFH_B));   // point the signed command at another blob
        String tampered = toJson(original);
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(tampered));
    }

    @Test
    public void withdraw_substitutedFrom_rejected() throws Exception {
        WithdrawAttachmentRequestBean original = ChatCryptoUtils.getSignedWithdrawAttachmentRequest(
                List.of(EFH_A), 1_000L, iwkED, 0);
        original.setFrom(iwkED.getPublicKeyAtIndexURL64(1));   // another identity may not withdraw my blob
        String forged = toJson(original);
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(forged));
    }

    @Test
    public void response_roundTrip_keepsOutcomesAndQuota() throws Exception {
        WithdrawAttachmentResponseBean r = new WithdrawAttachmentResponseBean(
                List.of(new WithdrawAttachmentOutcomeBean(EFH_A, WithdrawAttachmentOutcomeBean.STATUS_WITHDRAWN, 54_762L),
                        new WithdrawAttachmentOutcomeBean(EFH_B, WithdrawAttachmentOutcomeBean.STATUS_NOT_OWNED, 0L)),
                new StorageQuotaStatusResponseBean("u", 2_013_264L, 1, 21_474_836_480L, 21_472_823_216L, 1L, true, true, 2L),
                3L, null);
        String json = toJson(r);
        WithdrawAttachmentResponseBean back = TkmTextUtils.getJacksonMapper().readValue(json, WithdrawAttachmentResponseBean.class);
        assertEquals(r, back);
        assertTrue(json.contains("\"freed_bytes\":54762"));
        assertEquals(WithdrawAttachmentResponseBean.ERR_CLOCK_WINDOW, WithdrawAttachmentResponseBean.rejected("clock_window").getError());
    }
}
