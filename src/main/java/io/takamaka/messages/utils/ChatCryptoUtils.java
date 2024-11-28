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
import io.takamaka.messages.chat.SignedContentTopicBean;
import io.takamaka.messages.chat.TopicKeyDistributionItemBean;
import io.takamaka.messages.chat.TopicKeyDistributionMapBean;
import io.takamaka.messages.chat.TopicTitleKeyBean;
import io.takamaka.messages.chat.requests.RegisterUserRequestBean;
import io.takamaka.messages.chat.requests.RegisterUserRequestSignedContentBean;
import io.takamaka.messages.chat.responses.NonceResponseBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.exception.CryptoMessageException;
import io.takamaka.messages.exception.InvalidParameterException;
import io.takamaka.messages.exception.MessageException;
import io.takamaka.wallet.InstanceWalletKeyStoreBCRSA4096ENC;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.TkmCypherProviderBCRSA4096ENC;
import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.text.RandomStringGenerator;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
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

    public static final RegisterUserRequestBean getSignedRegisterUserRequest(NonceResponseBean nonceResponseBean, String rsaPublicKey, String rsaEncryptionType, InstanceWalletKeystoreInterface signIwk, int sigIwkIndex) throws MessageException {
        RegisterUserRequestSignedContentBean registerUserRequestSignedContentBean = new RegisterUserRequestSignedContentBean(nonceResponseBean, rsaPublicKey, rsaEncryptionType);
        RegisterUserRequestBean signedRegisteredUserRequests = ChatUtils.getSignedRegisteredUserRequests(signIwk, sigIwkIndex, registerUserRequestSignedContentBean);
        return signedRegisteredUserRequests;
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
}
