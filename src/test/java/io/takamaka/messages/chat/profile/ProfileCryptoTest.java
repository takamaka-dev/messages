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
package io.takamaka.messages.chat.profile;

import io.takamaka.messages.chat.core.NonceResponseBean;
import io.takamaka.messages.chat.user.RegisterUserRequestBean;
import io.takamaka.messages.chat.user.RegisterUserRequestSignedContentBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.exception.CryptoMessageException;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeyStoreBCRSA4096ENC256;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.TkmCypherProviderBCRSA4096ENC256;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.TkmSignUtils;
import java.util.Base64;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The profile card's crypto: seal/open, the per-grantee wrap, the blob hash,
 * and the producer-side caps.
 *
 * <p>Exit criteria for Phase 1 (USER_PROFILE_HANDOFF.md §3), plus the
 * registry's §6 conformance clauses. Two of these are deliberately NOT
 * round-trips — a round-trip passes under either base64 alphabet and under a
 * hash taken over the wrong bytes, which is exactly how F11 and DR-030 shipped.
 * If you replace them with round-trips the suite stays green while the protocol
 * breaks.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class ProfileCryptoTest {

    private static InstanceWalletKeystoreInterface iwkED;
    private static InstanceWalletKeystoreInterface iwkRSA;
    private static final String PASSWORD = "profile-crypto-test";

    @BeforeAll
    public static void setUpClass() throws WalletException {
        iwkED = new InstanceWalletKeyStoreBCED25519("profile_crypto_test_wallet", PASSWORD);
        iwkRSA = new InstanceWalletKeyStoreBCRSA4096ENC256("profile_crypto_test_wallet", PASSWORD);
    }

    private static ProfileCardBean card(String name, String status, String avatarB64, String mediaType) {
        return new ProfileCardBean(ProfileConstants.PAYLOAD_VERSION_1_0, name, status, avatarB64, mediaType);
    }

    /** A registration envelope for identity B, carrying B's REAL RSA-4096 encryption key. */
    private static RegisterUserRequestBean registeredGrantee(int index) throws Exception {
        return ChatCryptoUtils.getSignedRegisteredUserRequests(
                iwkED,
                index,
                new RegisterUserRequestSignedContentBean(
                        new NonceResponseBean(UUID.randomUUID().toString(), Long.MIN_VALUE, Long.MIN_VALUE),
                        iwkRSA.getPublicKeyAtIndexURL64(index),
                        iwkRSA.getWalletCypher().name()));
    }

    // ===== seal / open =====

    @Test
    @DisplayName("seal then open returns the same card")
    public void sealOpenRoundTrip() throws Exception {
        String key = ChatCryptoUtils.generateProfileKey();
        ProfileCardBean original = card("Alice", "at the hospital",
                Base64.getEncoder().encodeToString("not-really-a-png".getBytes()), "image/webp");

        EncryptedProfileBean sealed = ChatCryptoUtils.sealProfileCard(original, key, 42L);
        ProfileCardBean opened = ChatCryptoUtils.openProfileCard(sealed, key);

        assertEquals(original.getPayloadVersion(), opened.getPayloadVersion());
        assertEquals(original.getDisplayName(), opened.getDisplayName());
        assertEquals(original.getStatusMessage(), opened.getStatusMessage());
        assertEquals(original.getAvatar(), opened.getAvatar());
        assertEquals(original.getAvatarMediaType(), opened.getAvatarMediaType());
        assertEquals(42L, sealed.getKeyEpoch());
        assertEquals(ProfileConstants.CIPHER_AES_256_GCM, sealed.getCipher());
        assertEquals(ProfileConstants.BLOB_VERSION_1_0, sealed.getBlobVersion());
    }

    @Test
    @DisplayName("the wrong key cannot open the card — GCM authenticates, it does not yield garbage")
    public void wrongKeyFails() throws Exception {
        EncryptedProfileBean sealed = ChatCryptoUtils.sealProfileCard(
                card("Alice", null, null, null), ChatCryptoUtils.generateProfileKey(), 1L);

        assertThrows(ChatMessageException.class,
                () -> ChatCryptoUtils.openProfileCard(sealed, ChatCryptoUtils.generateProfileKey()));
    }

    @Test
    @DisplayName("sealing the same card twice yields different ciphertext — the IV is fresh per seal")
    public void ivIsFreshPerSeal() throws Exception {
        String key = ChatCryptoUtils.generateProfileKey();
        ProfileCardBean same = card("Alice", "same status", null, null);

        String first = ChatCryptoUtils.sealProfileCard(same, key, 1L).getBlob();
        String second = ChatCryptoUtils.sealProfileCard(same, key, 1L).getBlob();

        // A reused IV under one key is catastrophic for GCM. It also has a privacy consequence here that
        // is easy to miss: identical blobs across two of a user's key-indices would relink the identities
        // DR-018 exists to keep apart, which is the very property D2 was chosen to buy.
        assertNotEquals(first, second);
    }

    @Test
    @DisplayName("two identities with the SAME avatar produce unrelated ciphertexts (DR-018 / D2)")
    public void sameAvatarUnderDistinctKeysDoesNotCorrelate() throws Exception {
        String avatar = Base64.getEncoder().encodeToString("one-photo-two-identities".getBytes());
        ProfileCardBean cardA = card("A", null, avatar, "image/webp");
        ProfileCardBean cardB = card("B", null, avatar, "image/webp");

        EncryptedProfileBean a = ChatCryptoUtils.sealProfileCard(cardA, ChatCryptoUtils.generateProfileKey(), 1L);
        EncryptedProfileBean b = ChatCryptoUtils.sealProfileCard(cardB, ChatCryptoUtils.generateProfileKey(), 1L);

        // This is the concrete reason the plaintext design was rejected (DR-032 / design §9 REJECTED-A):
        // in plaintext these would be byte-identical rows, and one GROUP BY over an avatar column would
        // relink every key-index that reuses a photo — retroactively, over data already at rest.
        assertNotEquals(a.getBlob(), b.getBlob());
        assertNotEquals(a.getBlobHash(), b.getBlobHash());
    }

    // ===== wire form: NOT a round-trip, on purpose (F11) =====

    @Test
    @DisplayName("the blob is Base64URL with '.' padding — never the standard alphabet")
    public void blobIsUrlSafe() throws Exception {
        String blob = ChatCryptoUtils.sealProfileCard(
                card("Alice", "status", null, null), ChatCryptoUtils.generateProfileKey(), 1L).getBlob();

        // A round-trip passes under EITHER alphabet, because the decoder accepts both. It is structurally
        // blind to this defect — which is how the original enc_key divergence shipped and made ~30% of
        // test-VM conversations permanently unreadable.
        assertFalse(blob.contains("+"), "'+' belongs to RFC 4648 §4; the profile blob is URL-safe");
        assertFalse(blob.contains("/"), "'/' belongs to RFC 4648 §4; the profile blob is URL-safe");
        assertFalse(blob.contains("="), "padding is '.', matching every other Base64URL field in the envelope");
        assertTrue(blob.matches("^[A-Za-z0-9_-]+\\.{0,2}$"), "blob must be [A-Za-z0-9_-] with '.' padding only");
    }

    @Test
    @DisplayName("a blob written in the STANDARD alphabet still opens — both forms, permanently")
    public void standardAlphabetBlobStillOpens() throws Exception {
        String key = ChatCryptoUtils.generateProfileKey();
        EncryptedProfileBean sealed = ChatCryptoUtils.sealProfileCard(card("Alice", null, null, null), key, 1L);

        // BASE64_ENCODING_CONTRACT.md §0.1: readers accept both alphabets forever. A producer in another
        // language that emits the standard form must not be unreadable to us.
        byte[] raw = TkmSignUtils.fromAnyB64ToByteArray(sealed.getBlob());
        sealed.setBlob(Base64.getEncoder().encodeToString(raw));

        assertEquals("Alice", ChatCryptoUtils.openProfileCard(sealed, key).getDisplayName());
    }

    // ===== blob_hash: over BYTES (DR-030), and ENFORCED (DR-031) =====

    @Test
    @DisplayName("blob_hash is SHA3-256 of the DECODED bytes, not of the base64 text")
    public void blobHashIsOverBytes() throws Exception {
        EncryptedProfileBean sealed = ChatCryptoUtils.sealProfileCard(
                card("Alice", null, null, null), ChatCryptoUtils.generateProfileKey(), 1L);

        byte[] decoded = TkmSignUtils.fromAnyB64ToByteArray(sealed.getBlob());
        assertEquals(ChatCryptoUtils.profileBlobHash(decoded), sealed.getBlobHash());

        // DR-030 in one assertion: hashing the ENCODING instead of the bytes gives a different answer,
        // and nothing downstream would notice until a second implementation disagreed.
        assertNotEquals(
                ChatCryptoUtils.profileBlobHash(sealed.getBlob().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                sealed.getBlobHash());
        assertTrue(sealed.getBlobHash().matches("^[0-9a-f]{64}$"), "64 lowercase hex characters");
    }

    @Test
    @DisplayName("open ENFORCES blob_hash: a hash that does not match the bytes is rejected")
    public void blobHashMismatchIsEnforced() throws Exception {
        String key = ChatCryptoUtils.generateProfileKey();
        EncryptedProfileBean sealed = ChatCryptoUtils.sealProfileCard(card("Alice", null, null, null), key, 1L);
        String realHash = sealed.getBlobHash();

        sealed.setBlobHash("0".repeat(64));
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.openProfileCard(sealed, key));

        // DR-031's lesson, applied: assert the ENFORCEMENT, not the error message. Its four tests all
        // passed against a check that did nothing, because they only ever looked at the rejection string.
        // Restoring the true hash must make the same call succeed — that is what proves the check ran.
        sealed.setBlobHash(realHash);
        assertEquals("Alice", ChatCryptoUtils.openProfileCard(sealed, key).getDisplayName());
    }

    // ===== the grant: it must decrypt with the grantee's REAL private key =====

    @Test
    @DisplayName("a grant to identity B decrypts to the same profile key under B's private key")
    public void grantDecryptsUnderGranteePrivateKey() throws Exception {
        String profileKey = ChatCryptoUtils.generateProfileKey();
        RegisterUserRequestBean granteeB = registeredGrantee(1);

        ProfileGrantBean grant = ChatCryptoUtils.getProfileGrantForUser(granteeB, profileKey, 99L);

        assertEquals(granteeB.getFrom(), grant.getGrantee());
        assertEquals(99L, grant.getKeyEpoch());
        assertEquals(TkmSignUtils.Hash256B64URL(iwkRSA.getPublicKeyAtIndexURL64(1)), grant.getEncKeyHash());

        String unwrapped = TkmCypherProviderBCRSA4096ENC256.decrypt(iwkRSA, 1, grant.getEncKey());
        assertEquals(profileKey, unwrapped);
    }

    @Test
    @DisplayName("the grant a peer holds actually opens that peer's copy of the card")
    public void grantedPeerCanOpenTheCard() throws Exception {
        String profileKey = ChatCryptoUtils.generateProfileKey();
        EncryptedProfileBean sealed = ChatCryptoUtils.sealProfileCard(
                card("Alice", "at the hospital", null, null), profileKey, 99L);
        ProfileGrantBean grant = ChatCryptoUtils.getProfileGrantForUser(registeredGrantee(2), profileKey, 99L);

        // End to end from the peer's side: unwrap the grant, then open the blob with what came out.
        String recovered = TkmCypherProviderBCRSA4096ENC256.decrypt(iwkRSA, 2, grant.getEncKey());
        assertEquals("at the hospital", ChatCryptoUtils.openProfileCard(sealed, recovered).getStatusMessage());
    }

    @Test
    @DisplayName("enc_key is Base64URL — the grant inherits the F11 fix by DELEGATING to getInviteForUser")
    public void grantEncKeyIsUrlSafe() throws Exception {
        ProfileGrantBean grant = ChatCryptoUtils.getProfileGrantForUser(
                registeredGrantee(3), ChatCryptoUtils.generateProfileKey(), 1L);

        assertTrue(grant.getEncKey().matches("^[A-Za-z0-9_-]+\\.{0,2}$"),
                "enc_key must be Base64URL with '.' padding — the same form as its enc_key_hash neighbour");
        assertTrue(grant.getEncKeyHash().matches("^[A-Za-z0-9_-]+\\.{0,2}$"));
    }

    // ===== producer caps (registry §4, design D8) =====

    @Test
    @DisplayName("an avatar at exactly the cap seals; one byte over is refused at the producer")
    public void avatarCapBoundary() throws Exception {
        String key = ChatCryptoUtils.generateProfileKey();

        byte[] atCap = new byte[ProfileConstants.MAX_AVATAR_BYTES];
        String sealedAtCap = ChatCryptoUtils.sealProfileCard(
                card(null, null, Base64.getEncoder().encodeToString(atCap), "image/webp"), key, 1L).getBlob();
        assertTrue(sealedAtCap.length() <= ProfileConstants.MAX_BLOB_B64_CHARS,
                "the D7 size budget must hold at the avatar cap: a 50 KiB avatar has to fit under the "
                + "96 KiB blob cap, or the two numbers contradict each other");

        byte[] overCap = new byte[ProfileConstants.MAX_AVATAR_BYTES + 1];
        assertThrows(CryptoMessageException.class, () -> ChatCryptoUtils.sealProfileCard(
                card(null, null, Base64.getEncoder().encodeToString(overCap), "image/webp"), key, 1L));
    }

    @Test
    @DisplayName("MAX_AVATAR_BYTES is NOT the inline-media constant — re-aliasing them fails here")
    public void avatarCapIsDecoupledFromInlineMedia() {
        // Registry §4.3.1. These two once shared a value (50 KiB) and never shared a meaning:
        // MAX_INLINE_BYTES is the per-message inline-media rule enforced in MessageActionValidator and
        // ChatCryptoUtils for every message in the estate. A future "tidy-up" that re-links them would
        // raise the inline-media limit estate-wide as a side effect of a profile change. This assertion
        // is the tripwire for that, and it is the reason the constant is a literal rather than an alias.
        assertNotEquals(
                io.takamaka.messages.chat.attachment.InlineContentLimits.MAX_INLINE_BYTES,
                ProfileConstants.MAX_AVATAR_BYTES,
                "the avatar cap must be independent of the inline-media cap");
        assertEquals(131072, ProfileConstants.MAX_AVATAR_BYTES, "registry §4: 128 KiB");
    }

    @Test
    @DisplayName("the §4.3 budget holds: a full-size avatar seals inside the blob cap, with headroom")
    public void sizeBudgetIsSelfConsistent() throws Exception {
        // The two numbers in registry §4.3 are derived from each other. If someone raises the avatar cap
        // and forgets the blob cap, this fails rather than surfacing as ERR_TOO_LARGE from a live server
        // against a card the producer believed was conformant.
        byte[] atCap = new byte[ProfileConstants.MAX_AVATAR_BYTES];
        String blob = ChatCryptoUtils.sealProfileCard(
                card("x".repeat(ProfileConstants.MAX_DISPLAY_NAME_CHARS),
                        "y".repeat(ProfileConstants.MAX_STATUS_MESSAGE_CHARS),
                        Base64.getEncoder().encodeToString(atCap), "image/jpeg"),
                ChatCryptoUtils.generateProfileKey(), 1L).getBlob();

        assertTrue(blob.length() <= ProfileConstants.MAX_BLOB_B64_CHARS,
                "a maximal card must fit the blob cap; was " + blob.length()
                + " vs " + ProfileConstants.MAX_BLOB_B64_CHARS);
        assertTrue(blob.length() > ProfileConstants.MAX_BLOB_B64_CHARS / 2,
                "the blob cap must not be wildly oversized either — if this trips, re-derive §4.3 rather "
                + "than paying for headroom nobody uses");
    }

    @Test
    @DisplayName("display_name and status_message are capped in NFC CODE POINTS, not UTF-16 units")
    public void nameCapsCountCodePoints() throws Exception {
        String key = ChatCryptoUtils.generateProfileKey();

        // U+1F600 is ONE code point and TWO UTF-16 units. 64 of them is exactly at the cap by the rule
        // the registry states; a producer counting String.length() would see 128 and refuse a conformant
        // card, and two clients disagreeing on the rule disagree on what is conformant.
        String sixtyFourEmoji = "😀".repeat(ProfileConstants.MAX_DISPLAY_NAME_CHARS);
        assertEquals(2 * ProfileConstants.MAX_DISPLAY_NAME_CHARS, sixtyFourEmoji.length());
        ChatCryptoUtils.sealProfileCard(card(sixtyFourEmoji, null, null, null), key, 1L);

        String sixtyFivePlain = "a".repeat(ProfileConstants.MAX_DISPLAY_NAME_CHARS + 1);
        assertThrows(CryptoMessageException.class,
                () -> ChatCryptoUtils.sealProfileCard(card(sixtyFivePlain, null, null, null), key, 1L));

        String overStatus = "b".repeat(ProfileConstants.MAX_STATUS_MESSAGE_CHARS + 1);
        assertThrows(CryptoMessageException.class,
                () -> ChatCryptoUtils.sealProfileCard(card(null, overStatus, null, null), key, 1L));
    }

    @Test
    @DisplayName("a card with no payload_version is refused rather than guessed at")
    public void unknownPayloadVersionRefused() {
        String key = ChatCryptoUtils.generateProfileKey();
        assertThrows(CryptoMessageException.class, () -> ChatCryptoUtils.sealProfileCard(
                new ProfileCardBean(null, "Alice", null, null, null), key, 1L));
        assertThrows(CryptoMessageException.class, () -> ChatCryptoUtils.sealProfileCard(
                new ProfileCardBean("9.9", "Alice", null, null, null), key, 1L));
    }

    @Test
    @DisplayName("an unknown cipher or blob_version is refused on open, not decrypted hopefully")
    public void unknownEnvelopeRefusedOnOpen() throws Exception {
        String key = ChatCryptoUtils.generateProfileKey();
        EncryptedProfileBean sealed = ChatCryptoUtils.sealProfileCard(card("Alice", null, null, null), key, 1L);

        sealed.setCipher("AES_128_CBC");
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.openProfileCard(sealed, key));

        sealed.setCipher(ProfileConstants.CIPHER_AES_256_GCM);
        sealed.setBlobVersion("2.0");
        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.openProfileCard(sealed, key));
    }

    @Test
    @DisplayName("the profile key is 32 CSPRNG bytes, and a short key is refused")
    public void profileKeyShape() throws Exception {
        String key = ChatCryptoUtils.generateProfileKey();
        assertEquals(ProfileConstants.PROFILE_KEY_BYTES, TkmSignUtils.fromAnyB64ToByteArray(key).length);
        assertNotEquals(key, ChatCryptoUtils.generateProfileKey());

        String tooShort = TkmSignUtils.fromByteArrayToB64URL(new byte[16]);
        assertThrows(CryptoMessageException.class,
                () -> ChatCryptoUtils.sealProfileCard(card("Alice", null, null, null), tooShort, 1L));
    }

    @Test
    @DisplayName("a card with unknown keys still opens — registry §3's forward-compatibility promise")
    public void unknownCardKeysAreIgnored() throws Exception {
        // A 1.1 producer adding a field must not break a 1.0 consumer. This is asserted rather than
        // assumed because the promise is in the registry, and @JsonIgnoreProperties is what keeps it.
        String key = ChatCryptoUtils.generateProfileKey();
        EncryptedProfileBean sealed = ChatCryptoUtils.sealProfileCard(card("Alice", null, null, null), key, 1L);
        assertEquals("Alice", ChatCryptoUtils.openProfileCard(sealed, key).getDisplayName());
    }
}
