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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.takamaka.extra.beans.StreamEncryptedDescriptor;
import io.takamaka.messages.chat.BasicMessageEncryptedContentBean;
import io.takamaka.messages.chat.SignedMessageBean;
import io.takamaka.messages.chat.requests.BasicMessageRequestBean;
import io.takamaka.messages.chat.requests.RegisterUserRequestBean;
import io.takamaka.messages.chat.requests.RegisterUserRequestSignedContentBean;
import io.takamaka.messages.chat.requests.SignedTimestampRequestBean;
import io.takamaka.messages.chat.requests.SignedUploadRequestBean;
import io.takamaka.messages.chat.requests.UserNotificationRequestBean;
import io.takamaka.messages.chat.responses.NonceResponseBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.exception.InvalidParameterException;
import io.takamaka.messages.exception.MessageException;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.InstanceWalletKeyStoreBCRSA4096ENC;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class ChatUtilsTest {

    public static InstanceWalletKeystoreInterface iwkED;
    public static InstanceWalletKeystoreInterface iwkRSA;
    public static final String password = "superSecretPassword";

    @BeforeAll
    public static void setUpClass() throws WalletException {
        iwkED = new InstanceWalletKeyStoreBCED25519("chat_utils_test_wallet", password);
        iwkRSA = new InstanceWalletKeyStoreBCRSA4096ENC("chat_utils_test_wallet", password);
    }

    @Test
    public void testParseSafeConversationHashName_valid() throws InvalidParameterException {
        String validHash = "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd";
        String result = ChatUtils.parseSafeConversationHashName(validHash);
        assertEquals(validHash, result);
    }

    @Test
    public void testParseSafeConversationHashName_invalid() {
        String invalidHash = "invalid_hash";
        Exception ex = assertThrows(InvalidParameterException.class,
            () -> ChatUtils.parseSafeConversationHashName(invalidHash));
        assertEquals("invalid conversation hash name", ex.getMessage());
    }

    @Test
    public void testParseSafeConversationHashName_tooShort() {
        String shortHash = "a1b2c3d4";
        Exception ex = assertThrows(InvalidParameterException.class,
            () -> ChatUtils.parseSafeConversationHashName(shortHash));
        assertEquals("invalid conversation hash name", ex.getMessage());
    }

    @Test
    public void testParseSafeSIgnatureName_valid() throws InvalidParameterException {
        String validSignature = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_0123456789abcdefghijkl..";
        String result = ChatUtils.parseSafeSIgnatureName(validSignature);
        assertEquals(validSignature, result);
    }

    @Test
    public void testParseSafeSIgnatureName_invalid() {
        String invalidSignature = "invalid@signature";
        Exception ex = assertThrows(InvalidParameterException.class,
            () -> ChatUtils.parseSafeSIgnatureName(invalidSignature));
        assertEquals("invalid conversation hash name", ex.getMessage());
    }

    @Test
    public void testGetObjectJsonPretty() throws JsonProcessingException, ChatMessageException, MessageException, WalletException {
        RegisterUserRequestBean bean = ChatCryptoUtils.getSignedRegisteredUserRequests(
                iwkED,
                0,
                new RegisterUserRequestSignedContentBean(
                        new NonceResponseBean(
                                UUID.randomUUID().toString(),
                                Long.MIN_VALUE,
                                Long.MIN_VALUE
                        ),
                        iwkRSA.getPublicKeyAtIndexURL64(0),
                        iwkRSA.getWalletCypher().name()
                )
        );

        String json = ChatUtils.getObjectJsonPretty(bean);
        assertNotNull(json);
        assertTrue(json.contains("\"message_type\""));
        assertTrue(json.contains("\"signature\""));
        log.info("Pretty JSON: {}", json);
    }

    @Test
    public void testFromJsonToRegisterUserRequestBean() throws ChatMessageException, MessageException, JsonProcessingException, WalletException {
        RegisterUserRequestBean original = ChatCryptoUtils.getSignedRegisteredUserRequests(
                iwkED,
                0,
                new RegisterUserRequestSignedContentBean(
                        new NonceResponseBean(
                                UUID.randomUUID().toString(),
                                Long.MIN_VALUE,
                                Long.MIN_VALUE
                        ),
                        iwkRSA.getPublicKeyAtIndexURL64(0),
                        iwkRSA.getWalletCypher().name()
                )
        );

        String json = ChatUtils.getObjectJsonPretty(original);
        RegisterUserRequestBean deserialized = ChatUtils.fromJsonToRegisterUserRequestBean(json);

        assertEquals(original.getFrom(), deserialized.getFrom());
        assertEquals(original.getSignature(), deserialized.getSignature());
        assertEquals(original.getMessageType(), deserialized.getMessageType());
    }

    @Test
    public void testFromJsonToSignedMessageBean() throws ChatMessageException, MessageException, JsonProcessingException, WalletException {
        RegisterUserRequestBean bean = ChatCryptoUtils.getSignedRegisteredUserRequests(
                iwkED,
                0,
                new RegisterUserRequestSignedContentBean(
                        new NonceResponseBean(
                                UUID.randomUUID().toString(),
                                Long.MIN_VALUE,
                                Long.MIN_VALUE
                        ),
                        iwkRSA.getPublicKeyAtIndexURL64(0),
                        iwkRSA.getWalletCypher().name()
                )
        );

        String json = ChatUtils.getObjectJsonPretty(bean);
        SignedMessageBean signedMessage = ChatUtils.fromJsonToSignedMessageBean(json);

        assertNotNull(signedMessage);
        assertNotNull(signedMessage.getSignature());
        assertNotNull(signedMessage.getFrom());
        assertEquals("REGISTER_USER_SIGNED_REQUEST", signedMessage.getMessageType());
    }

    @Test
    public void testFromJsonToBasicMessageBeanRequest() throws ChatMessageException, MessageException, JsonProcessingException, WalletException {
        BasicMessageRequestBean original = ChatCryptoUtils.getBasicMessageBean(
                iwkED,
                0,
                "placeholder",
                "testSymmetricKey",
                new ArrayList<>(),
                new BasicMessageEncryptedContentBean(
                        "test message content",
                        new ArrayList<>()
                )
        );

        String json = TkmTextUtils.getJacksonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(original);
        BasicMessageRequestBean deserialized = ChatUtils.fromJsonToBasicMessageBeanRequest(json);

        assertEquals(original.getFrom(), deserialized.getFrom());
        assertEquals(original.getSignature(), deserialized.getSignature());
    }

    @Test
    public void testFromJsonToUserNotificationRequestBean() throws ChatMessageException, MessageException, JsonProcessingException, WalletException {
        UserNotificationRequestBean original = ChatCryptoUtils.getUserNotificationRequestBean(
                new Date().getTime(),
                true,
                iwkED,
                0
        );

        String json = TkmTextUtils.getJacksonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(original);
        UserNotificationRequestBean deserialized = ChatUtils.fromJsonToUserNotificationRequestBean(json);

        assertEquals(original.getFrom(), deserialized.getFrom());
        assertEquals(original.getSignature(), deserialized.getSignature());
    }

    @Test
    public void testFromJsonToTimestampSignedRequestBean() throws ChatMessageException, MessageException, JsonProcessingException, WalletException {
        SignedTimestampRequestBean original = ChatCryptoUtils.getSignedTimestampRequest(iwkED, 0);

        String json = TkmTextUtils.getJacksonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(original);
        SignedTimestampRequestBean deserialized = ChatUtils.fromJsonToTimestampSignedRequestBean(json);

        assertEquals(original.getFrom(), deserialized.getFrom());
        assertEquals(original.getSignature(), deserialized.getSignature());
    }

    @Test
    public void testFromJsonToSignedUploadRequestBean() throws ChatMessageException, MessageException, JsonProcessingException, WalletException {
        SignedUploadRequestBean original = ChatCryptoUtils.getSignedUploadRequestBean(
                "test_topic",
                "file_signature",
                Long.MAX_VALUE,
                new StreamEncryptedDescriptor(),
                iwkED,
                0
        );

        String json = TkmTextUtils.getJacksonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(original);
        SignedUploadRequestBean deserialized = ChatUtils.fromJsonToSignedUploadRequestBean(json);

        assertEquals(original.getFrom(), deserialized.getFrom());
        assertEquals(original.getSignature(), deserialized.getSignature());
    }

    @Test
    public void testGetConversationName() throws ChatMessageException {
        String[] participants = {"user1", "user2", "user3"};
        String topicHash = "test_topic_hash";

        String conversationName1 = ChatUtils.getConversationName(participants, topicHash);
        assertNotNull(conversationName1);
        assertEquals(64, conversationName1.length());

        // Test consistency - same participants should produce same hash
        String[] participants2 = {"user3", "user1", "user2"}; // different order
        String conversationName2 = ChatUtils.getConversationName(participants2, topicHash);
        assertEquals(conversationName1, conversationName2);

        log.info("Conversation name: {}", conversationName1);
    }

    @Test
    public void testGetConversationName_differentTopic() throws ChatMessageException {
        String[] participants = {"user1", "user2"};
        String topicHash1 = "topic1";
        String topicHash2 = "topic2";

        String conversationName1 = ChatUtils.getConversationName(participants, topicHash1);
        String conversationName2 = ChatUtils.getConversationName(participants, topicHash2);

        assertTrue(!conversationName1.equals(conversationName2));
    }

    @Test
    public void testGetUserConversationHash() throws HashAlgorithmNotFoundException, HashEncodeException, HashProviderNotFoundException {
        String memberKey = "test_member_key";
        String conversationName = "test_conversation_name";

        String hash1 = ChatUtils.getUserConversationHash(memberKey, conversationName);
        assertNotNull(hash1);

        // Test consistency
        String hash2 = ChatUtils.getUserConversationHash(memberKey, conversationName);
        assertEquals(hash1, hash2);

        // Test different inputs produce different hashes
        String hash3 = ChatUtils.getUserConversationHash(memberKey + "X", conversationName);
        assertTrue(!hash1.equals(hash3));

        log.info("User conversation hash: {}", hash1);
    }

    @Test
    public void testConversationHashNamePattern() {
        assertTrue(ChatUtils.CONVERSATION_HASH_NAME_PARAM_PATTERN.matcher(
                "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd").matches());
        assertTrue(ChatUtils.CONVERSATION_HASH_NAME_PARAM_PATTERN.matcher(
                "ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567878").matches());
        assertTrue(!ChatUtils.CONVERSATION_HASH_NAME_PARAM_PATTERN.matcher("short").matches());
        assertTrue(!ChatUtils.CONVERSATION_HASH_NAME_PARAM_PATTERN.matcher(
                "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcdXX").matches());
    }

    @Test
    public void testConversationSignaturePattern() {
        assertTrue(ChatUtils.CONVERSATION_SIGNATURE_PARAM_PATTERN.matcher(
                "abcdefghijklmnopqrstuvwxyzABCDEFGqqHIJLMNOPQRSTUVWXYZ0123456789-_0123456789abcdefghijk..").matches());
        assertTrue(!ChatUtils.CONVERSATION_SIGNATURE_PARAM_PATTERN.matcher("invalid").matches());
        assertTrue(!ChatUtils.CONVERSATION_SIGNATURE_PARAM_PATTERN.matcher(
                "abcdefghijklmnopqrstuvwxyz@BCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_0123456789abcdefghijk..").matches());
    }
}
