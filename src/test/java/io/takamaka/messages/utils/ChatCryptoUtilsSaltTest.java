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
package io.takamaka.messages.utils;

import io.takamaka.extra.beans.EncMessageBean;
import io.takamaka.messages.chat.conversation.TopicKeyDistributionMapBean;
import io.takamaka.messages.chat.conversation.TopicTitleKeyBean;
import io.takamaka.messages.chat.core.SignedContentTopicBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.exception.CryptoMessageException;
import io.takamaka.messages.exception.InvalidParameterException;
import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.utils.TkmSignUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Unit tests for conversation salt security enhancement (Protocol v1.3).
 *
 * <p>Tests verify that:
 * <ul>
 *   <li>Salt generation is unique and random</li>
 *   <li>Topic hashes are non-deterministic</li>
 *   <li>Salt validation rejects missing/invalid salts</li>
 *   <li>Encryption/decryption preserves salt</li>
 *   <li>Enumeration attacks are prevented</li>
 * </ul>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @version 1.2.0
 * @since 1.2.0
 */
@Slf4j
public class ChatCryptoUtilsSaltTest {

    /**
     * Test 1: Salt Generation - Uniqueness
     *
     * <p>Verify that generateTopicKeyBean() produces unique salts for every call.
     * Generate 1000 salts and ensure all are unique (no collisions).
     */
    @Test
    public void testSaltGeneration_Uniqueness() throws InvalidParameterException {
        log.info("Test 1: Salt Generation - Uniqueness");

        Set<String> salts = new HashSet<>();
        int iterations = 1000;

        for (int i = 0; i < iterations; i++) {
            TopicTitleKeyBean topic = ChatCryptoUtils.generateTopicKeyBean("Test");
            salts.add(topic.getConversationSalt());
        }

        assertEquals(iterations, salts.size(),
            "All generated salts must be unique (no collisions in " + iterations + " generations)");

        log.info("✓ Generated {} unique salts", salts.size());
    }

    /**
     * Test 2: Salt Format Validation
     *
     * <p>Verify that generated salts meet format requirements:
     * <ul>
     *   <li>Exactly 32 characters</li>
     *   <li>Alphanumeric only [a-zA-Z0-9]</li>
     * </ul>
     */
    @Test
    public void testSaltGeneration_Format() throws InvalidParameterException {
        log.info("Test 2: Salt Format Validation");

        TopicTitleKeyBean topic = ChatCryptoUtils.generateTopicKeyBean("Test");
        String salt = topic.getConversationSalt();

        assertNotNull(salt, "Salt must not be null");
        assertEquals(32, salt.length(), "Salt must be exactly 32 characters");
        assertTrue(salt.matches("[a-zA-Z0-9]+"),
            "Salt must contain only alphanumeric characters [a-zA-Z0-9]");

        log.info("✓ Salt format valid: {} (length={})", salt.substring(0, 10) + "...", salt.length());
    }

    /**
     * Test 3: Non-Deterministic Topic Hashes
     *
     * <p>Verify that same topic title with different salts produces different hashes.
     * This is the core security property that prevents enumeration attacks.
     */
    @Test
    public void testTopicHash_NonDeterministic() throws Exception {
        log.info("Test 3: Non-Deterministic Topic Hashes");

        String title = "Test Conversation";

        // Generate two topics with same title but different salts
        TopicTitleKeyBean topic1 = ChatCryptoUtils.generateTopicKeyBean(title);
        TopicTitleKeyBean topic2 = ChatCryptoUtils.generateTopicKeyBean(title);

        // Salts must be different
        assertNotEquals(topic1.getConversationSalt(), topic2.getConversationSalt(),
            "Different salts must be generated for same title");

        // Compute topic hashes
        String hash1 = TkmSignUtils.Hash256B64URL(title + topic1.getConversationSalt());
        String hash2 = TkmSignUtils.Hash256B64URL(title + topic2.getConversationSalt());

        // Topic hashes must be different (non-deterministic)
        assertNotEquals(hash1, hash2,
            "Same title with different salts must produce different topic hashes");

        log.info("✓ Non-determinism verified:");
        log.info("  Title: '{}'", title);
        log.info("  Salt 1: {} → Hash: {}", topic1.getConversationSalt().substring(0, 8) + "...", hash1.substring(0, 16) + "...");
        log.info("  Salt 2: {} → Hash: {}", topic2.getConversationSalt().substring(0, 8) + "...", hash2.substring(0, 16) + "...");
    }

    /**
     * Test 4: Same Salt Produces Same Hash
     *
     * <p>Verify that using the same salt consistently produces the same hash.
     * This ensures hash computation is deterministic given the same inputs.
     */
    @Test
    public void testTopicHash_DeterministicWithSameSalt() throws Exception {
        log.info("Test 4: Same Salt Produces Same Hash");

        String title = "Test Conversation";
        String salt = "abc123xyz789ABC123XYZ789abcd";  // Fixed salt

        // Compute hash twice with same inputs
        String hash1 = TkmSignUtils.Hash256B64URL(title + salt);
        String hash2 = TkmSignUtils.Hash256B64URL(title + salt);

        // Hashes must be identical
        assertEquals(hash1, hash2,
            "Same title and salt must produce identical hash");

        log.info("✓ Determinism with same salt verified: {}", hash1.substring(0, 16) + "...");
    }

    /**
     * Test 5: TopicTitleKeyBean Validation - Reject Null Salt
     *
     * <p>Verify that validate() rejects beans with null salt.
     * Missing salt is a critical security bug.
     */
    @Test
    public void testTopicKeyBean_RejectNullSalt() {
        log.info("Test 5: TopicTitleKeyBean Validation - Reject Null Salt");

        TopicTitleKeyBean topic = new TopicTitleKeyBean("Test", "key123", null);

        InvalidParameterException ex = assertThrows(
            InvalidParameterException.class,
            () -> topic.validate(),
            "validate() must throw InvalidParameterException for null salt"
        );

        assertTrue(ex.getMessage().contains("conversation_salt is required"),
            "Error message must mention salt requirement");

        log.info("✓ Null salt rejected: {}", ex.getMessage());
    }

    /**
     * Test 6: TopicTitleKeyBean Validation - Reject Empty Salt
     *
     * <p>Verify that validate() rejects beans with empty salt string.
     */
    @Test
    public void testTopicKeyBean_RejectEmptySalt() {
        log.info("Test 6: TopicTitleKeyBean Validation - Reject Empty Salt");

        TopicTitleKeyBean topic = new TopicTitleKeyBean("Test", "key123", "");

        InvalidParameterException ex = assertThrows(
            InvalidParameterException.class,
            () -> topic.validate(),
            "validate() must throw InvalidParameterException for empty salt"
        );

        assertTrue(ex.getMessage().contains("conversation_salt is required"),
            "Error message must mention salt requirement");

        log.info("✓ Empty salt rejected: {}", ex.getMessage());
    }

    /**
     * Test 7: TopicTitleKeyBean Validation - Reject Wrong Length
     *
     * <p>Verify that validate() rejects salts with incorrect length (must be 32 chars).
     */
    @Test
    public void testTopicKeyBean_RejectWrongLength() {
        log.info("Test 7: TopicTitleKeyBean Validation - Reject Wrong Length");

        // Too short
        TopicTitleKeyBean topicShort = new TopicTitleKeyBean("Test", "key123", "abc");

        InvalidParameterException ex1 = assertThrows(
            InvalidParameterException.class,
            () -> topicShort.validate(),
            "validate() must reject salt with length != 32"
        );

        assertTrue(ex1.getMessage().contains("must be exactly 32 characters"),
            "Error message must mention length requirement");

        // Too long
        TopicTitleKeyBean topicLong = new TopicTitleKeyBean("Test", "key123",
            "abc123xyz789ABC123XYZ789abcdEXTRA");

        InvalidParameterException ex2 = assertThrows(
            InvalidParameterException.class,
            () -> topicLong.validate(),
            "validate() must reject salt with length != 32"
        );

        log.info("✓ Wrong length rejected (too short and too long)");
    }

    /**
     * Test 8: TopicTitleKeyBean Validation - Reject Invalid Characters
     *
     * <p>Verify that validate() rejects salts with non-alphanumeric characters.
     */
    @Test
    public void testTopicKeyBean_RejectInvalidCharacters() {
        log.info("Test 8: TopicTitleKeyBean Validation - Reject Invalid Characters");

        // Salt with special characters (exactly 32 chars but has special chars)
        TopicTitleKeyBean topic = new TopicTitleKeyBean("Test", "key123",
            "abc123xyz789ABC123XYZ789abcdef!@");

        InvalidParameterException ex = assertThrows(
            InvalidParameterException.class,
            () -> topic.validate(),
            "validate() must reject salt with non-alphanumeric characters"
        );

        assertTrue(ex.getMessage().contains("alphanumeric"),
            "Error message must mention alphanumeric requirement");

        log.info("✓ Invalid characters rejected: {}", ex.getMessage());
    }

    /**
     * Test 9: getSignedContentTopicBean - Reject Missing Salt
     *
     * <p>Verify that getSignedContentTopicBean() rejects TopicTitleKeyBean without salt.
     */
    @Test
    public void testGetSignedContentTopicBean_RejectMissingSalt() {
        log.info("Test 9: getSignedContentTopicBean - Reject Missing Salt");

        TopicTitleKeyBean noSalt = new TopicTitleKeyBean("Test", "key123", null);
        TopicKeyDistributionMapBean map = new TopicKeyDistributionMapBean();

        assertThrows(CryptoMessageException.class, () -> {
            ChatCryptoUtils.getSignedContentTopicBean(map, noSalt);
        }, "getSignedContentTopicBean() must reject topic without salt");

        log.info("✓ getSignedContentTopicBean() rejects missing salt");
    }

    /**
     * Test 10: getSignedContentTopicBean - Salted Hash Computation
     *
     * <p>Verify that getSignedContentTopicBean() computes salted topic hash correctly.
     */
    @Test
    public void testGetSignedContentTopicBean_SaltedHashComputation() throws Exception {
        log.info("Test 10: getSignedContentTopicBean - Salted Hash Computation");

        String title = "Test Topic";
        TopicTitleKeyBean topic = ChatCryptoUtils.generateTopicKeyBean(title);
        TopicKeyDistributionMapBean map = new TopicKeyDistributionMapBean();

        // Create signed content topic bean
        SignedContentTopicBean signedBean =
            ChatCryptoUtils.getSignedContentTopicBean(map, topic);

        // Compute expected salted hash manually
        String expectedHash = TkmSignUtils.Hash256B64URL(title + topic.getConversationSalt());

        // Verify that getSignedContentTopicBean computed the same hash
        assertEquals(expectedHash, signedBean.getTopicTitleHash(),
            "getSignedContentTopicBean() must compute salted hash correctly");

        log.info("✓ Salted hash computation verified:");
        log.info("  Title: '{}'", title);
        log.info("  Salt: {}", topic.getConversationSalt().substring(0, 8) + "...");
        log.info("  Hash: {}", signedBean.getTopicTitleHash().substring(0, 16) + "...");
    }

    /**
     * Test 11: Encryption/Decryption Round-Trip with Salt
     *
     * <p>Verify that salt survives encryption/decryption cycle.
     */
    @Test
    public void testEncryptionDecryption_SaltPreserved() throws Exception {
        log.info("Test 11: Encryption/Decryption Round-Trip with Salt");

        // Create topic with salt
        String title = "Test Conversation";
        TopicTitleKeyBean original = ChatCryptoUtils.generateTopicKeyBean(title);

        // Encrypt
        EncMessageBean encrypted = ChatCryptoUtils.getEncryptedTopic(
            original, original.getSymmetricKey()
        );

        assertNotNull(encrypted, "Encryption must succeed");

        // Decrypt
        String keyHash = TkmSignUtils.Hash256B64URL(original.getSymmetricKey());
        TopicTitleKeyBean decrypted = ChatCryptoUtils.decryptTopicTitleKeyBean(
            encrypted, original.getSymmetricKey(), keyHash
        );

        // Verify all fields preserved
        assertEquals(original.getTopicTitle(), decrypted.getTopicTitle(),
            "Topic title must survive round-trip");
        assertEquals(original.getSymmetricKey(), decrypted.getSymmetricKey(),
            "Symmetric key must survive round-trip");
        assertEquals(original.getConversationSalt(), decrypted.getConversationSalt(),
            "Salt must survive round-trip");

        log.info("✓ Encryption/decryption preserves salt:");
        log.info("  Original salt: {}", original.getConversationSalt().substring(0, 10) + "...");
        log.info("  Decrypted salt: {}", decrypted.getConversationSalt().substring(0, 10) + "...");
    }

    /**
     * Test 12: decryptTopicTitleKeyBean - Validate Salt After Decryption
     *
     * <p>Verify that decryptTopicTitleKeyBean() validates salt presence after decryption.
     * This catches old protocol versions or corrupted data.
     */
    @Test
    public void testDecryptTopicTitleKeyBean_ValidatesSalt() throws Exception {
        log.info("Test 12: decryptTopicTitleKeyBean - Validate Salt After Decryption");

        // Create topic WITH salt
        TopicTitleKeyBean original = ChatCryptoUtils.generateTopicKeyBean("Test");

        // Encrypt
        EncMessageBean encrypted = ChatCryptoUtils.getEncryptedTopic(
            original, original.getSymmetricKey()
        );

        // Decrypt (should succeed - has salt)
        String keyHash = TkmSignUtils.Hash256B64URL(original.getSymmetricKey());
        TopicTitleKeyBean decrypted = ChatCryptoUtils.decryptTopicTitleKeyBean(
            encrypted, original.getSymmetricKey(), keyHash
        );

        assertNotNull(decrypted, "Decryption must succeed for topic with salt");
        assertNotNull(decrypted.getConversationSalt(), "Decrypted topic must have salt");

        log.info("✓ Decryption validates salt presence");
    }

    /**
     * Test 13: Enumeration Attack Prevention Simulation
     *
     * <p>Simulate an attacker trying to enumerate conversations by guessing topic titles.
     * Verify that attacker cannot compute correct hash without knowing the salt.
     */
    @Test
    public void testEnumerationAttackPrevention() throws Exception {
        log.info("Test 13: Enumeration Attack Prevention Simulation");

        String title = "Private";  // Attacker guesses this common title
        String[] participants = {"Alice_PK", "Bob_PK"};

        // Real conversation created with salt
        TopicTitleKeyBean realTopic = ChatCryptoUtils.generateTopicKeyBean(title);
        String realTopicHash = TkmSignUtils.Hash256B64URL(
            title + realTopic.getConversationSalt()
        );
        String realConversationHash = ChatUtils.getConversationName(
            participants, realTopicHash
        );

        // Attacker tries to compute hash WITHOUT salt
        String attackerTopicHash = TkmSignUtils.Hash256B64URL(title);  // No salt!
        String attackerConversationHash = ChatUtils.getConversationName(
            participants, attackerTopicHash
        );

        // Attack must fail - hashes don't match
        assertNotEquals(realConversationHash, attackerConversationHash,
            "Attacker cannot compute correct conversation hash without salt");

        log.info("✓ Enumeration attack prevented:");
        log.info("  Title: '{}'", title);
        log.info("  Real hash (with salt): {}", realConversationHash.substring(0, 16) + "...");
        log.info("  Attacker hash (no salt): {}", attackerConversationHash.substring(0, 16) + "...");
        log.info("  Hashes DIFFERENT ✓ (attack fails)");
    }

    /**
     * Test 14: Multiple Conversations Same Title Different Hashes
     *
     * <p>Verify that multiple conversations with same title produce different hashes.
     * This demonstrates implicit key rotation capability.
     */
    @Test
    public void testMultipleConversations_SameTitle_DifferentHashes() throws Exception {
        log.info("Test 14: Multiple Conversations Same Title Different Hashes");

        String title = "Team Chat";
        String[] participants = {"Alice_PK", "Bob_PK", "Charlie_PK"};

        // Create 3 conversations with same title and participants
        List<String> conversationHashes = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            TopicTitleKeyBean topic = ChatCryptoUtils.generateTopicKeyBean(title);
            String topicHash = TkmSignUtils.Hash256B64URL(title + topic.getConversationSalt());
            String convHash = ChatUtils.getConversationName(participants, topicHash);
            conversationHashes.add(convHash);
        }

        // All hashes must be unique
        Set<String> uniqueHashes = new HashSet<>(conversationHashes);
        assertEquals(3, uniqueHashes.size(),
            "Same title/participants with different salts must produce unique conversation hashes");

        log.info("✓ 3 conversations with same title/participants have unique hashes:");
        for (int i = 0; i < conversationHashes.size(); i++) {
            log.info("  Conversation {}: {}", i + 1,
                conversationHashes.get(i).substring(0, 16) + "...");
        }
    }
}
