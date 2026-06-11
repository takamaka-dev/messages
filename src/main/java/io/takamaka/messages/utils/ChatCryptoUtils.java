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
import io.takamaka.messages.chat.options.GetUserOptionPeerRequestBean;
import io.takamaka.messages.chat.options.GetUserOptionPeerSignedContentBean;
import io.takamaka.messages.chat.options.GetUserOptionsRequestBean;
import io.takamaka.messages.chat.options.GetUserOptionsSignedContentBean;
import io.takamaka.messages.chat.options.ResetUserOptionsRequestBean;
import io.takamaka.messages.chat.options.ResetUserOptionsSignedContentBean;
import io.takamaka.messages.chat.options.SetUserOptionRequestBean;
import io.takamaka.messages.chat.options.SetUserOptionSignedContentBean;
import io.takamaka.messages.chat.receipt.ReadReceiptRequestBean;
import io.takamaka.messages.chat.receipt.ReadReceiptSignedContentBean;
import io.takamaka.messages.chat.receipt.ReadReceiptSubscribeBean;
import io.takamaka.messages.chat.receipt.ReadReceiptSubscribeSignedContentBean;
import io.takamaka.messages.chat.typing.TypingSubscribeBean;
import io.takamaka.messages.chat.typing.TypingSubscribeSignedContentBean;
import io.takamaka.messages.chat.core.NonceResponseBean;
import io.takamaka.messages.chat.message.RetrieveMessagesResponseBean;
import io.takamaka.messages.chat.attachment.ChatMediaPlaceholderBean;
import io.takamaka.messages.chat.attachment.InlineContentLimits;
import io.takamaka.messages.chat.message.MessageAction;
import io.takamaka.messages.chat.message.MessageActionValidator;
import io.takamaka.messages.chat.message.MessageProtocolVersion;
import io.takamaka.messages.exception.ChatCryptoConstructionException;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.exception.CryptoMessageException;
import io.takamaka.messages.exception.ForwardDepthExceededException;
import io.takamaka.messages.exception.InlineContentViolationException;
import io.takamaka.messages.exception.InvalidChatMessageSignatureException;
import io.takamaka.messages.exception.InvalidEmbeddedEnvelopeException;
import io.takamaka.messages.exception.InvalidParameterException;
import io.takamaka.messages.exception.MalformedTargetException;
import io.takamaka.messages.exception.MessageException;
import io.takamaka.messages.exception.UnsupportedChatMessageTypeException;
import io.takamaka.messages.exception.UnsupportedSignatureCypherException;
import static io.takamaka.messages.utils.ChatUtils.fromJsonToRetrieveConversationRequestBean;
import io.takamaka.wallet.InstanceWalletKeyStoreBCRSA4096ENC;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.TkmCypherProviderBCED25519;
import io.takamaka.wallet.TkmCypherProviderBCRSA4096ENC;
import io.takamaka.wallet.TkmCypherProviderBCRSA4096ENC256;
import io.takamaka.wallet.beans.TkmCypherBean;
import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.KeyContexts;
import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
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

    /**
     * Build a signed {@code setuseroption} request. The signature covers
     * {@code canonical(pl)}. The caller supplies a fresh server-issued nonce;
     * for ordered writes the client must fetch-then-use nonces sequentially
     * (USER_OPTIONS_DESIGN.md §8).
     */
    public static final SetUserOptionRequestBean getSignedSetUserOptionRequest(
            final NonceResponseBean nonce,
            final String parameterName,
            final String version,
            final String parameterJson,
            final Long clientTimestamp,
            final InstanceWalletKeystoreInterface signIwk,
            final int sigIwkIndex
    ) throws CryptoMessageException {
        try {
            final SetUserOptionSignedContentBean pl = new SetUserOptionSignedContentBean(
                    nonce, parameterName, version, parameterJson, clientTimestamp);
            final String canonicalJson = SimpleRequestHelper.getCanonicalJson(pl);
            final String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, sigIwkIndex);
            return new SetUserOptionRequestBean(
                    pl,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.SET_USER_OPTION.name(),
                    signIwk.getWalletCypher().name());
        } catch (WalletException | MessageException | JsonProcessingException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    /**
     * Build a signed {@code resetuseroptions} request (bulk reset). The nonce's
     * issue time becomes the per-user reset watermark.
     */
    public static final ResetUserOptionsRequestBean getSignedResetUserOptionsRequest(
            final NonceResponseBean nonce,
            final InstanceWalletKeystoreInterface signIwk,
            final int sigIwkIndex
    ) throws CryptoMessageException {
        try {
            final ResetUserOptionsSignedContentBean pl = new ResetUserOptionsSignedContentBean(nonce);
            final String canonicalJson = SimpleRequestHelper.getCanonicalJson(pl);
            final String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, sigIwkIndex);
            return new ResetUserOptionsRequestBean(
                    pl,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.RESET_USER_OPTIONS.name(),
                    signIwk.getWalletCypher().name());
        } catch (WalletException | MessageException | JsonProcessingException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    /**
     * Build a signed {@code getuseroptions} self-read request (no nonce).
     *
     * @param parameterName a specific option, or {@code null} for all options
     */
    public static final GetUserOptionsRequestBean getSignedGetUserOptionsRequest(
            final String parameterName,
            final Long clientTimestamp,
            final InstanceWalletKeystoreInterface signIwk,
            final int sigIwkIndex
    ) throws CryptoMessageException {
        try {
            final GetUserOptionsSignedContentBean pl = new GetUserOptionsSignedContentBean(parameterName, clientTimestamp);
            final String canonicalJson = SimpleRequestHelper.getCanonicalJson(pl);
            final String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, sigIwkIndex);
            return new GetUserOptionsRequestBean(
                    pl,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.GET_USER_OPTIONS.name(),
                    signIwk.getWalletCypher().name());
        } catch (WalletException | MessageException | JsonProcessingException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    /**
     * Build a signed {@code getuseroptionpeer} peer-read request (D10, no
     * nonce). Returns the target's declared projection subject to visibility.
     */
    public static final GetUserOptionPeerRequestBean getSignedGetUserOptionPeerRequest(
            final String targetPublicKey,
            final String parameterName,
            final Long clientTimestamp,
            final InstanceWalletKeystoreInterface signIwk,
            final int sigIwkIndex
    ) throws CryptoMessageException {
        try {
            final GetUserOptionPeerSignedContentBean pl = new GetUserOptionPeerSignedContentBean(targetPublicKey, parameterName, clientTimestamp);
            final String canonicalJson = SimpleRequestHelper.getCanonicalJson(pl);
            final String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, sigIwkIndex);
            return new GetUserOptionPeerRequestBean(
                    pl,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.GET_USER_OPTION_PEER.name(),
                    signIwk.getWalletCypher().name());
        } catch (WalletException | MessageException | JsonProcessingException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    // ===== Read receipts (Deliverable B) — READ_RECEIPT_DESIGN.md §6/§12 =====

    /**
     * Build a signed read receipt: AES-CBC encrypt the last-read message
     * signature under the conversation key with scope {@code "READ_RECEIPT"}
     * (domain-separated from messages, §12.3), then sign {@code canonical(pl)}.
     * The server learns <em>that</em> the reader read in the conversation, never
     * <em>which</em> message. {@code ts} is left null (server-set at fan-out).
     */
    public static final ReadReceiptRequestBean getReadReceiptBean(
            final InstanceWalletKeystoreInterface iwkSign, final int index,
            final String conversationHashName, final String conversationEncryptionKey,
            final String lastReadMessageSignature) throws CryptoMessageException {
        try {
            final EncMessageBean enc = TkmEncryptionUtils.toPasswordEncryptedContent(
                    conversationEncryptionKey, lastReadMessageSignature,
                    CHAT_MESSAGE_TYPES.READ_RECEIPT.name(),       // scope = domain-separating salt (== message_type)
                    EncryptionContext.v0_1_a.name());
            final ReadReceiptSignedContentBean pl = new ReadReceiptSignedContentBean(
                    conversationHashName,
                    enc.getEncryptedMessage()[1],                 // em[1] = ciphertext
                    enc.getEncryptedMessage()[0],                 // em[0] = iv
                    MessageProtocolVersion.V_1_0,                 // pv
                    EncryptionContext.v0_1_a.name());             // v
            final String signature = SimpleRequestHelper.signChatMessage(
                    SimpleRequestHelper.getCanonicalJson(pl), iwkSign, index);
            return new ReadReceiptRequestBean(
                    pl,
                    iwkSign.getPublicKeyAtIndexURL64(index),
                    signature,
                    CHAT_MESSAGE_TYPES.READ_RECEIPT.name(),
                    iwkSign.getWalletCypher().name());
        } catch (MessageException | JsonProcessingException | WalletException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    /**
     * Decrypt mirror of {@link #getReadReceiptBean}: rebuild the
     * {@link EncMessageBean} from the pinned {@code pl.v} params + {@code pl.iv}
     * + {@code pl.enc}, then decrypt to the last-read message signature
     * (watermark). <b>Verify the envelope signature BEFORE calling this</b>
     * (verify-then-decrypt, D3).
     */
    public static final String decryptReadReceiptWatermark(
            final ReadReceiptSignedContentBean pl, final String conversationEncryptionKey) throws CryptoMessageException {
        try {
            final EncryptionContext ctx = EncryptionContext.valueOf(pl.getCipherVersion());
            final EncMessageBean emb = new EncMessageBean(
                    ctx.getPasswordHashAlgorithm(), ctx.getIterations(), ctx.getTransformation(),
                    ctx.getKeySpecAlgorithm(), ctx.name(), ctx.getOutputKeyLengthBit(), ctx.getEncoding(),
                    new String[]{pl.getIv(), pl.getEncryptedWatermark()});
            return TkmEncryptionUtils.fromPasswordEncryptedContent(
                    conversationEncryptionKey, CHAT_MESSAGE_TYPES.READ_RECEIPT.name(), emb);
        } catch (WalletException | IllegalArgumentException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    /**
     * Build a signed {@code retrievereadreceipts} subscribe carrying a fresh
     * server-issued nonce (F3 fix, §8). A reconnect/unmute must fetch a fresh
     * nonce (the prior one is consumed).
     */
    public static final ReadReceiptSubscribeBean getReadReceiptSubscribeBean(
            final NonceResponseBean nonce, final Long notBefore,
            final InstanceWalletKeystoreInterface iwkSign, final int index) throws CryptoMessageException {
        try {
            final ReadReceiptSubscribeSignedContentBean pl = new ReadReceiptSubscribeSignedContentBean(nonce, notBefore);
            final String signature = SimpleRequestHelper.signChatMessage(
                    SimpleRequestHelper.getCanonicalJson(pl), iwkSign, index);
            return new ReadReceiptSubscribeBean(
                    pl,
                    iwkSign.getPublicKeyAtIndexURL64(index),
                    signature,
                    CHAT_MESSAGE_TYPES.RETRIEVE_READ_RECEIPTS.name(),
                    iwkSign.getWalletCypher().name());
        } catch (MessageException | JsonProcessingException | WalletException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    // ===== Typing indicator — TYPING_INDICATOR_DESIGN.md D2/D3 =====

    /**
     * Build the one-time signed typing subscribe (the ONLY signed typing call).
     * Emits afterwards are plain {@code TypingEmitBean} fire-and-forget frames —
     * no helper needed (just {@code new TypingEmitBean(conv, pv)}).
     */
    public static final TypingSubscribeBean getTypingSubscribeBean(
            final InstanceWalletKeystoreInterface signIwk, final int sigIwkIndex) throws CryptoMessageException {
        try {
            final TypingSubscribeSignedContentBean pl = new TypingSubscribeSignedContentBean(
                    new Date().getTime(), MessageProtocolVersion.V_1_0);
            final String canonicalJson = SimpleRequestHelper.getCanonicalJson(pl);
            final String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, sigIwkIndex);
            return new TypingSubscribeBean(
                    pl,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.TYPING_SUBSCRIBE.name(),
                    signIwk.getWalletCypher().name());
        } catch (MessageException | JsonProcessingException | WalletException ex) {
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
            String encryptionPublicKeyType = registerUserRequestBean.getRegisterUserRequestSignedContentBean().getEncryptionPublicKeyType();
            String encryptedKey = switch (encryptionPublicKeyType) {
                case "RSA_4096_ECB_OAEP_SHA256" -> TkmCypherProviderBCRSA4096ENC256.encrypt(encryptionPublicKey, topicSymmetricKey);
                default -> TkmCypherProviderBCRSA4096ENC.encrypt(encryptionPublicKey, topicSymmetricKey);
            };
            TopicKeyDistributionItemBean invite = new TopicKeyDistributionItemBean(
                    TkmSignUtils.Hash256B64URL(encryptionPublicKey),
                    encryptedKey);
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
                case "SET_USER_OPTION" -> {
                    final SetUserOptionRequestBean setUserOptionRequestBean = ChatUtils.fromJsonToSetUserOptionRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(setUserOptionRequestBean.getPl());
                    returnObj = setUserOptionRequestBean;
                }
                case "RESET_USER_OPTIONS" -> {
                    final ResetUserOptionsRequestBean resetUserOptionsRequestBean = ChatUtils.fromJsonToResetUserOptionsRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(resetUserOptionsRequestBean.getPl());
                    returnObj = resetUserOptionsRequestBean;
                }
                case "GET_USER_OPTIONS" -> {
                    final GetUserOptionsRequestBean getUserOptionsRequestBean = ChatUtils.fromJsonToGetUserOptionsRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(getUserOptionsRequestBean.getPl());
                    returnObj = getUserOptionsRequestBean;
                }
                case "GET_USER_OPTION_PEER" -> {
                    final GetUserOptionPeerRequestBean getUserOptionPeerRequestBean = ChatUtils.fromJsonToGetUserOptionPeerRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(getUserOptionPeerRequestBean.getPl());
                    returnObj = getUserOptionPeerRequestBean;
                }
                case "READ_RECEIPT" -> {
                    final ReadReceiptRequestBean readReceiptRequestBean = ChatUtils.fromJsonToReadReceiptRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(readReceiptRequestBean.getPl());
                    returnObj = readReceiptRequestBean;
                }
                case "RETRIEVE_READ_RECEIPTS" -> {
                    final ReadReceiptSubscribeBean readReceiptSubscribeBean = ChatUtils.fromJsonToReadReceiptSubscribeBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(readReceiptSubscribeBean.getPl());
                    returnObj = readReceiptSubscribeBean;
                }
                case "TYPING_SUBSCRIBE" -> {
                    final TypingSubscribeBean typingSubscribeBean = ChatUtils.fromJsonToTypingSubscribeBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(typingSubscribeBean.getPl());
                    returnObj = typingSubscribeBean;
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

    // ========================================================================
    // Phase 1 (Messages 1.5.0) — message-action construction helpers (§3.4).
    // One canonical-construction method per action; shared internal predicates
    // for the genuinely-common work. Every helper auto-stamps
    // client_protocol_version = MessageProtocolVersion.CURRENT.
    // ========================================================================

    static final Pattern SIGNATURE_TARGET = Pattern.compile("^[A-Za-z0-9_-]{86}\\.\\.$");
    static final Pattern PUBLIC_KEY_TARGET = Pattern.compile("^[A-Za-z0-9_-]{43}\\.$");

    /** Plain message (no action; helper still auto-stamps the version). */
    public static BasicMessageRequestBean getPlainMessageBean(
            SendContext ctx, String textMessage,
            List<ChatMediaPlaceholderBean> attachedMedia, List<String> citedUsers)
            throws ChatCryptoConstructionException {
        validateSendContext(ctx);
        BasicMessageEncryptedContentBean inner = BasicMessageEncryptedContentBean.builder()
                .textMessage(textMessage)
                .attachedMedia(attachedMedia)
                .clientProtocolVersion(MessageProtocolVersion.CURRENT)
                .build();
        return buildAndSign(ctx, inner, citedUsers);
    }

    public static BasicMessageRequestBean getReplyMessageBean(
            SendContext ctx, String parentSignature, String replyText,
            List<ChatMediaPlaceholderBean> attachedMedia, List<String> citedUsers)
            throws ChatCryptoConstructionException, MalformedTargetException {
        validateSendContext(ctx);
        validateSignatureFormat(parentSignature,
                ChatCryptoConstructionException.MISSING_PARENT_SIGNATURE,
                ChatCryptoConstructionException.MALFORMED_PARENT_SIGNATURE);
        BasicMessageEncryptedContentBean inner = BasicMessageEncryptedContentBean.builder()
                .textMessage(replyText)
                .attachedMedia(attachedMedia)
                .action(MessageAction.REPLY)
                .targets(List.of(parentSignature))
                .clientProtocolVersion(MessageProtocolVersion.CURRENT)
                .build();
        return buildAndSign(ctx, inner, citedUsers);
    }

    public static BasicMessageRequestBean getReactionMessageBean(
            SendContext ctx, String parentSignature,
            ChatMediaPlaceholderBean reactionPayload, List<String> citedUsers)
            throws ChatCryptoConstructionException, MalformedTargetException, InlineContentViolationException {
        validateSendContext(ctx);
        validateSignatureFormat(parentSignature,
                ChatCryptoConstructionException.MISSING_PARENT_SIGNATURE,
                ChatCryptoConstructionException.MALFORMED_PARENT_SIGNATURE);
        if (reactionPayload == null) {
            throw new ChatCryptoConstructionException(
                    ChatCryptoConstructionException.MISSING_REACTION_PAYLOAD,
                    "reaction payload is required");
        }
        validateReactionPayload(reactionPayload);
        BasicMessageEncryptedContentBean inner = BasicMessageEncryptedContentBean.builder()
                .attachedMedia(List.of(reactionPayload))
                .action(MessageAction.REACTION)
                .targets(List.of(parentSignature))
                .clientProtocolVersion(MessageProtocolVersion.CURRENT)
                .build();
        return buildAndSign(ctx, inner, citedUsers);
    }

    public static BasicMessageRequestBean getReactionRemoveMessageBean(
            SendContext ctx, String parentSignature)
            throws ChatCryptoConstructionException, MalformedTargetException {
        validateSendContext(ctx);
        validateSignatureFormat(parentSignature,
                ChatCryptoConstructionException.MISSING_PARENT_SIGNATURE,
                ChatCryptoConstructionException.MALFORMED_PARENT_SIGNATURE);
        BasicMessageEncryptedContentBean inner = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.REACTION_REMOVE)
                .targets(List.of(parentSignature))
                .clientProtocolVersion(MessageProtocolVersion.CURRENT)
                .build();
        return buildAndSign(ctx, inner, null);
    }

    public static BasicMessageRequestBean getEditMessageBean(
            SendContext ctx, String parentSignature, String newText,
            List<ChatMediaPlaceholderBean> newAttachedMedia, List<String> citedUsers)
            throws ChatCryptoConstructionException, MalformedTargetException {
        validateSendContext(ctx);
        validateSignatureFormat(parentSignature,
                ChatCryptoConstructionException.MISSING_PARENT_SIGNATURE,
                ChatCryptoConstructionException.MALFORMED_PARENT_SIGNATURE);
        BasicMessageEncryptedContentBean inner = BasicMessageEncryptedContentBean.builder()
                .textMessage(newText)
                .attachedMedia(newAttachedMedia)
                .action(MessageAction.EDIT)
                .targets(List.of(parentSignature))
                .clientProtocolVersion(MessageProtocolVersion.CURRENT)
                .build();
        return buildAndSign(ctx, inner, citedUsers);
    }

    public static BasicMessageRequestBean getRedactMessageBean(
            SendContext ctx, String parentSignature, String optionalReason)
            throws ChatCryptoConstructionException, MalformedTargetException {
        validateSendContext(ctx);
        validateSignatureFormat(parentSignature,
                ChatCryptoConstructionException.MISSING_PARENT_SIGNATURE,
                ChatCryptoConstructionException.MALFORMED_PARENT_SIGNATURE);
        BasicMessageEncryptedContentBean inner = BasicMessageEncryptedContentBean.builder()
                .textMessage(optionalReason)
                .action(MessageAction.REDACT)
                .targets(List.of(parentSignature))
                .clientProtocolVersion(MessageProtocolVersion.CURRENT)
                .build();
        return buildAndSign(ctx, inner, null);
    }

    public static BasicMessageRequestBean getPinMessageBean(
            SendContext ctx, String targetMessageSignature, String optionalReason)
            throws ChatCryptoConstructionException, MalformedTargetException {
        validateSendContext(ctx);
        validateSignatureFormat(targetMessageSignature,
                ChatCryptoConstructionException.MISSING_TARGET_MESSAGE_SIGNATURE,
                ChatCryptoConstructionException.MALFORMED_TARGET_MESSAGE_SIGNATURE);
        validatePinReason(optionalReason);
        BasicMessageEncryptedContentBean inner = BasicMessageEncryptedContentBean.builder()
                .textMessage(optionalReason)
                .action(MessageAction.PIN)
                .targets(List.of(targetMessageSignature))
                .clientProtocolVersion(MessageProtocolVersion.CURRENT)
                .build();
        return buildAndSign(ctx, inner, null);
    }

    public static BasicMessageRequestBean getUnpinMessageBean(SendContext ctx)
            throws ChatCryptoConstructionException {
        validateSendContext(ctx);
        BasicMessageEncryptedContentBean inner = BasicMessageEncryptedContentBean.builder()
                .action(MessageAction.UNPIN)
                .clientProtocolVersion(MessageProtocolVersion.CURRENT)
                .build();
        return buildAndSign(ctx, inner, null);
    }

    public static BasicMessageRequestBean getForwardMessageBean(
            SendContext ctx, BasicMessageEncryptedContentBean beanToForward,
            String forwarderText, String claimedOriginPk)
            throws ChatCryptoConstructionException, ForwardDepthExceededException {
        validateSendContext(ctx);
        if (beanToForward == null) {
            throw new ChatCryptoConstructionException(
                    ChatCryptoConstructionException.INCOHERENT_BEAN_CONSTRUCTION,
                    "beanToForward is required");
        }
        validatePublicKeyFormat(claimedOriginPk);
        int prospectiveDepth = 1 + walkForwardDepth(beanToForward);
        if (prospectiveDepth > MessageActionValidator.MAX_FORWARD_DEPTH) {
            throw new ForwardDepthExceededException(prospectiveDepth, MessageActionValidator.MAX_FORWARD_DEPTH);
        }
        List<String> targets = (claimedOriginPk == null || claimedOriginPk.isBlank())
                ? List.of() : List.of(claimedOriginPk);
        BasicMessageEncryptedContentBean inner = BasicMessageEncryptedContentBean.builder()
                .textMessage(forwarderText)
                .action(MessageAction.FORWARD)
                .targets(targets)
                .fwContent(beanToForward)
                .clientProtocolVersion(MessageProtocolVersion.CURRENT)
                .build();
        return buildAndSign(ctx, inner, null);
    }

    public static BasicMessageRequestBean getShareHistoryMessageBean(
            SendContext ctx, BasicMessageRequestBean originalEnvelope,
            String relayerNote, boolean reShared)
            throws ChatCryptoConstructionException, InvalidEmbeddedEnvelopeException {
        validateSendContext(ctx);
        if (originalEnvelope == null) {
            throw new InvalidEmbeddedEnvelopeException(
                    ChatCryptoConstructionException.MISSING_ORIGINAL_ENVELOPE,
                    "original envelope is required");
        }
        verifyEmbeddedSignature(originalEnvelope);
        String innerConversation = originalEnvelope.getBasicMessageSignedContentBean() == null
                ? null : originalEnvelope.getBasicMessageSignedContentBean().getConversationHashName();
        if (innerConversation == null || !innerConversation.equals(ctx.conversationHashName())) {
            throw new InvalidEmbeddedEnvelopeException(
                    ChatCryptoConstructionException.EMBEDDED_INNER_CONVERSATION_MISMATCH,
                    "embedded original_message belongs to a different conversation");
        }
        rejectNestedShareHistory(ctx, originalEnvelope);
        BasicMessageEncryptedContentBean inner = BasicMessageEncryptedContentBean.builder()
                .textMessage(relayerNote)
                .action(MessageAction.SHARE_HISTORY)
                .originalMessage(originalEnvelope)
                .reShared(reShared ? Boolean.TRUE : null)
                .clientProtocolVersion(MessageProtocolVersion.CURRENT)
                .build();
        return buildAndSign(ctx, inner, null);
    }

    // ---- internal helpers (shared across the public methods) ---------------

    private static BasicMessageRequestBean buildAndSign(SendContext ctx,
            BasicMessageEncryptedContentBean inner, List<String> citedUsers)
            throws ChatCryptoConstructionException {
        try {
            return getBasicMessageBean(
                    ctx.signingWallet(), ctx.keyIndex(), ctx.conversationHashName(),
                    ctx.conversationEncryptionKey(), citedUsers, inner);
        } catch (ChatMessageException ex) {
            throw new ChatCryptoConstructionException(
                    ChatCryptoConstructionException.INCOHERENT_BEAN_CONSTRUCTION,
                    "failed to encrypt and sign message: " + ex.getMessage(), ex);
        }
    }

    private static void validateSendContext(SendContext ctx) throws ChatCryptoConstructionException {
        if (ctx == null) {
            throw new ChatCryptoConstructionException(
                    ChatCryptoConstructionException.INCOHERENT_BEAN_CONSTRUCTION, "SendContext is null");
        }
        if (ctx.signingWallet() == null) {
            throw new ChatCryptoConstructionException(
                    ChatCryptoConstructionException.INCOHERENT_BEAN_CONSTRUCTION, "signingWallet is null");
        }
        if (TkmTextUtils.isNullOrBlank(ctx.conversationHashName())) {
            throw new ChatCryptoConstructionException(
                    ChatCryptoConstructionException.INCOHERENT_BEAN_CONSTRUCTION, "conversationHashName is required");
        }
        if (TkmTextUtils.isNullOrBlank(ctx.conversationEncryptionKey())) {
            throw new ChatCryptoConstructionException(
                    ChatCryptoConstructionException.INCOHERENT_BEAN_CONSTRUCTION, "conversationEncryptionKey is required");
        }
    }

    private static void validateSignatureFormat(String signature, String missingCode, String malformedCode)
            throws MalformedTargetException {
        if (TkmTextUtils.isNullOrBlank(signature)) {
            throw new MalformedTargetException(missingCode, "signature target is missing");
        }
        if (!SIGNATURE_TARGET.matcher(signature).matches()) {
            throw new MalformedTargetException(malformedCode, "malformed signature target: " + signature);
        }
    }

    private static void validatePublicKeyFormat(String publicKey) throws MalformedTargetException {
        if (publicKey == null || publicKey.isBlank()) {
            return; // claimed-origin PK is optional (anonymous forward)
        }
        if (!PUBLIC_KEY_TARGET.matcher(publicKey).matches()) {
            throw new MalformedTargetException(
                    ChatCryptoConstructionException.MALFORMED_CLAIMED_ORIGIN_PK,
                    "malformed claimed-origin public key: " + publicKey);
        }
    }

    private static void validatePinReason(String reason) {
        if (reason != null && reason.length() > 200) {
            log.warn("pin reason exceeds the recommended 200-character limit ({} chars); code {}",
                    reason.length(), ChatCryptoConstructionException.PIN_REASON_TOO_LONG);
        }
    }

    private static void validateReactionPayload(ChatMediaPlaceholderBean payload)
            throws InlineContentViolationException {
        if (!Boolean.TRUE.equals(payload.getIsTheObject())) {
            return; // not inline content; nothing to enforce here
        }
        if (!InlineContentLimits.isReactionImageMimeAllowed(payload.getMediaType())) {
            throw new InlineContentViolationException(
                    ChatCryptoConstructionException.REACTION_MIME_NOT_ALLOWED,
                    "reaction inline media type not allowed: " + payload.getMediaType());
        }
        String preview = payload.getPreview();
        if (preview == null) {
            throw new InlineContentViolationException(
                    ChatCryptoConstructionException.INLINE_DECODE_FAILURE,
                    "inline reaction payload has no preview content");
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(preview);
        } catch (IllegalArgumentException ex) {
            throw new InlineContentViolationException(
                    ChatCryptoConstructionException.INLINE_DECODE_FAILURE,
                    "inline reaction payload preview is not valid standard Base64", ex);
        }
        if (decoded.length > InlineContentLimits.MAX_INLINE_BYTES) {
            throw new InlineContentViolationException(
                    ChatCryptoConstructionException.INLINE_CONTENT_TOO_LARGE,
                    "inline reaction payload exceeds " + InlineContentLimits.MAX_INLINE_BYTES + " bytes");
        }
    }

    /**
     * Counts the {@code fw_content} chain depth (0 for a leaf, i.e. a bean
     * with no {@code fw_content}).
     */
    static int walkForwardDepth(BasicMessageEncryptedContentBean bean) {
        int depth = 0;
        BasicMessageEncryptedContentBean node = bean == null ? null : bean.getFwContent();
        while (node != null) {
            depth++;
            node = node.getFwContent();
        }
        return depth;
    }

    private static void verifyEmbeddedSignature(BasicMessageRequestBean originalEnvelope)
            throws InvalidEmbeddedEnvelopeException {
        try {
            String canonical = SimpleRequestHelper.getCanonicalJson(originalEnvelope.getBasicMessageSignedContentBean());
            TkmCypherBean verify = TkmCypherProviderBCED25519.verify(
                    originalEnvelope.getFrom(), originalEnvelope.getSignature(), canonical);
            if (!verify.isValid()) {
                throw new InvalidEmbeddedEnvelopeException(
                        ChatCryptoConstructionException.EMBEDDED_INNER_SIGNATURE_INVALID,
                        "embedded original_message inner signature is invalid");
            }
        } catch (JsonProcessingException ex) {
            throw new InvalidEmbeddedEnvelopeException(
                    ChatCryptoConstructionException.EMBEDDED_INNER_SIGNATURE_INVALID,
                    "embedded original_message could not be canonicalized for signature verification", ex);
        }
    }

    private static void rejectNestedShareHistory(SendContext ctx, BasicMessageRequestBean originalEnvelope)
            throws InvalidEmbeddedEnvelopeException {
        final BasicMessageEncryptedContentBean innerContent;
        try {
            innerContent = decryptBasicMessageEncryptedContentBeanWithScope(
                    originalEnvelope.getBasicMessageSignedContentBean().getEncryptedContent(),
                    ctx.conversationEncryptionKey(),
                    CHAT_MESSAGE_TYPES.TOPIC_MESSAGE);
        } catch (ChatMessageException ex) {
            // Cannot decrypt the embed (key mismatch / malformed) — leave the
            // nested check best-effort; the conversation-hash check above is
            // the authoritative same-conversation guard.
            log.warn("share_history nested-check skipped: embedded content not decryptable", ex);
            return;
        }
        if (MessageAction.SHARE_HISTORY.equals(MessageAction.normalize(innerContent.getAction()))) {
            throw new InvalidEmbeddedEnvelopeException(
                    ChatCryptoConstructionException.NESTED_SHARE_HISTORY,
                    "cannot embed a share_history inside another share_history");
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
