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
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.utils.ChatCryptoUtils;
import io.takamaka.messages.utils.ChatUtils;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Round-trips all SIX profile envelopes through
 * sign &rarr; serialize &rarr; parse &rarr; verify, and asserts the
 * canonical-{@code pl} signature holds — and that tampering breaks it.
 *
 * <p>Exit criterion for Phase 1 (USER_PROFILE_HANDOFF.md §3). A missing arm in
 * any of the three places a message type has to be registered — the enum,
 * {@code ChatCryptoUtils.verifySignedMessage}, {@code ChatUtils.fromJsonTo*} —
 * fails here rather than at the server, which is the whole point of testing all
 * six rather than one representative.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class UserProfileSignVerifyTest {

    public static InstanceWalletKeystoreInterface iwkED;
    public static final String PASSWORD = "superSecretPassword";

    @BeforeAll
    public static void setUpClass() throws WalletException {
        iwkED = new InstanceWalletKeyStoreBCED25519("user_profile_test_wallet", PASSWORD);
    }

    private static NonceResponseBean freshNonce() {
        return new NonceResponseBean(UUID.randomUUID().toString(), System.currentTimeMillis(), 60_000L);
    }

    private static String toJson(Object bean) throws Exception {
        return TkmTextUtils.getJacksonMapper().writeValueAsString(bean);
    }

    private static EncryptedProfileBean sampleProfile() throws Exception {
        ProfileCardBean card = new ProfileCardBean(
                ProfileConstants.PAYLOAD_VERSION_1_0, "Alice", "at the hospital", null, null);
        return ChatCryptoUtils.sealProfileCard(card, ChatCryptoUtils.generateProfileKey(), 1_700_000_000_000L);
    }

    private static ProfileGrantBean sampleGrant() throws Exception {
        return new ProfileGrantBean(
                iwkED.getPublicKeyAtIndexURL64(1), 1_700_000_000_000L, "hash-of-the-grantee-enc-key", "wrapped-key");
    }

    @Test
    @DisplayName("setuserprofile: sign, parse, verify")
    public void setUserProfile_roundTrip_verifies() throws Exception {
        SetUserProfileRequestBean original = ChatCryptoUtils.getSignedSetUserProfileRequest(
                freshNonce(), sampleProfile(), List.of(sampleGrant()), System.currentTimeMillis(), iwkED, 0);
        String json = toJson(original);

        SetUserProfileRequestBean parsed = ChatUtils.fromJsonToSetUserProfileRequestBean(json);
        assertEquals(original.getSignature(), parsed.getSignature());
        assertEquals(original.getPl().getProfile().getBlobHash(), parsed.getPl().getProfile().getBlobHash());
        assertEquals(1, parsed.getPl().getGrants().size());
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
    }

    @Test
    @DisplayName("setuserprofile: swapping the blob after signing breaks the signature")
    public void setUserProfile_tamperedBlob_rejected() throws Exception {
        SetUserProfileRequestBean original = ChatCryptoUtils.getSignedSetUserProfileRequest(
                freshNonce(), sampleProfile(), List.of(sampleGrant()), System.currentTimeMillis(), iwkED, 0);

        // The blob is INSIDE the signed pl. A server that stored it without verifying would be storing
        // whatever a middlebox substituted — the signature is the only thing that ties the ciphertext to
        // the identity that claims to own it.
        original.getPl().getProfile().setBlob("dGFtcGVyZWQ.");
        String tampered = toJson(original);

        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(tampered));
    }

    @Test
    @DisplayName("setuserprofile: adding a grant after signing breaks the signature")
    public void setUserProfile_injectedGrant_rejected() throws Exception {
        SetUserProfileRequestBean original = ChatCryptoUtils.getSignedSetUserProfileRequest(
                freshNonce(), sampleProfile(), List.of(sampleGrant()), System.currentTimeMillis(), iwkED, 0);

        // The attack this covers: injecting a grantee grants a reader the owner never chose. The grants
        // ride inside the signed pl precisely so this cannot be done in flight.
        original.getPl().setGrants(List.of(sampleGrant(), sampleGrant()));
        String tampered = toJson(original);

        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(tampered));
    }

    @Test
    @DisplayName("putprofilegrants: sign, parse, verify")
    public void putProfileGrants_roundTrip_verifies() throws Exception {
        PutProfileGrantsRequestBean original = ChatCryptoUtils.getSignedPutProfileGrantsRequest(
                freshNonce(), 1_700_000_000_000L, List.of(sampleGrant()), System.currentTimeMillis(), iwkED, 0);
        String json = toJson(original);

        PutProfileGrantsRequestBean parsed = ChatUtils.fromJsonToPutProfileGrantsRequestBean(json);
        assertEquals(1_700_000_000_000L, parsed.getPl().getKeyEpoch());
        assertEquals(1, parsed.getPl().getGrants().size());
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
    }

    @Test
    @DisplayName("clearuserprofile: sign, parse, verify")
    public void clearUserProfile_roundTrip_verifies() throws Exception {
        ClearUserProfileRequestBean original = ChatCryptoUtils.getSignedClearUserProfileRequest(
                freshNonce(), System.currentTimeMillis(), iwkED, 0);
        String json = toJson(original);

        ClearUserProfileRequestBean parsed = ChatUtils.fromJsonToClearUserProfileRequestBean(json);
        assertNotNull(parsed.getPl().getNonce());
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
    }

    @Test
    @DisplayName("getuserprofile: sign, parse, verify (nonce-free)")
    public void getUserProfile_roundTrip_verifies() throws Exception {
        GetUserProfileRequestBean original = ChatCryptoUtils.getSignedGetUserProfileRequest(
                System.currentTimeMillis(), iwkED, 0);
        String json = toJson(original);

        GetUserProfileRequestBean parsed = ChatUtils.fromJsonToGetUserProfileRequestBean(json);
        assertNotNull(parsed.getPl());
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
    }

    @Test
    @DisplayName("getuserprofile: a null client_timestamp still serialises and verifies")
    public void getUserProfile_nullTimestamp_verifies() throws Exception {
        // NON_NULL inclusion means the pl serialises to `{}`. The estate's default ObjectMapper fails on a
        // bean with no PROPERTIES; one that merely omits them at runtime is fine. This is the case that
        // would have broken had the bean been left literally empty, as design §6.2 draws it.
        GetUserProfileRequestBean original = ChatCryptoUtils.getSignedGetUserProfileRequest(null, iwkED, 0);
        String json = toJson(original);
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
    }

    @Test
    @DisplayName("getuserprofilepeer: sign, parse, verify, with known_blob_hash")
    public void getUserProfilePeer_roundTrip_verifies() throws Exception {
        String targetPk = iwkED.getPublicKeyAtIndexURL64(1);
        GetUserProfilePeerRequestBean original = ChatCryptoUtils.getSignedGetUserProfilePeerRequest(
                targetPk, "cafebabe", System.currentTimeMillis(), iwkED, 0);
        String json = toJson(original);

        GetUserProfilePeerRequestBean parsed = ChatUtils.fromJsonToGetUserProfilePeerRequestBean(json);
        assertEquals(targetPk, parsed.getPl().getTargetPublicKey());
        assertEquals("cafebabe", parsed.getPl().getKnownBlobHash());
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
    }

    @Test
    @DisplayName("getuserprofilepeer: an unconditional read (no known_blob_hash) verifies")
    public void getUserProfilePeer_noKnownHash_verifies() throws Exception {
        GetUserProfilePeerRequestBean original = ChatCryptoUtils.getSignedGetUserProfilePeerRequest(
                iwkED.getPublicKeyAtIndexURL64(1), null, System.currentTimeMillis(), iwkED, 0);
        String json = toJson(original);
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
    }

    @Test
    @DisplayName("getuserprofilepeer: retargeting after signing breaks the signature")
    public void getUserProfilePeer_retargeted_rejected() throws Exception {
        GetUserProfilePeerRequestBean original = ChatCryptoUtils.getSignedGetUserProfilePeerRequest(
                iwkED.getPublicKeyAtIndexURL64(1), null, System.currentTimeMillis(), iwkED, 0);
        original.getPl().setTargetPublicKey(iwkED.getPublicKeyAtIndexURL64(2));
        String tampered = toJson(original);

        assertThrows(ChatMessageException.class, () -> ChatCryptoUtils.verifySignedMessage(tampered));
    }

    @Test
    @DisplayName("getprofiledigests: sign, parse, verify")
    public void getProfileDigests_roundTrip_verifies() throws Exception {
        List<String> targets = List.of(
                iwkED.getPublicKeyAtIndexURL64(1),
                iwkED.getPublicKeyAtIndexURL64(2),
                iwkED.getPublicKeyAtIndexURL64(3));
        GetProfileDigestsRequestBean original = ChatCryptoUtils.getSignedGetProfileDigestsRequest(
                targets, System.currentTimeMillis(), iwkED, 0);
        String json = toJson(original);

        GetProfileDigestsRequestBean parsed = ChatUtils.fromJsonToGetProfileDigestsRequestBean(json);
        assertEquals(targets, parsed.getPl().getTargetPublicKeys());
        assertNotNull(ChatCryptoUtils.verifySignedMessage(json));
    }

    @Test
    @DisplayName("getprofiledigests: the producer refuses a batch over the cap rather than truncating")
    public void getProfileDigests_overCap_refusedAtTheProducer() throws Exception {
        List<String> targets = new java.util.ArrayList<>();
        for (int i = 0; i < ProfileConstants.MAX_DIGEST_BATCH + 1; i++) {
            targets.add(iwkED.getPublicKeyAtIndexURL64(i));
        }
        // Truncating instead would make the dropped targets read as "no profile" — a silent wrong answer,
        // not a slow one.
        assertThrows(io.takamaka.messages.exception.CryptoMessageException.class,
                () -> ChatCryptoUtils.getSignedGetProfileDigestsRequest(
                        targets, System.currentTimeMillis(), iwkED, 0));
    }

    @Test
    @DisplayName("getprofiledigests: a batch at exactly the cap is accepted")
    public void getProfileDigests_atCap_accepted() throws Exception {
        List<String> targets = new java.util.ArrayList<>();
        for (int i = 0; i < ProfileConstants.MAX_DIGEST_BATCH; i++) {
            targets.add(iwkED.getPublicKeyAtIndexURL64(i));
        }
        assertNotNull(ChatCryptoUtils.getSignedGetProfileDigestsRequest(
                targets, System.currentTimeMillis(), iwkED, 0));
    }
}
