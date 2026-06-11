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
package io.takamaka.messages.chat.receipt;

import io.takamaka.messages.chat.core.NonceResponseBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.messages.utils.ChatUtils;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Read-receipt crypto + cursor-merge unit tests (Deliverable B, Messages).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class ReadReceiptTest {

    static InstanceWalletKeystoreInterface iwkED;
    static final String CONV = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";
    static final String CONV_KEY = "conversation-symmetric-key-deadbeef";
    // a realistic 88-char base64url message signature (the watermark plaintext)
    static final String MSG_SIG = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_abcdefghijklmnopqrstuvwx..";

    @BeforeAll
    static void setUp() throws WalletException {
        iwkED = new InstanceWalletKeyStoreBCED25519("read_receipt_test_wallet", "pw");
    }

    private static String toJson(Object bean) throws Exception {
        return TkmTextUtils.getJacksonMapper().writeValueAsString(bean);
    }

    @Test
    void receipt_signVerifyRoundTrip_andWatermarkDecrypts() throws Exception {
        ReadReceiptRequestBean rr = ChatCryptoUtils.getReadReceiptBean(iwkED, 0, CONV, CONV_KEY, MSG_SIG);
        assertEquals("READ_RECEIPT", rr.getMessageType());
        assertEquals(CONV, rr.getPl().getConversationHashName());
        assertEquals("v0_1_a", rr.getPl().getCipherVersion());
        assertEquals("1.0", rr.getPl().getProtocolVersion());
        assertNull(rr.getServerTimestamp(), "ts is server-set at fan-out, null on the wire");

        String json = toJson(rr);
        // envelope verifies (returns typed bean)
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
        // parse round-trip
        ReadReceiptRequestBean parsed = ChatUtils.fromJsonToReadReceiptRequestBean(json);
        assertEquals(rr.getSignature(), parsed.getSignature());

        // verify-then-decrypt: watermark comes back exactly
        String watermark = ChatCryptoUtils.decryptReadReceiptWatermark(parsed.getPl(), CONV_KEY);
        assertEquals(MSG_SIG, watermark);
    }

    @Test
    void receipt_tamperedCiphertext_rejected() throws Exception {
        ReadReceiptRequestBean rr = ChatCryptoUtils.getReadReceiptBean(iwkED, 0, CONV, CONV_KEY, MSG_SIG);
        rr.getPl().setConversationHashName(
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"); // flip a signed field
        String tampered = toJson(rr);
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(tampered));
    }

    @Test
    void receipt_randomIv_makesCiphertextUnlinkable() throws Exception {
        ReadReceiptRequestBean a = ChatCryptoUtils.getReadReceiptBean(iwkED, 0, CONV, CONV_KEY, MSG_SIG);
        ReadReceiptRequestBean b = ChatCryptoUtils.getReadReceiptBean(iwkED, 0, CONV, CONV_KEY, MSG_SIG);
        // same watermark, different random IV -> different ciphertext (semantic security, D4)
        assertFalse(a.getPl().getEncryptedWatermark().equals(b.getPl().getEncryptedWatermark()));
        // both still decrypt to the same watermark
        assertEquals(MSG_SIG, ChatCryptoUtils.decryptReadReceiptWatermark(a.getPl(), CONV_KEY));
        assertEquals(MSG_SIG, ChatCryptoUtils.decryptReadReceiptWatermark(b.getPl(), CONV_KEY));
    }

    @Test
    void subscribe_signVerifyRoundTrip() throws Exception {
        NonceResponseBean nonce = new NonceResponseBean(UUID.randomUUID().toString(), System.currentTimeMillis(), 60_000L);
        ReadReceiptSubscribeBean sub = ChatCryptoUtils.getReadReceiptSubscribeBean(nonce, 123L, iwkED, 0);
        assertEquals("RETRIEVE_READ_RECEIPTS", sub.getMessageType());
        String json = toJson(sub);
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
        ReadReceiptSubscribeBean parsed = ChatUtils.fromJsonToReadReceiptSubscribeBean(json);
        assertEquals(Long.valueOf(123L), parsed.getPl().getNotBefore());
        assertEquals(nonce.getNonce(), parsed.getPl().getNonce().getNonce());
    }

    // ---------------- merge logic (§12.2) ----------------

    @Test
    void merge_advancesOnNewer_ignoresOlder() {
        PeerReadCursor c = new PeerReadCursor();
        assertTrue(ReadReceiptCursorMerge.merge(c, "sigA", 100L));   // first resolve advances
        assertEquals("sigA", c.getLastReadMessageSignature());
        assertEquals(Long.valueOf(100L), c.getLastReadTimestamp());

        assertTrue(ReadReceiptCursorMerge.merge(c, "sigB", 200L));   // newer advances
        assertEquals("sigB", c.getLastReadMessageSignature());

        assertFalse(ReadReceiptCursorMerge.merge(c, "sigC", 150L));  // older ignored
        assertEquals("sigB", c.getLastReadMessageSignature());
        assertEquals(Long.valueOf(200L), c.getLastReadTimestamp());
    }

    @Test
    void merge_equalTimestamp_breaksBySignature() {
        PeerReadCursor c = new PeerReadCursor("sigM", 100L, null);
        assertFalse(ReadReceiptCursorMerge.merge(c, "sigA", 100L));  // "sigA" < "sigM" -> no advance
        assertEquals("sigM", c.getLastReadMessageSignature());
        assertTrue(ReadReceiptCursorMerge.merge(c, "sigZ", 100L));   // "sigZ" > "sigM" -> advance
        assertEquals("sigZ", c.getLastReadMessageSignature());
    }

    @Test
    void explicit_unresolved_parksSingleSlot_thenResolvesOnBackfill() {
        PeerReadCursor c = new PeerReadCursor();
        // unresolved watermark -> park, cursor untouched
        assertFalse(ReadReceiptCursorMerge.applyExplicit(c, "unknown1", null));
        assertEquals("unknown1", c.getUnknownSignature());
        assertNull(c.getLastReadMessageSignature());

        // a second unresolved overwrites the single slot (never a set)
        assertFalse(ReadReceiptCursorMerge.applyExplicit(c, "unknown2", null));
        assertEquals("unknown2", c.getUnknownSignature());

        // backfill resolves the parked unknown -> advance + clear slot
        assertTrue(ReadReceiptCursorMerge.resolveUnknownOnBackfill(c, 300L));
        assertEquals("unknown2", c.getLastReadMessageSignature());
        assertEquals(Long.valueOf(300L), c.getLastReadTimestamp());
        assertNull(c.getUnknownSignature());
    }

    @Test
    void explicit_resolved_clearsMatchingUnknownSlot() {
        PeerReadCursor c = new PeerReadCursor(null, null, "sigX");
        assertTrue(ReadReceiptCursorMerge.applyExplicit(c, "sigX", 400L)); // resolves to local msg
        assertEquals("sigX", c.getLastReadMessageSignature());
        assertNull(c.getUnknownSignature(), "matching parked unknown is cleared");
    }
}
