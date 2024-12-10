/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.takamaka.extra.beans.CombinedRSAAESBean;
import io.takamaka.extra.beans.EncMessageBean;
import io.takamaka.extra.utils.EncryptionContext;
import io.takamaka.extra.utils.TkmEncryptionUtils;
import io.takamaka.messages.chat.BasicMessageEncryptedContentBean;
import io.takamaka.messages.chat.BasicMessageSignedContentBean;
import io.takamaka.messages.chat.BasicTimestampBean;
import io.takamaka.messages.chat.SignedContentTopicBean;
import io.takamaka.messages.chat.SignedMessageBean;
import io.takamaka.messages.chat.TopicKeyDistributionItemBean;
import io.takamaka.messages.chat.TopicKeyDistributionMapBean;
import io.takamaka.messages.chat.TopicTitleKeyBean;
import io.takamaka.messages.chat.notifications.SignedNotificationRequestContent;
import io.takamaka.messages.chat.requests.BasicMessageRequestBean;
import io.takamaka.messages.chat.requests.CreateConversationRequestBean;
import io.takamaka.messages.chat.requests.RegisterUserRequestBean;
import io.takamaka.messages.chat.requests.RegisterUserRequestSignedContentBean;
import io.takamaka.messages.chat.requests.RequestUserKeyRequestBean;
import io.takamaka.messages.chat.requests.RequestUserKeyRequestBeanSignedContent;
import io.takamaka.messages.chat.requests.RetrieveMessageRequestBean;
import io.takamaka.messages.chat.requests.RetrieveMessageSignedRequestBean;
import io.takamaka.messages.chat.requests.SignedTimestampRequestBean;
import io.takamaka.messages.chat.requests.UserNotificationRequestBean;
import io.takamaka.messages.chat.responses.NonceResponseBean;
import io.takamaka.messages.chat.responses.RetriveMessagesResponseBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.exception.CryptoMessageException;
import io.takamaka.messages.exception.InvalidChatMessageSignatureException;
import io.takamaka.messages.exception.InvalidParameterException;
import io.takamaka.messages.exception.MessageException;
import io.takamaka.messages.exception.UnsupportedChatMessageTypeException;
import io.takamaka.messages.exception.UnsupportedSignatureCypherException;
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
     * generate a random key using a secure rng implementation and appen the
     * topic title
     *
     * @param topicTitle title of the topic
     * @return
     * @throws InvalidParameterException
     */
    public static final TopicTitleKeyBean generateTopicKeyBean(String topicTitle) throws InvalidParameterException {
        return new TopicTitleKeyBean(topicTitle, generateRandomSafeKey());
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

    public static final TopicTitleKeyBean decryptTopicTitleKeyBean(EncMessageBean topicDescription, String symmetricConversationKey, String keyHash) throws ChatMessageException {
        try {
            String fromPasswordEncryptedContent = TkmEncryptionUtils.fromPasswordEncryptedContent(symmetricConversationKey, CHAT_MESSAGE_TYPES.TOPIC_CREATION.name(), topicDescription);
            TopicTitleKeyBean tkb = TkmTextUtils.getJacksonMapper().readValue(fromPasswordEncryptedContent, TopicTitleKeyBean.class);
            String decryptedKeyHash = TkmSignUtils.Hash256B64URL(tkb.getSymmetricKey());
            if (!decryptedKeyHash.equals(keyHash)) {
                throw new ChatMessageException(String.format("key hash %s does not match declared hash %s", decryptedKeyHash, keyHash));
            }
            return tkb;
        } catch (WalletException | JsonProcessingException | HashEncodeException | HashAlgorithmNotFoundException | HashProviderNotFoundException ex) {
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

    public static final BasicMessageEncryptedContentBean decryptBasicMessageEncryptedContentBean(EncMessageBean encryptedBasicMessageBean, String symmetricKey) throws ChatMessageException {
        try {
            String fromPasswordEncryptedContent = TkmEncryptionUtils.fromPasswordEncryptedContent(symmetricKey, CHAT_MESSAGE_TYPES.TOPIC_CREATION.name(), encryptedBasicMessageBean);
            BasicMessageEncryptedContentBean readValue = TkmTextUtils.getJacksonMapper().readValue(fromPasswordEncryptedContent, BasicMessageEncryptedContentBean.class);
            return readValue;
        } catch (WalletException | JsonProcessingException ex) {
            throw new ChatMessageException(ex);
        }
    }

    public static final SignedContentTopicBean getSignedContentTopicBean(TopicKeyDistributionMapBean topicKeyDistributionMapBean, TopicTitleKeyBean topicTitleKeyBean) throws CryptoMessageException {
        try {
            String topicSymmetricKeySignature = TkmSignUtils.Hash256B64URL(topicTitleKeyBean.getSymmetricKey());
            String topicTitleHash = TkmSignUtils.Hash256B64URL(topicTitleKeyBean.getTopicTitle());
            EncMessageBean topicDescription = getEncryptedTopic(topicTitleKeyBean, topicTitleKeyBean.getSymmetricKey());
            SignedContentTopicBean signedContentTopicBean = new SignedContentTopicBean(
                    topicTitleHash,
                    topicSymmetricKeySignature,
                    topicDescription,
                    topicKeyDistributionMapBean);
            return signedContentTopicBean;
        } catch (HashEncodeException | HashAlgorithmNotFoundException | HashProviderNotFoundException | CryptoMessageException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    public static final SignedTimestampRequestBean getSignedRegisterUserRequest(InstanceWalletKeystoreInterface signIwk, int sigIwkIndex) throws MessageException {
        return getSignedRegisterUserRequest(new Date().getTime(), signIwk, sigIwkIndex);
    }

    public static final SignedTimestampRequestBean getSignedRegisterUserRequest(Long timestamp, InstanceWalletKeystoreInterface signIwk, int sigIwkIndex) throws MessageException {
        try {
            BasicTimestampBean basicTimestampBean = new BasicTimestampBean(timestamp);
            String canonicalJson = SimpleRequestHelper.getCanonicalJson(timestamp);
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

    public static final RegisterUserRequestBean getSignedRegisterUserRequest(NonceResponseBean nonceResponseBean, String rsaPublicKey, String rsaEncryptionType, InstanceWalletKeystoreInterface signIwk, int sigIwkIndex) throws MessageException {
        RegisterUserRequestSignedContentBean registerUserRequestSignedContentBean = new RegisterUserRequestSignedContentBean(nonceResponseBean, rsaPublicKey, rsaEncryptionType);
        RegisterUserRequestBean signedRegisteredUserRequests = getSignedRegisteredUserRequests(signIwk, sigIwkIndex, registerUserRequestSignedContentBean);
        return signedRegisteredUserRequests;
    }

    public static final UserNotificationRequestBean getUserNotificationRequestBean(Long notBefore, boolean onlyUnread, InstanceWalletKeystoreInterface signIwk, int sigIwkIndex) throws MessageException {
        try {
            SignedNotificationRequestContent signedNotificationRequestContent = new SignedNotificationRequestContent(notBefore, onlyUnread);
            String messageSignature = SimpleRequestHelper.signChatMessage(SimpleRequestHelper.getCanonicalJson(signedNotificationRequestContent), signIwk, sigIwkIndex);
            UserNotificationRequestBean userNotificationRequestBean = new UserNotificationRequestBean(
                    signedNotificationRequestContent,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    messageSignature,
                    CHAT_MESSAGE_TYPES.NOTIFICATION_REQUEST.name(),
                    signIwk.getWalletCypher().name());
            return userNotificationRequestBean;
            //return new RegisterUserRequestBean(registerUserRequestSignedContentBean, iwk.getPublicKeyAtIndexURL64(i), messageSignature, CHAT_MESSAGE_TYPES.REGISTER_USER_SIGNED_REQUEST.name(), KeyContexts.WalletCypher.Ed25519BC.name());
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

    //    public static final boolean verifyCreateConversationRequestSignature(CreateConversationRequest createConversationRequest) throws ChatMessageException {
    //        try {
    ////            SignedContentTopicBean topic = createConversationRequest.getTopic();
    ////            String canonicalJson = SimpleRequestHelper.getCanonicalJson(topic);
    //            SignedMessageBean verifySignedMessage = verifySignedMessage(canonicalJson);
    //            if (verifySignedMessage.getFrom() != null) {
    //                return true;
    //            }
    //            return false;
    //        } catch (JsonProcessingException ex) {
    //            throw new ChatMessageException(ex);
    //        }
    //    }
    public static final SignedMessageBean verifySignedMessage(String messageJson, String... from) throws ChatMessageException {
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
            switch (fromJsonToSignedMessageBean.getMessageType()) {
                //java 17 limitation...
                case "REGISTER_USER_SIGNED_REQUEST":
                    RegisterUserRequestBean fromJsonToRegisterUserRequestBean = ChatUtils.fromJsonToRegisterUserRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToRegisterUserRequestBean.getRegisterUserRequestSignedContentBean());
                    returnObj = fromJsonToRegisterUserRequestBean;
                    break;
                case "REQUEST_USER_KEYS":
                    RequestUserKeyRequestBean fromJsonToRequestUserKeyRequestBean = ChatUtils.fromJsonToRequestUserKeyRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToRequestUserKeyRequestBean.getRequestUserKeyRequestBeanSignedContent());
                    returnObj = fromJsonToRequestUserKeyRequestBean;
                    break;
                case "TOPIC_CREATION":
                    CreateConversationRequestBean fromJsonToCreateConversationRequest = ChatUtils.fromJsonToCreateConversationRequest(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToCreateConversationRequest.getTopic());
                    returnObj = fromJsonToCreateConversationRequest;
                    break;
                case "TOPIC_MESSAGE":
                    BasicMessageRequestBean fromJsonToBasicMessageBeanRequest = ChatUtils.fromJsonToBasicMessageBeanRequest(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToBasicMessageBeanRequest.getBasicMessageSignedContentBean());
                    returnObj = fromJsonToBasicMessageBeanRequest;
                    break;
                case "NOTIFICATION_REQUEST":
                    UserNotificationRequestBean fromJsonToUserNotificationRequestBean = ChatUtils.fromJsonToUserNotificationRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToUserNotificationRequestBean.getSignedNotificationRequestContent());
                    returnObj = fromJsonToUserNotificationRequestBean;
                    break;
                case "RETRIEVE_MESSAGE_FROM_CONVERSATION_LAST_N":
                case "RETRIEVE_MESSAGE_FROM_CONVERSATION_BY_SIGNATURE":
                    RetrieveMessageRequestBean fromJsonToRetrieveMessageRequestBean = ChatUtils.fromJsonToRetrieveMessageRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToRetrieveMessageRequestBean.getRetrieveMessageSignedRequestBean());
                    returnObj = fromJsonToRetrieveMessageRequestBean;
                    break;

                case "SIGNED_TIMESTAMP":
                    SignedTimestampRequestBean fromJsonToTimestampSignedRequestBean = ChatUtils.fromJsonToTimestampSignedRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToTimestampSignedRequestBean.getSignedTimestamp());
                    returnObj = fromJsonToTimestampSignedRequestBean;
                    break;

                default:
                    throw new UnsupportedChatMessageTypeException("unsupported message type" + fromJsonToSignedMessageBean.getMessageType());
            }
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

    //    public static final CreateConversationRequestBean getSignedCreateConversationRequest(InstanceWalletKeystoreInterface iwk, int index, SignedContentTopicBean signedContentTopicBean) throws MessageException {
    //        try {
    //            String messageSignature = SimpleRequestHelper.signChatMessage(SimpleRequestHelper.getCanonicalJson(signedContentTopicBean), iwk, index);
    //            CreateConversationRequestBean createConversationRequest = new CreateConversationRequestBean(
    //                    signedContentTopicBean,
    //                    iwk.getPublicKeyAtIndexURL64(index),
    //                    messageSignature,
    //                    CHAT_MESSAGE_TYPES.TOPIC_CREATION.name(),
    //                    iwk.getWalletCypher().name());
    //            return createConversationRequest;
    //        } catch (JsonProcessingException | MessageException | WalletException ex) {
    //            throw new MessageException(ex);
    //        }
    //
    //    }
    public static final BasicMessageRequestBean getBasicMessageBean(InstanceWalletKeystoreInterface iwkSign, int index, String conversationHashName, String conversationEncryptionKey, List<String> citedUsers, BasicMessageEncryptedContentBean basicMessageEncryptedContentBean) throws ChatMessageException {
        try {
            //encrypt content
            EncMessageBean encContent = ChatCryptoUtils.getEncryptedBasicMessageEncryptedContentBean(basicMessageEncryptedContentBean, conversationEncryptionKey); //TODO CREATE CONTENT
            //signed content
            BasicMessageSignedContentBean basicMessageSignedContentBean = new BasicMessageSignedContentBean(conversationHashName, citedUsers, encContent);
            String messageSignature = SimpleRequestHelper.signChatMessage(SimpleRequestHelper.getCanonicalJson(basicMessageSignedContentBean), iwkSign, index);
            //server request
            BasicMessageRequestBean basicMessageBeanRequest = new BasicMessageRequestBean(basicMessageSignedContentBean, iwkSign.getPublicKeyAtIndexURL64(index), messageSignature, CHAT_MESSAGE_TYPES.TOPIC_MESSAGE.name(), iwkSign.getWalletCypher().name());
            return basicMessageBeanRequest;
        } catch (MessageException | JsonProcessingException | WalletException ex) {
            throw new ChatMessageException(ex);
        }
    }

    public static final RegisterUserRequestBean getSignedRegisteredUserRequests(InstanceWalletKeystoreInterface iwk, int i, RegisterUserRequestSignedContentBean registerUserRequestSignedContentBean) throws MessageException {
        try {
            String messageSignature = SimpleRequestHelper.signChatMessage(SimpleRequestHelper.getCanonicalJson(registerUserRequestSignedContentBean), iwk, i);
            return new RegisterUserRequestBean(registerUserRequestSignedContentBean, iwk.getPublicKeyAtIndexURL64(i), messageSignature, CHAT_MESSAGE_TYPES.REGISTER_USER_SIGNED_REQUEST.name(), KeyContexts.WalletCypher.Ed25519BC.name());
        } catch (JsonProcessingException | MessageException ex) {
            log.error("json error ", ex);
            throw new MessageException("json error ", ex);
        } catch (WalletException ex) {
            log.error("wallet error ", ex);
            throw new MessageException("wallet error ", ex);
        }
    }

    public static final RequestUserKeyRequestBean getRequestUserKeyRequestBean(InstanceWalletKeystoreInterface iwk, int i, List<RequestUserKeyRequestBeanSignedContent> requestUserKeyRequestBeanSignedContent) throws MessageException {
        try {
            String messageSignature = SimpleRequestHelper.signChatMessage(SimpleRequestHelper.getCanonicalJson(requestUserKeyRequestBeanSignedContent), iwk, i);
            return new RequestUserKeyRequestBean(requestUserKeyRequestBeanSignedContent, iwk.getPublicKeyAtIndexURL64(i), messageSignature, CHAT_MESSAGE_TYPES.REQUEST_USER_KEYS.name(), KeyContexts.WalletCypher.Ed25519BC.name());
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
            return new RetrieveMessageRequestBean(retrieveMessageSignedRequestBean, iwk.getPublicKeyAtIndexURL64(i), messageSignature, CHAT_MESSAGE_TYPES.RETRIEVE_MESSAGE_FROM_CONVERSATION_LAST_N.name(), KeyContexts.WalletCypher.Ed25519BC.name());
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
            return new RetrieveMessageRequestBean(retrieveMessageSignedRequestBean, iwk.getPublicKeyAtIndexURL64(i), messageSignature, CHAT_MESSAGE_TYPES.RETRIEVE_MESSAGE_FROM_CONVERSATION_BY_SIGNATURE.name(), KeyContexts.WalletCypher.Ed25519BC.name());
        } catch (JsonProcessingException | MessageException ex) {
            log.error("json error ", ex);
            throw new MessageException("json error ", ex);
        } catch (WalletException ex) {
            log.error("wallet error ", ex);
            throw new MessageException("wallet error ", ex);
        }
    }
}
