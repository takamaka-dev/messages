/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.takamaka.extra.beans.CombinedRSAAESBean;
import io.takamaka.extra.beans.EncMessageBean;
import io.takamaka.extra.beans.StreamEncryptedDescriptor;
import io.takamaka.extra.utils.EncryptionContext;
import io.takamaka.extra.utils.TkmEncryptionUtils;
import io.takamaka.messages.chat.message.BasicMessageEncryptedContentBean;
import io.takamaka.messages.chat.message.BasicMessageSignedContentBean;
import io.takamaka.messages.chat.core.BasicTimestampBean;
import io.takamaka.messages.chat.attachment.DownloadRequestBean;
//import io.takamaka.messages.chat.ChatMediaBean;
import io.takamaka.messages.chat.core.SignedContentTopicBean;
import io.takamaka.messages.chat.core.SignedMessageBean;
import io.takamaka.messages.chat.conversation.TopicKeyDistributionItemBean;
import io.takamaka.messages.chat.conversation.TopicKeyDistributionMapBean;
import io.takamaka.messages.chat.conversation.TopicTitleKeyBean;
import io.takamaka.messages.chat.attachment.UploadRequestBean;
import io.takamaka.messages.chat.notification.SignedNotificationRequestContentBean;
import io.takamaka.messages.chat.message.BasicMessageRequestBean;
import io.takamaka.messages.chat.conversation.CreateConversationRequestBean;
import io.takamaka.messages.chat.user.RegisterUserRequestBean;
import io.takamaka.messages.chat.user.RegisterUserRequestSignedContentBean;
import io.takamaka.messages.chat.user.RequestUserKeyRequestBean;
import io.takamaka.messages.chat.user.RequestUserKeyRequestSignedContentBean;
import io.takamaka.messages.chat.conversation.RetrieveAllConversationsRequestBean;
import io.takamaka.messages.chat.conversation.RetrieveAllConversationsRequestContentBean;
import io.takamaka.messages.chat.conversation.RetrieveConversationRequestBean;
import io.takamaka.messages.chat.conversation.RetrieveConversationRequestContentBean;
import io.takamaka.messages.chat.message.RetrieveMessageRequestBean;
import io.takamaka.messages.chat.message.RetrieveMessageSignedRequestBean;
import io.takamaka.messages.chat.attachment.SignedDownloadRequestBean;
import io.takamaka.messages.chat.core.SignedTimestampRequestBean;
import io.takamaka.messages.chat.attachment.SignedUploadRequestBean;
import io.takamaka.messages.chat.notification.UserNotificationRequestBean;
import io.takamaka.messages.chat.core.NonceResponseBean;
import io.takamaka.messages.chat.message.RetrieveMessagesResponseBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.exception.CryptoMessageException;
import io.takamaka.messages.exception.InvalidChatMessageSignatureException;
import io.takamaka.messages.exception.InvalidParameterException;
import io.takamaka.messages.exception.MessageException;
import io.takamaka.messages.exception.UnsupportedChatMessageTypeException;
import io.takamaka.messages.exception.UnsupportedSignatureCypherException;
import static io.takamaka.messages.utils.ChatUtils.fromJsonToRetrieveConversationRequestBean;
import io.takamaka.wallet.InstanceWalletKeyStoreBCRSA4096ENC;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.TkmCypherProviderBCED25519;
import io.takamaka.wallet.TkmCypherProviderBCRSA4096ENC;
import io.takamaka.wallet.beans.TkmCypherBean;
import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.KeyContexts;
import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.RandomStringGenerator;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class ChatCryptoUtils {

    /**
     *
     * @param len default key len 400, you can specify different key len
     * @return [a-zA-Z0-9]{len} safe random key
     * @throws io.takamaka.messages.exception.InvalidParameterException
     */
    public static final String generateRandomSafeKey(int... len) throws InvalidParameterException {
        int keyLenght = 400;
        switch (len.length) {
            case 0:
                keyLenght = 400;
                break;
            case 1:
                keyLenght = len[0];
                break;
            default:
                throw new InvalidParameterException("exactly 0 or 1 argument required, given " + Arrays.toString(len));
        }

        RandomStringGenerator generator = new RandomStringGenerator.Builder()
                .withinRange('0', 'z')
                .filteredBy(Character::isLetterOrDigit)
                .get();
        String secretKey = generator.generate(keyLenght);
        return secretKey;
    }

    /**
     * Generate a topic key bean with random symmetric key and salt.
     *
     * <p><strong>Security Enhancement (v1.2.0):</strong> This method now generates
     * a cryptographically random salt to prevent conversation enumeration attacks.
     * The salt makes topic hashes non-deterministic.</p>
     *
     * @param topicTitle title of the topic
     * @return TopicTitleKeyBean with title, symmetric key, and random 32-char salt
     * @throws InvalidParameterException if parameters are invalid
     * @since 1.0.0
     */
    public static final TopicTitleKeyBean generateTopicKeyBean(String topicTitle) throws InvalidParameterException {
        String symmetricKey = generateRandomSafeKey();
        String conversationSalt = generateRandomSafeKey(32);  // NEW: 32-char random salt
        return new TopicTitleKeyBean(topicTitle, symmetricKey, conversationSalt);
    }

//    public static final CombinedRSAAESBean getTopicEncryptedForUser(TopicTitleKeyBean topicTitleKeyBean, String userEncryptionKet) throws CryptoMessageException {
//        try {
//            String canonicalJson = SimpleRequestHelper.getCanonicalJson(topicTitleKeyBean);
//            CombinedRSAAESBean encryptRSAAES = TkmEncryptionUtils.encryptRSAAES(userEncryptionKet, canonicalJson);
//            return encryptRSAAES;
//        } catch (JsonProcessingException | WalletException ex) {
//            throw new CryptoMessageException(ex);
//        }
//    }
    public static final EncMessageBean getEncryptedTopic(TopicTitleKeyBean topicTitleKeyBean, String symmetricKey) throws CryptoMessageException {
        try {
            String canonicalJson = SimpleRequestHelper.getCanonicalJson(topicTitleKeyBean);
            EncMessageBean toPasswordEncryptedContent = TkmEncryptionUtils.toPasswordEncryptedContent(
                    symmetricKey,
                    canonicalJson,
                    CHAT_MESSAGE_TYPES.TOPIC_CREATION.name(),
                    EncryptionContext.v0_1_a.name());
            return toPasswordEncryptedContent;
        } catch (JsonProcessingException | WalletException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    /**
     * Decrypt topic title key bean and validate security requirements.
     *
     * <p><strong>SECURITY:</strong> This method validates that the decrypted bean
     * contains all required security fields including the conversation salt.
     * Missing salt indicates a critical security bug or old protocol version.</p>
     *
     * @param topicDescription encrypted topic description
     * @param symmetricConversationKey symmetric key for decryption
     * @param keyHash expected hash of the symmetric key (for integrity check)
     * @return decrypted and validated TopicTitleKeyBean
     * @throws ChatMessageException if decryption fails or validation fails
     * @since 1.0.0
     */
    public static final TopicTitleKeyBean decryptTopicTitleKeyBean(EncMessageBean topicDescription, String symmetricConversationKey, String keyHash) throws ChatMessageException {
        try {
            String fromPasswordEncryptedContent = TkmEncryptionUtils.fromPasswordEncryptedContent(symmetricConversationKey, CHAT_MESSAGE_TYPES.TOPIC_CREATION.name(), topicDescription);
            TopicTitleKeyBean tkb = TkmTextUtils.getJacksonMapper().readValue(fromPasswordEncryptedContent, TopicTitleKeyBean.class);

            String decryptedKeyHash = TkmSignUtils.Hash256B64URL(tkb.getSymmetricKey());
            if (!decryptedKeyHash.equals(keyHash)) {
                throw new ChatMessageException(String.format("key hash %s does not match declared hash %s", decryptedKeyHash, keyHash));
            }

            // SECURITY: Validate salt presence after decryption (v1.2.0+)
            // Missing salt indicates old protocol version or critical bug
            tkb.validate();

            return tkb;
        } catch (WalletException | JsonProcessingException | HashEncodeException | HashAlgorithmNotFoundException | HashProviderNotFoundException | InvalidParameterException ex) {
            throw new ChatMessageException(ex);
        }
    }

    public static final EncMessageBean getEncryptedBasicMessageEncryptedContentBean(BasicMessageEncryptedContentBean basicMessageEncryptedContentBean, String symmetricKey) throws CryptoMessageException {
        try {
            String canonicalJson = SimpleRequestHelper.getCanonicalJson(basicMessageEncryptedContentBean);
            EncMessageBean toPasswordEncryptedContent = TkmEncryptionUtils.toPasswordEncryptedContent(
                    symmetricKey,
                    canonicalJson,
                    CHAT_MESSAGE_TYPES.TOPIC_MESSAGE.name(),
                    EncryptionContext.v0_1_a.name());
            return toPasswordEncryptedContent;
        } catch (JsonProcessingException | WalletException ex) {
            throw new CryptoMessageException(ex);
        }

    }

//    public static final EncMessageBean getEncryptedMessageAttachmentMediaBean(ChatMediaBean chatMediaBean, String symmetricKey) throws CryptoMessageException {
//        try {
//            String canonicalJson = SimpleRequestHelper.getCanonicalJson(chatMediaBean);
//            EncMessageBean toPasswordEncryptedContent = TkmEncryptionUtils.toPasswordEncryptedContent(
//                    symmetricKey,
//                    canonicalJson,
//                    CHAT_MESSAGE_TYPES.TOPIC_MESSAGE_MEDIA.name(),
//                    EncryptionContext.v0_1_a.name());
//            return toPasswordEncryptedContent;
//        } catch (JsonProcessingException | WalletException ex) {
//            throw new CryptoMessageException(ex);
//        }
//
//    }
    public static final BasicMessageEncryptedContentBean decryptBasicMessageEncryptedContentBeanWithScope(EncMessageBean encryptedBasicMessageBean, String symmetricKey, CHAT_MESSAGE_TYPES scope) throws ChatMessageException {
        try {
            String fromPasswordEncryptedContent = TkmEncryptionUtils.fromPasswordEncryptedContent(symmetricKey, scope.name(), encryptedBasicMessageBean);
            BasicMessageEncryptedContentBean readValue = TkmTextUtils.getJacksonMapper().readValue(fromPasswordEncryptedContent, BasicMessageEncryptedContentBean.class);
            return readValue;
        } catch (WalletException | JsonProcessingException ex) {
            throw new ChatMessageException(ex);
        }
    }

//    public static final ChatMediaBean decryptMediaMessageEncryptedContentBeanWithScope(EncMessageBean encryptedBasicMessageBean, String symmetricKey, CHAT_MESSAGE_TYPES scope) throws ChatMessageException {
//        try {
//            String fromPasswordEncryptedContent = TkmEncryptionUtils.fromPasswordEncryptedContent(symmetricKey, scope.name(), encryptedBasicMessageBean);
//            ChatMediaBean readValue = TkmTextUtils.getJacksonMapper().readValue(fromPasswordEncryptedContent, ChatMediaBean.class);
//            return readValue;
//        } catch (WalletException | JsonProcessingException ex) {
//            throw new ChatMessageException(ex);
//        }
//    }
    public static final BasicMessageEncryptedContentBean decryptBasicMessageEncryptedContentBean(EncMessageBean encryptedBasicMessageBean, String symmetricKey) throws ChatMessageException {
        try {
            String fromPasswordEncryptedContent = TkmEncryptionUtils.fromPasswordEncryptedContent(symmetricKey, CHAT_MESSAGE_TYPES.TOPIC_CREATION.name(), encryptedBasicMessageBean);
            BasicMessageEncryptedContentBean readValue = TkmTextUtils.getJacksonMapper().readValue(fromPasswordEncryptedContent, BasicMessageEncryptedContentBean.class);
            return readValue;
        } catch (WalletException | JsonProcessingException ex) {
            throw new ChatMessageException(ex);
        }
    }

    /**
     * Create signed content topic bean with salted topic hash.
     *
     * <p><strong>SECURITY CRITICAL:</strong> This method computes a salted topic hash
     * to prevent conversation enumeration attacks. The salt MUST be present in the
     * TopicTitleKeyBean or this method will throw an exception.</p>
     *
     * <p><strong>Hash Computation (v1.2.0+):</strong><br>
     * topicTitleHash = SHA3-256(topicTitle + conversationSalt)</p>
     *
     * <p>This makes the topic hash non-deterministic and unpredictable without
     * knowledge of the salt, which is encrypted in the topic description.</p>
     *
     * @param topicKeyDistributionMapBean key distribution for participants
     * @param topicTitleKeyBean topic metadata including MANDATORY salt
     * @return SignedContentTopicBean with salted topic hash
     * @throws CryptoMessageException if salt is missing or crypto operations fail
     * @since 1.0.0
     */
    public static final SignedContentTopicBean getSignedContentTopicBean(TopicKeyDistributionMapBean topicKeyDistributionMapBean, TopicTitleKeyBean topicTitleKeyBean) throws CryptoMessageException {
        try {
            // SECURITY: Validate salt presence before proceeding
            topicTitleKeyBean.validate();

            String topicSymmetricKeySignature = TkmSignUtils.Hash256B64URL(topicTitleKeyBean.getSymmetricKey());

            // FIXED: Include salt in topic hash computation (prevents enumeration attacks)
            // This makes the topic hash non-deterministic and unpredictable
            String saltedTopicString = topicTitleKeyBean.getTopicTitle() +
                                       topicTitleKeyBean.getConversationSalt();
            String topicTitleHash = TkmSignUtils.Hash256B64URL(saltedTopicString);

            // Encrypt TopicTitleKeyBean (including salt)
            // Only participants can decrypt and retrieve the salt
            EncMessageBean topicDescription = getEncryptedTopic(topicTitleKeyBean, topicTitleKeyBean.getSymmetricKey());

            SignedContentTopicBean signedContentTopicBean = new SignedContentTopicBean(
                    topicTitleHash,  // Now contains salted hash
                    topicSymmetricKeySignature,
                    topicDescription,  // Contains encrypted salt
                    topicKeyDistributionMapBean);
            return signedContentTopicBean;
        } catch (HashEncodeException | HashAlgorithmNotFoundException | HashProviderNotFoundException | CryptoMessageException | InvalidParameterException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    public static final SignedTimestampRequestBean getSignedTimestampRequest(InstanceWalletKeystoreInterface signIwk, int sigIwkIndex) throws MessageException {
        return getSignedTimestampRequest(new Date().getTime(), signIwk, sigIwkIndex);
    }

    public static final SignedTimestampRequestBean getSignedTimestampRequest(Long timestamp, InstanceWalletKeystoreInterface signIwk, int sigIwkIndex) throws MessageException {
        try {
            BasicTimestampBean basicTimestampBean = new BasicTimestampBean(timestamp);
            String canonicalJson = SimpleRequestHelper.getCanonicalJson(basicTimestampBean);
            String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, sigIwkIndex);
            SignedTimestampRequestBean signedTimestampRequestBean = new SignedTimestampRequestBean(
                    basicTimestampBean,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.SIGNED_TIMESTAMP.name(),
                    signIwk.getWalletCypher().name());
            return signedTimestampRequestBean;
        } catch (WalletException | JsonProcessingException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    public static final SignedDownloadRequestBean getSignedDownloadRequestBean(
            final String topicTitle,
            final String uploadContentIdentifingHash,
            final Long timestamp,
            final InstanceWalletKeystoreInterface signIwk,
            final int signIwkIndex
    ) throws CryptoMessageException {
        try {
            final DownloadRequestBean downloadRequestBean = new DownloadRequestBean(topicTitle, uploadContentIdentifingHash, timestamp);
            final String canonicalJson = SimpleRequestHelper.getCanonicalJson(downloadRequestBean);
            final String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, signIwkIndex);
            final SignedDownloadRequestBean signedDownloadRequestBean = new SignedDownloadRequestBean(
                    downloadRequestBean,
                    signIwk.getPublicKeyAtIndexURL64(signIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.DOWNLOAD_REQUEST.name(),
                    signIwk.getWalletCypher().name());
            return signedDownloadRequestBean;
        } catch (MessageException | WalletException | JsonProcessingException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    public static final SignedUploadRequestBean getSignedUploadRequestBean(
            final String topicTitle,
            final String uploadContentSignature,
            final long size,
            final StreamEncryptedDescriptor sed,
            final InstanceWalletKeystoreInterface signIwk,
            final int sigIwkIndex
    ) throws CryptoMessageException {
        try {
            UploadRequestBean uploadRequestBean = new UploadRequestBean(topicTitle, uploadContentSignature, size, sed);
            String canonicalJson = SimpleRequestHelper.getCanonicalJson(uploadRequestBean);
            String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, sigIwkIndex);
            return new SignedUploadRequestBean(
                    uploadRequestBean,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.UPLOAD_REQUEST.name(),
                    signIwk.getWalletCypher().name()
            );
        } catch (WalletException | MessageException | JsonProcessingException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    public static final RegisterUserRequestBean getSignedRegisterUserRequest(NonceResponseBean nonceResponseBean, String rsaPublicKey, String rsaEncryptionType, InstanceWalletKeystoreInterface signIwk, int sigIwkIndex) throws MessageException {
        RegisterUserRequestSignedContentBean registerUserRequestSignedContentBean = new RegisterUserRequestSignedContentBean(nonceResponseBean, rsaPublicKey, rsaEncryptionType);
        RegisterUserRequestBean signedRegisteredUserRequests = getSignedRegisteredUserRequests(signIwk, sigIwkIndex, registerUserRequestSignedContentBean);
        return signedRegisteredUserRequests;
    }

    public static final RetrieveAllConversationsRequestBean getRetrieveAllConversationsRequestBean(Long notBefore, InstanceWalletKeystoreInterface signIwk, int sigIwkIndex) throws MessageException {
        try {
            RetrieveAllConversationsRequestContentBean retrieveAllConversationsRequestContentBean = new RetrieveAllConversationsRequestContentBean(notBefore);
            String messageSignature = SimpleRequestHelper.signChatMessage(SimpleRequestHelper.getCanonicalJson(retrieveAllConversationsRequestContentBean), signIwk, sigIwkIndex);
            RetrieveAllConversationsRequestBean retrieveAllConversationsRequestBean = new RetrieveAllConversationsRequestBean(
                    retrieveAllConversationsRequestContentBean,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    messageSignature,
                    CHAT_MESSAGE_TYPES.RETRIEVE_ALL_CONVERSATIONS.name(),
                    signIwk.getWalletCypher().name());
            return retrieveAllConversationsRequestBean;
            //return new RegisterUserRequestBean(registerUserRequestSignedContentBean, iwk.getPublicKeyAtIndexURL64(i), messageSignature, CHAT_MESSAGE_TYPES.REGISTER_USER_SIGNED_REQUEST.name(), iwk.getWalletCypher().name());
        } catch (JsonProcessingException | MessageException ex) {
            log.error("json error ", ex);
            throw new MessageException("json error ", ex);
        } catch (WalletException ex) {
            log.error("wallet error ", ex);
            throw new MessageException("wallet error ", ex);
        }

        //return signedRegisteredUserRequests;
    }

    public static final UserNotificationRequestBean getUserNotificationRequestBean(Long notBefore, boolean onlyUnread, InstanceWalletKeystoreInterface signIwk, int sigIwkIndex) throws MessageException {
        try {
            SignedNotificationRequestContentBean signedNotificationRequestContent = new SignedNotificationRequestContentBean(notBefore, onlyUnread);
            String messageSignature = SimpleRequestHelper.signChatMessage(SimpleRequestHelper.getCanonicalJson(signedNotificationRequestContent), signIwk, sigIwkIndex);
            UserNotificationRequestBean userNotificationRequestBean = new UserNotificationRequestBean(
                    signedNotificationRequestContent,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    messageSignature,
                    CHAT_MESSAGE_TYPES.NOTIFICATION_REQUEST.name(),
                    signIwk.getWalletCypher().name());
            return userNotificationRequestBean;
            //return new RegisterUserRequestBean(registerUserRequestSignedContentBean, iwk.getPublicKeyAtIndexURL64(i), messageSignature, CHAT_MESSAGE_TYPES.REGISTER_USER_SIGNED_REQUEST.name(), iwk.getWalletCypher().name());
        } catch (JsonProcessingException | MessageException ex) {
            log.error("json error ", ex);
            throw new MessageException("json error ", ex);
        } catch (WalletException ex) {
            log.error("wallet error ", ex);
            throw new MessageException("wallet error ", ex);
        }

        //return signedRegisteredUserRequests;
    }

    public static final TopicKeyDistributionItemBean getInviteForUser(RegisterUserRequestBean registerUserRequestBean, String topicSymmetricKey) throws CryptoMessageException {
        try {
            String encryptionPublicKey = registerUserRequestBean.getRegisterUserRequestSignedContentBean().getEncryptionPublicKey();
            TopicKeyDistributionItemBean invite = new TopicKeyDistributionItemBean(
                    TkmSignUtils.Hash256B64URL(encryptionPublicKey),
                    TkmCypherProviderBCRSA4096ENC
                            .encrypt(encryptionPublicKey, topicSymmetricKey));
            return invite;
        } catch (HashEncodeException | HashAlgorithmNotFoundException | HashProviderNotFoundException | WalletException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    public static final SignedMessageBean verifySignedMessage(String messageJson, String... from) throws ChatMessageException {
        return verifySignedMessage(messageJson, null, from);
    }

    public static final SignedMessageBean verifySignedMessage(String messageJson, Integer maxChar, String... from) throws ChatMessageException {
        try {
            SignedMessageBean fromJsonToSignedMessageBean = ChatUtils.fromJsonToSignedMessageBean(messageJson);
            final String jsonCanonical;
            final String pk;
            switch (from.length) {
                case 0:
                    pk = fromJsonToSignedMessageBean.getFrom();
                    break;
                case 1:
                    pk = from[0];
                    break;
                default:
                    throw new ChatMessageException("invalid parameters number, expected 0..1 got " + Arrays.toString(from));
            }
            final TkmCypherBean verifyResult;
            final SignedMessageBean returnObj;
            log.debug("dec {}", fromJsonToSignedMessageBean);
            if (fromJsonToSignedMessageBean == null) {
                throw new ChatMessageException("invalid json message");
            }
            if (fromJsonToSignedMessageBean.getFrom() == null
                    || fromJsonToSignedMessageBean.getFrom() == null
                    || fromJsonToSignedMessageBean.getMessageType() == null
                    || fromJsonToSignedMessageBean.getSignature() == null
                    || fromJsonToSignedMessageBean.getSignatureType() == null) {
                throw new ChatMessageException("missing required field");
            }
            switch (fromJsonToSignedMessageBean.getMessageType()) {
                case "REGISTER_USER_SIGNED_REQUEST" -> {
                    final RegisterUserRequestBean fromJsonToRegisterUserRequestBean = ChatUtils.fromJsonToRegisterUserRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToRegisterUserRequestBean.getRegisterUserRequestSignedContentBean());
                    returnObj = fromJsonToRegisterUserRequestBean;
                }
                case "REQUEST_USER_KEYS" -> {
                    final RequestUserKeyRequestBean fromJsonToRequestUserKeyRequestBean = ChatUtils.fromJsonToRequestUserKeyRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToRequestUserKeyRequestBean.getRequestUserKeyRequestBeanSignedContent());
                    returnObj = fromJsonToRequestUserKeyRequestBean;
                }
                case "TOPIC_CREATION" -> {
                    final CreateConversationRequestBean fromJsonToCreateConversationRequest = ChatUtils.fromJsonToCreateConversationRequest(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToCreateConversationRequest.getTopic());
                    returnObj = fromJsonToCreateConversationRequest;
                }
                case "TOPIC_MESSAGE" -> {
                    final BasicMessageRequestBean fromJsonToBasicMessageBeanRequest = ChatUtils.fromJsonToBasicMessageBeanRequest(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToBasicMessageBeanRequest.getBasicMessageSignedContentBean());
                    returnObj = fromJsonToBasicMessageBeanRequest;
                }
                case "TOPIC_MESSAGE_MEDIA" -> {
                    if (maxChar == null) {
                        final BasicMessageRequestBean fromJsonToBasicMessageBeanRequest = ChatUtils.fromJsonToBasicMessageBeanRequest(messageJson);
                        jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToBasicMessageBeanRequest.getBasicMessageSignedContentBean());
                        returnObj = fromJsonToBasicMessageBeanRequest;
                    } else {
                        final BasicMessageRequestBean fromJsonToBasicMessageBeanRequest = ChatUtils.fromJsonToBasicMessageBeanRequest(messageJson, maxChar);
                        jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToBasicMessageBeanRequest.getBasicMessageSignedContentBean());
                        returnObj = fromJsonToBasicMessageBeanRequest;
                    }
                }
                case "NOTIFICATION_REQUEST" -> {
                    final UserNotificationRequestBean fromJsonToUserNotificationRequestBean = ChatUtils.fromJsonToUserNotificationRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToUserNotificationRequestBean.getSignedNotificationRequestContent());
                    returnObj = fromJsonToUserNotificationRequestBean;
                }
                case "RETRIEVE_ALL_CONVERSATIONS" -> {
                    final RetrieveAllConversationsRequestBean fromJsonToRetrieveAllConversationsRequestBean = ChatUtils.fromJsonToRetrieveAllConversationsRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToRetrieveAllConversationsRequestBean.getAllConversationsRequestContentBean());
                    returnObj = fromJsonToRetrieveAllConversationsRequestBean;
                }
                case "RETRIEVE_MESSAGE_FROM_CONVERSATION_LAST_N", "RETRIEVE_MESSAGE_FROM_CONVERSATION_BY_SIGNATURE" -> {
                    final RetrieveMessageRequestBean fromJsonToRetrieveMessageRequestBean = ChatUtils.fromJsonToRetrieveMessageRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToRetrieveMessageRequestBean.getRetrieveMessageSignedRequestBean());
                    returnObj = fromJsonToRetrieveMessageRequestBean;
                }
                case "SIGNED_TIMESTAMP" -> {
                    final SignedTimestampRequestBean fromJsonToTimestampSignedRequestBean = ChatUtils.fromJsonToTimestampSignedRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToTimestampSignedRequestBean.getSignedTimestamp());
                    returnObj = fromJsonToTimestampSignedRequestBean;
                }
                case "DOWNLOAD_REQUEST" -> {
                    final SignedDownloadRequestBean fromJsonToSignedDownloadRequestBean = ChatUtils.fromJsonToSignedDownloadRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToSignedDownloadRequestBean.getDownloadRequestBean());
                    returnObj = fromJsonToSignedDownloadRequestBean;
                }
                case "UPLOAD_REQUEST" -> {
                    final SignedUploadRequestBean fromJsonToSignedUploadRequestBean = ChatUtils.fromJsonToSignedUploadRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToSignedUploadRequestBean.getUploadRequestBean());
                    returnObj = fromJsonToSignedUploadRequestBean;
                }

                case "RETRIEVE_CONVERSATION" -> {
                    final RetrieveConversationRequestBean fromJsonToRetrieveConversationRequestBean = ChatUtils.fromJsonToRetrieveConversationRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToRetrieveConversationRequestBean.getRetrieveConversationRequestContentBean());
                    returnObj = fromJsonToRetrieveConversationRequestBean;
                }

                default ->
                    throw new UnsupportedChatMessageTypeException("unsupported message type" + fromJsonToSignedMessageBean.getMessageType());
            }
            //java 17 limitation...
            switch (fromJsonToSignedMessageBean.getSignatureType()) {
                case "Ed25519BC":
                    verifyResult = TkmCypherProviderBCED25519.verify(pk, fromJsonToSignedMessageBean.getSignature(), jsonCanonical);
                    break;
                default:
                    throw new UnsupportedSignatureCypherException("unsupported message type" + fromJsonToSignedMessageBean.getSignatureType());
            }
            if (verifyResult.isValid()) {
                return returnObj;
            } else {
                throw new InvalidChatMessageSignatureException("invalid message signature");
            }
        } catch (JsonProcessingException ex) {
            throw new ChatMessageException(ex);
        }
    }

    public static final BasicMessageRequestBean getBasicMessageBean(InstanceWalletKeystoreInterface iwkSign, int index, String conversationHashName, String conversationEncryptionKey, List<String> citedUsers, BasicMessageEncryptedContentBean basicMessageEncryptedContentBean) throws ChatMessageException {
        try {
            //encrypt content
            EncMessageBean encContent
                    = ChatCryptoUtils
                            .getEncryptedBasicMessageEncryptedContentBean(
                                    basicMessageEncryptedContentBean,
                                    conversationEncryptionKey
                            );
            //signed content
            BasicMessageSignedContentBean basicMessageSignedContentBean
                    = new BasicMessageSignedContentBean(
                            conversationHashName,
                            citedUsers,
                            encContent
                    );
            String messageSignature
                    = SimpleRequestHelper.signChatMessage(
                            SimpleRequestHelper.getCanonicalJson(basicMessageSignedContentBean),
                            iwkSign,
                            index
                    );
            //server request
            BasicMessageRequestBean basicMessageBeanRequest
                    = new BasicMessageRequestBean(
                            basicMessageSignedContentBean,
                            iwkSign.getPublicKeyAtIndexURL64(index),
                            messageSignature,
                            CHAT_MESSAGE_TYPES.TOPIC_MESSAGE.name(),
                            iwkSign.getWalletCypher().name()
                    );
            return basicMessageBeanRequest;
        } catch (MessageException | JsonProcessingException | WalletException ex) {
            throw new ChatMessageException(ex);
        }
    }

//    public static final BasicMessageRequestBean getEncryptedMessageAttachmentBean(InstanceWalletKeystoreInterface iwkSign, int index, String conversationHashName, String conversationEncryptionKey, ChatMediaBean chatMediaBean) throws ChatMessageException {
//        try {
//            //encrypt content
//            EncMessageBean encContent
//                    = ChatCryptoUtils
//                            .getEncryptedMessageAttachmentMediaBean(
//                                    chatMediaBean,
//                                    conversationEncryptionKey
//                            );
//            //signed content
//            BasicMessageSignedContentBean basicMessageSignedContentBean
//                    = new BasicMessageSignedContentBean(
//                            conversationHashName,
//                            List.of(),
//                            encContent
//                    );
//            String messageSignature
//                    = SimpleRequestHelper.signChatMessage(
//                            SimpleRequestHelper.getCanonicalJson(basicMessageSignedContentBean),
//                            iwkSign,
//                            index
//                    );
//            //server request
//            BasicMessageRequestBean basicMessageBeanRequest
//                    = new BasicMessageRequestBean(
//                            basicMessageSignedContentBean,
//                            iwkSign.getPublicKeyAtIndexURL64(index),
//                            messageSignature,
//                            CHAT_MESSAGE_TYPES.TOPIC_MESSAGE_MEDIA.name(),
//                            iwkSign.getWalletCypher().name()
//                    );
//            return basicMessageBeanRequest;
//        } catch (MessageException | JsonProcessingException | WalletException ex) {
//            throw new ChatMessageException(ex);
//        }
//    }
    public static final RegisterUserRequestBean getSignedRegisteredUserRequests(InstanceWalletKeystoreInterface iwk, int i, RegisterUserRequestSignedContentBean registerUserRequestSignedContentBean) throws MessageException {
        try {
            String messageSignature = SimpleRequestHelper.signChatMessage(SimpleRequestHelper.getCanonicalJson(registerUserRequestSignedContentBean), iwk, i);
            return new RegisterUserRequestBean(registerUserRequestSignedContentBean, iwk.getPublicKeyAtIndexURL64(i), messageSignature, CHAT_MESSAGE_TYPES.REGISTER_USER_SIGNED_REQUEST.name(), iwk.getWalletCypher().name());
        } catch (JsonProcessingException | MessageException ex) {
            log.error("json error ", ex);
            throw new MessageException("json error ", ex);
        } catch (WalletException ex) {
            log.error("wallet error ", ex);
            throw new MessageException("wallet error ", ex);
        }
    }

    public static final RequestUserKeyRequestBean getRequestUserKeyRequestBean(InstanceWalletKeystoreInterface iwk, int i, List<RequestUserKeyRequestSignedContentBean> requestUserKeyRequestBeanSignedContent) throws MessageException {
        try {
            String messageSignature = SimpleRequestHelper.signChatMessage(SimpleRequestHelper.getCanonicalJson(requestUserKeyRequestBeanSignedContent), iwk, i);
            return new RequestUserKeyRequestBean(requestUserKeyRequestBeanSignedContent, iwk.getPublicKeyAtIndexURL64(i), messageSignature, CHAT_MESSAGE_TYPES.REQUEST_USER_KEYS.name(), iwk.getWalletCypher().name());
        } catch (JsonProcessingException | MessageException ex) {
            log.error("json error ", ex);
            throw new MessageException("json error ", ex);
        } catch (WalletException ex) {
            log.error("wallet error ", ex);
            throw new MessageException("wallet error ", ex);
        }
    }

    public static final CreateConversationRequestBean getSignedCreateConversationRequest(InstanceWalletKeystoreInterface iwk, int index, SignedContentTopicBean signedContentTopicBean) throws MessageException {
        try {
            String messageSignature = SimpleRequestHelper.signChatMessage(SimpleRequestHelper.getCanonicalJson(signedContentTopicBean), iwk, index);
            CreateConversationRequestBean createConversationRequest = new CreateConversationRequestBean(signedContentTopicBean, iwk.getPublicKeyAtIndexURL64(index), messageSignature, CHAT_MESSAGE_TYPES.TOPIC_CREATION.name(), iwk.getWalletCypher().name());
            return createConversationRequest;
        } catch (JsonProcessingException | MessageException | WalletException ex) {
            throw new MessageException(ex);
        }
    }

    public static final RetrieveMessageRequestBean getRetrieveMessageRequestBeanLastN(InstanceWalletKeystoreInterface iwk, int i, RetrieveMessageSignedRequestBean retrieveMessageSignedRequestBean) throws MessageException {
        try {
            String messageSignature = SimpleRequestHelper.signChatMessage(SimpleRequestHelper.getCanonicalJson(retrieveMessageSignedRequestBean), iwk, i);
            return new RetrieveMessageRequestBean(retrieveMessageSignedRequestBean, iwk.getPublicKeyAtIndexURL64(i), messageSignature, CHAT_MESSAGE_TYPES.RETRIEVE_MESSAGE_FROM_CONVERSATION_LAST_N.name(), iwk.getWalletCypher().name());
        } catch (JsonProcessingException | MessageException ex) {
            log.error("json error ", ex);
            throw new MessageException("json error ", ex);
        } catch (WalletException ex) {
            log.error("wallet error ", ex);
            throw new MessageException("wallet error ", ex);
        }
    }

    public static final RetrieveConversationRequestBean getRetrieveConversationRequestBean(InstanceWalletKeystoreInterface iwk, int i, RetrieveConversationRequestContentBean retrieveConversationRequestContentBean) throws MessageException {
        try {
            String messageSignature = SimpleRequestHelper.signChatMessage(SimpleRequestHelper.getCanonicalJson(retrieveConversationRequestContentBean), iwk, i);
            return new RetrieveConversationRequestBean(retrieveConversationRequestContentBean, iwk.getPublicKeyAtIndexURL64(i), messageSignature, CHAT_MESSAGE_TYPES.RETRIEVE_CONVERSATION.name(), iwk.getWalletCypher().name());
        } catch (JsonProcessingException | MessageException ex) {
            log.error("json error ", ex);
            throw new MessageException("json error ", ex);
        } catch (WalletException ex) {
            log.error("wallet error ", ex);
            throw new MessageException("wallet error ", ex);
        }
    }

    public static final RetrieveMessageRequestBean getRetrieveMessageRequestBeanBySignature(InstanceWalletKeystoreInterface iwk, int i, RetrieveMessageSignedRequestBean retrieveMessageSignedRequestBean) throws MessageException {
        try {
            String messageSignature = SimpleRequestHelper.signChatMessage(SimpleRequestHelper.getCanonicalJson(retrieveMessageSignedRequestBean), iwk, i);
            return new RetrieveMessageRequestBean(retrieveMessageSignedRequestBean, iwk.getPublicKeyAtIndexURL64(i), messageSignature, CHAT_MESSAGE_TYPES.RETRIEVE_MESSAGE_FROM_CONVERSATION_BY_SIGNATURE.name(), iwk.getWalletCypher().name());
        } catch (JsonProcessingException | MessageException ex) {
            log.error("json error ", ex);
            throw new MessageException("json error ", ex);
        } catch (WalletException ex) {
            log.error("wallet error ", ex);
            throw new MessageException("wallet error ", ex);
        }
    }
}
