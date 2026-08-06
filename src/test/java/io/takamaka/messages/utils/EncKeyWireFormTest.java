package io.takamaka.messages.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.takamaka.messages.chat.conversation.TopicKeyDistributionItemBean;
import io.takamaka.messages.chat.user.RegisterUserRequestBean;
import io.takamaka.messages.chat.user.RegisterUserRequestSignedContentBean;
import io.takamaka.messages.chat.core.NonceResponseBean;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeyStoreBCRSA4096ENC256;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.TkmSignUtils;

/**
 * F11 — the wire form of {@code enc_key}, asserted at the ONE place that produces it.
 *
 * <p>{@link ChatCryptoUtils#getInviteForUser} is the sole production encoder of the RSA-wrapped
 * conversation key in the entire estate. Everything else — the shell, chat-web-gui, rsclient — reaches the
 * wire through it, so this is the only place the contract can be pinned once and hold everywhere.
 *
 * <p><b>The failure this prevents.</b> The RSA providers return STANDARD base64, because that is what
 * BouncyCastle's {@code Base64.toBase64String} returns — never a protocol decision. Letting it reach the
 * wire made {@code enc_key} the lone standard-base64 field in an otherwise URL-safe envelope, in the same
 * JSON object as {@code enc_key_hash}, which is URL-safe. A client that implemented the documented
 * convention faithfully produced conversations no Java client could open, and nothing in any suite could
 * see it.
 *
 * <p><b>Why these are character-set assertions and not round-trips.</b> A round-trip
 * (encrypt → decrypt → compare) passes under EITHER alphabet, because the decoder now accepts both. It is
 * structurally blind to this defect — which is exactly how the original divergence shipped. If you replace
 * these with a round-trip, the suite stays green while the protocol breaks.
 */
public class EncKeyWireFormTest {

    private static InstanceWalletKeystoreInterface iwkED;
    private static InstanceWalletKeystoreInterface iwkRSA;
    private static final String PASSWORD = "enc-key-wire-form-test";

    /** RSA-4096 ciphertext = 512 bytes = 684 base64 chars in EITHER alphabet — length can never catch this. */
    private static final int RSA_4096_CIPHER_BYTES = 512;

    @BeforeAll
    public static void setUpClass() throws WalletException {
        iwkED = new InstanceWalletKeyStoreBCED25519("enc_key_wire_form_wallet", PASSWORD);
        iwkRSA = new InstanceWalletKeyStoreBCRSA4096ENC256("enc_key_wire_form_wallet", PASSWORD);
    }

    private static TopicKeyDistributionItemBean buildInvite() throws Exception {
        RegisterUserRequestBean member = ChatCryptoUtils.getSignedRegisteredUserRequests(
                iwkED,
                0,
                new RegisterUserRequestSignedContentBean(
                        new NonceResponseBean(UUID.randomUUID().toString(), Long.MIN_VALUE, Long.MIN_VALUE),
                        iwkRSA.getPublicKeyAtIndexURL64(0),
                        iwkRSA.getWalletCypher().name()));
        return ChatCryptoUtils.getInviteForUser(member, "a-topic-symmetric-key-for-the-wire-form-test");
    }

    @Test
    @DisplayName("enc_key is emitted as Base64URL with '.' padding — never the standard alphabet")
    public void encKeyIsUrlSafeWithDotPadding() throws Exception {
        String encKey = buildInvite().getEncryptedTopicKey();

        assertFalse(encKey.contains("+"), "'+' belongs to RFC 4648 §4; enc_key uses the URL-safe alphabet");
        assertFalse(encKey.contains("/"), "'/' belongs to RFC 4648 §4; enc_key uses the URL-safe alphabet");
        assertFalse(encKey.contains("="), "padding is '.', not '='; '=' would be a THIRD form, matching "
                + "neither the legacy wire nor the wallet app");
        assertTrue(encKey.matches("^[A-Za-z0-9_-]+\\.{0,2}$"),
                "enc_key must be [A-Za-z0-9_-] with '.' padding only, was: "
                        + encKey.substring(Math.max(0, encKey.length() - 12)));
    }

    @Test
    @DisplayName("enc_key sits in the SAME alphabet as enc_key_hash beside it — the split was the bug")
    public void encKeyAgreesWithItsSiblingField() throws Exception {
        TopicKeyDistributionItemBean invite = buildInvite();
        String encKey = invite.getEncryptedTopicKey();
        String encKeyHash = invite.getEncryptionKeyHash();

        // enc_key_hash was ALWAYS URL-safe (TkmSignUtils.Hash256B64URL). enc_key now matches it. Two
        // adjacent fields in one JSON object disagreeing on encoding is what nobody could see by reading
        // one side of the wire.
        assertTrue(encKeyHash.matches("^[A-Za-z0-9_-]+\\.{0,2}$"), "enc_key_hash is URL-safe (unchanged)");
        assertTrue(encKey.matches("^[A-Za-z0-9_-]+\\.{0,2}$"), "enc_key now agrees with it");
    }

    @Test
    @DisplayName("the ciphertext survives the re-encode: 512 bytes, decodable, unchanged")
    public void reEncodingPreservesTheCiphertext() throws Exception {
        String encKey = buildInvite().getEncryptedTopicKey();

        byte[] bytes = TkmSignUtils.fromAnyB64ToByteArray(encKey);
        assertEquals(RSA_4096_CIPHER_BYTES, bytes.length,
                "re-encoding must change the ALPHABET only, never the payload");

        // And it must still be readable by the tolerant decoder every consumer now uses.
        assertDoesNotThrow(() -> TkmSignUtils.fromAnyB64ToByteArray(encKey));
    }

    @Test
    @DisplayName("the legacy STANDARD form is still decodable — old conversations can never be re-encoded")
    public void legacyStandardFormRemainsReadable() {
        // A real enc_key written by chat-web-gui before the flip, captured from rschat on 2026-08-05.
        // It is inside a signed, permanently-stored envelope, so it will keep arriving forever.
        final String legacy =
                "Ton6nMJLcungeB38uJfPvPzvanZH2AFENaZT2Rs3aERcHzlw5L9lnlvoljYR4Sib16QyDM5dkkWD"
                + "E3M5BBgjCvFrxzh2FycWBNDEcduzso3okutb31IW246qFRanSMTtf2Y02HIdZDnqW9unIh5D/J/+"
                + "AKJ7SrBK4xj4rRsWnCLFvHto7YUlHgRfgVI1I6MVvtunCkcFsjnniIepkxrMrJ0zJyggyE3T8RxK"
                + "9fCDLjv6UKT8Tegkgy1N84tSqUsqaqt8I2GX+jpr7ORPhhgFCsNfWcPKeMjnf1y1aG+d3zSLIG91"
                + "0KqgvMPq0TNAKLSmDA3dp4ZEWaaRDT5LMEgXTMc1zgm+RB90nboGUC7b0hbkR+7EeLwb/3touWkc"
                + "1rntQNYxkbT0erOacfi9VA4bpr93eb2wzUQ76Nh30mChFKyJHyW9xzfmcFECBoZWlpnSoVSaOqdb"
                + "brJZQBSdfKQLWAWqxmHYtzY0QWk16Aj9BofywsgAuusfAxToDAiKHvjvRU9b1CVR3eOOe6HzsNWi"
                + "nYJK9hHmm9uJ8k+sk3+rJxJHAAz/8c5OMWBxYHdyqRrvTKoGbq0cDDqr3smStFiycpF/fQbwf+jc"
                + "Zq88+Fo/SftJYqhA7VqewCLLu8Umb7ckxDIONy1gEXW+KTQvQ5lYYaB7XPNDWqbGVxAREz+Cei8=";
        assertEquals(RSA_4096_CIPHER_BYTES, TkmSignUtils.fromAnyB64ToByteArray(legacy).length);
    }
}
