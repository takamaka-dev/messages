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
import io.takamaka.messages.chat.message.DeleteMessageRequestBean;
import io.takamaka.messages.chat.message.RetrieveDeletionsRequestBean;
import io.takamaka.messages.chat.message.RetrieveDeletionsSignedContentBean;
import io.takamaka.messages.chat.message.DeleteMessageSignedContentBean;
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
import io.takamaka.messages.chat.profile.ClearUserProfileRequestBean;
import io.takamaka.messages.chat.profile.ClearUserProfileSignedContentBean;
import io.takamaka.messages.chat.profile.EncryptedProfileBean;
import io.takamaka.messages.chat.profile.GetProfileDigestsRequestBean;
import io.takamaka.messages.chat.profile.GetProfileDigestsSignedContentBean;
import io.takamaka.messages.chat.profile.GetUserProfilePeerRequestBean;
import io.takamaka.messages.chat.profile.GetUserProfilePeerSignedContentBean;
import io.takamaka.messages.chat.profile.GetUserProfileRequestBean;
import io.takamaka.messages.chat.profile.GetUserProfileSignedContentBean;
import io.takamaka.messages.chat.profile.ProfileCardBean;
import io.takamaka.messages.chat.profile.ProfileConstants;
import io.takamaka.messages.chat.profile.ProfileGrantBean;
import io.takamaka.messages.chat.profile.PutProfileGrantsRequestBean;
import io.takamaka.messages.chat.profile.PutProfileGrantsSignedContentBean;
import io.takamaka.messages.chat.profile.SetUserProfileRequestBean;
import io.takamaka.messages.chat.profile.SetUserProfileSignedContentBean;
import io.takamaka.messages.chat.receipt.ReadReceiptRequestBean;
import io.takamaka.messages.chat.receipt.ReadReceiptSignedContentBean;
import io.takamaka.messages.chat.receipt.ReadReceiptSubscribeBean;
import io.takamaka.messages.chat.receipt.ReadReceiptSubscribeSignedContentBean;
import io.takamaka.messages.chat.typing.TypingSubscribeBean;
import io.takamaka.messages.chat.typing.TypingSubscribeSignedContentBean;
import io.takamaka.messages.chat.core.NonceResponseBean;
import io.takamaka.messages.chat.fcm.FcmTokenRegistrationRequestBean;
import io.takamaka.messages.chat.fcm.FcmTokenRegistrationSignedContentBean;
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
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.text.Normalizer;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
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
    /** VB-29: CSPRNG for every RandomStringGenerator in this class. commons-text falls back to
     *  ThreadLocalRandom when no provider is supplied — a 64-bit clock-seeded root shared by the whole
     *  JVM. See rschat-docs/security/PRNG_ENTROPY_AUDIT.md. */
    private static final java.security.SecureRandom TKM_CSPRNG = new java.security.SecureRandom();


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
                    .usingRandom(TKM_CSPRNG::nextInt)
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
     * Build a signed {@code DELETE_MESSAGE} ("delete for everyone") command
     * (DR-025). The signature covers {@code canonical(pl)}. The owner's client
     * supplies {@code targetEncryptedFileHashes} (the efh list read from the
     * decrypted message body) so the server can purge the attachment blobs it
     * cannot otherwise locate; pass {@code null}/empty for a text-only message.
     * {@code clientTimestamp} is advisory only — the server enforces the window
     * against its own clock and the server-assigned target timestamp.
     */
    public static final DeleteMessageRequestBean getSignedDeleteMessageRequest(
            final String conversationHashName,
            final String targetMessageSignature,
            final List<String> targetEncryptedFileHashes,
            final Long clientTimestamp,
            final EncMessageBean reason,
            final InstanceWalletKeystoreInterface signIwk,
            final int signIwkIndex
    ) throws CryptoMessageException {
        try {
            final DeleteMessageSignedContentBean pl = new DeleteMessageSignedContentBean(
                    conversationHashName,
                    targetMessageSignature,
                    targetEncryptedFileHashes,
                    clientTimestamp,
                    reason);
            final String canonicalJson = SimpleRequestHelper.getCanonicalJson(pl);
            final String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, signIwkIndex);
            return new DeleteMessageRequestBean(
                    pl,
                    signIwk.getPublicKeyAtIndexURL64(signIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.DELETE_MESSAGE.name(),
                    signIwk.getWalletCypher().name());
        } catch (MessageException | WalletException | JsonProcessingException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    /**
     * Encrypt a delete reason for transport inside {@code DeleteMessageSignedContentBean.reason}.
     *
     * <p>The reason is the ONE field of that bean the server has no use for — it is there so MEMBERS can see
     * why a message was removed. It must therefore travel encrypted like any other user-authored text; a
     * plaintext reason on a zero-knowledge relay breaches "E2E encryption is always on", and tends to
     * describe the very message being deleted.</p>
     *
     * <p>Scope {@code DELETE_MESSAGE} gives domain separation: a delete-reason ciphertext is not
     * interchangeable with a message body, so neither can be replayed as the other.</p>
     *
     * @param reason the plaintext reason; {@code null}/blank returns {@code null} so the field stays absent
     *               from {@code canonical(pl)} under {@code NON_EMPTY}
     * @param symmetricConversationKey the conversation key every member already holds
     */
    public static final EncMessageBean encryptDeleteReason(final String reason,
            final String symmetricConversationKey) throws CryptoMessageException {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        try {
            return TkmEncryptionUtils.toPasswordEncryptedContent(
                    symmetricConversationKey,
                    reason,
                    CHAT_MESSAGE_TYPES.DELETE_MESSAGE.name(),
                    EncryptionContext.v0_1_a.name());
        } catch (WalletException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    /**
     * Decrypt a delete reason. Returns {@code null} for an absent reason, so a caller can render "no reason
     * given" and an undecryptable one identically rather than surfacing crypto errors in a tombstone.
     *
     * @param reason the encrypted reason from a VERIFIED delete envelope
     * @param symmetricConversationKey the conversation key
     */
    public static final String decryptDeleteReason(final EncMessageBean reason,
            final String symmetricConversationKey) throws CryptoMessageException {
        if (reason == null) {
            return null;
        }
        try {
            return TkmEncryptionUtils.fromPasswordEncryptedContent(
                    symmetricConversationKey,
                    CHAT_MESSAGE_TYPES.DELETE_MESSAGE.name(),
                    reason);
        } catch (WalletException ex) {
            // InvalidCypherException extends WalletException — one catch covers a wrong key and a corrupt blob alike
            throw new CryptoMessageException(ex);
        }
    }

    /**
     * Build a signed {@code retrievedeletions} request (DR-025 catch-up). The signature covers
     * {@code canonical(pl)}; the server verifies it, checks conversation membership, and streams back the
     * owner-signed delete envelopes recorded after {@code since}.
     *
     * @param conversationHashName the conversation whose deletions to replay
     * @param since replay deletions with a SERVER delete-time strictly greater than this; {@code null} = all
     *              the server still holds (bounded by the deletion-log retention window)
     */
    public static final RetrieveDeletionsRequestBean getSignedRetrieveDeletionsRequest(
            final String conversationHashName,
            final Long since,
            final InstanceWalletKeystoreInterface signIwk,
            final int signIwkIndex
    ) throws CryptoMessageException {
        try {
            final RetrieveDeletionsSignedContentBean pl = new RetrieveDeletionsSignedContentBean(
                    conversationHashName, since, new Date().getTime());
            final String canonicalJson = SimpleRequestHelper.getCanonicalJson(pl);
            final String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, signIwkIndex);
            return new RetrieveDeletionsRequestBean(
                    pl,
                    signIwk.getPublicKeyAtIndexURL64(signIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.RETRIEVE_DELETIONS.name(),
                    signIwk.getWalletCypher().name());
        } catch (MessageException | WalletException | JsonProcessingException ex) {
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

    public static final FcmTokenRegistrationRequestBean getSignedFcmTokenRegistrationRequest(NonceResponseBean nonceResponseBean, String fcmToken, String platform, String deviceId, InstanceWalletKeystoreInterface signIwk, int sigIwkIndex) throws MessageException {
        try {
            FcmTokenRegistrationSignedContentBean fcmTokenRegistrationSignedContentBean = new FcmTokenRegistrationSignedContentBean(nonceResponseBean, fcmToken, platform, deviceId);
            String messageSignature = SimpleRequestHelper.signChatMessage(SimpleRequestHelper.getCanonicalJson(fcmTokenRegistrationSignedContentBean), signIwk, sigIwkIndex);
            FcmTokenRegistrationRequestBean fcmTokenRegistrationRequestBean = new FcmTokenRegistrationRequestBean(
                    fcmTokenRegistrationSignedContentBean,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    messageSignature,
                    CHAT_MESSAGE_TYPES.FCM_TOKEN_REGISTRATION.name(),
                    signIwk.getWalletCypher().name());
            return fcmTokenRegistrationRequestBean;
        } catch (JsonProcessingException | MessageException ex) {
            log.error("json error ", ex);
            throw new MessageException("json error ", ex);
        } catch (WalletException ex) {
            log.error("wallet error ", ex);
            throw new MessageException("wallet error ", ex);
        }
    }

    public static final TopicKeyDistributionItemBean getInviteForUser(RegisterUserRequestBean registerUserRequestBean, String topicSymmetricKey) throws CryptoMessageException {
        try {
            String encryptionPublicKey = registerUserRequestBean.getRegisterUserRequestSignedContentBean().getEncryptionPublicKey();
            String encryptionPublicKeyType = registerUserRequestBean.getRegisterUserRequestSignedContentBean().getEncryptionPublicKeyType();
            String providerEncoded = switch (encryptionPublicKeyType) {
                case "RSA_4096_ECB_OAEP_SHA256" -> TkmCypherProviderBCRSA4096ENC256.encrypt(encryptionPublicKey, topicSymmetricKey);
                default -> TkmCypherProviderBCRSA4096ENC.encrypt(encryptionPublicKey, topicSymmetricKey);
            };
            // THE ENCODING DECISION LIVES HERE, at the protocol layer — not inside the crypto provider.
            //
            // The providers return STANDARD base64 (BouncyCastle `Base64.toBase64String`). That was never a
            // protocol decision; it is simply what the library returns, and letting it reach the wire made
            // `enc_key` the lone standard-base64 field in an otherwise URL-safe envelope — sitting in the
            // same JSON object as `enc_key_hash`, which is URL-safe. A client that faithfully implemented
            // the documented convention produced conversations no Java client could open (F11).
            //
            // Re-encode to Base64URL with '.' padding: the same form as signatures, hashes and addresses,
            // and the form the field-tested wallet app already emits.
            //
            // ⚠️ Do NOT "simplify" this by changing the providers instead. TkmCypherProviderBCRSA4096ENC*
            // are published public API of wallet-core and also feed CombinedRSAAESBean in takamaka-extra,
            // which is serialisable and reachable by consumers outside this estate. Converting here keeps
            // the blast radius to the one field that has a protocol contract.
            //
            // Readers must accept BOTH forms permanently (TkmSignUtils.fromAnyB64ToByteArray): existing
            // conversations carry the standard form inside a signed, permanently-stored envelope and can
            // never be re-encoded. See rschat-docs/security/BASE64_ENCODING_CONTRACT.md §0.1.
            String encryptedKey = TkmSignUtils.fromByteArrayToB64URL(
                    TkmSignUtils.fromAnyB64ToByteArray(providerEncoded));
            TopicKeyDistributionItemBean invite = new TopicKeyDistributionItemBean(
                    TkmSignUtils.Hash256B64URL(encryptionPublicKey),
                    encryptedKey);
            return invite;
        } catch (HashEncodeException | HashAlgorithmNotFoundException | HashProviderNotFoundException | WalletException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    // ===== User profile channel (USER_PROFILE_DESIGN.md, registry, DR-032) =====

    /**
     * Generate a profile key: {@link ProfileConstants#PROFILE_KEY_BYTES} CSPRNG
     * bytes, returned as Base64URL text.
     *
     * <p>Text rather than {@code byte[]} because that text is what gets
     * RSA-wrapped per grantee by {@link #getInviteForUser} — the same primitive
     * conversation keys use, unchanged (design D3).</p>
     *
     * <p>Note this does NOT go through {@link #generateRandomSafeKey}. That
     * generator produces an alphanumeric PASSPHRASE for the estate's
     * PBKDF2-then-AES path; a profile blob is sealed with the raw key directly
     * (there is no KDF and no salt on the wire — see
     * {@link #sealProfileCard}), so the key must BE 32 bytes of entropy, not a
     * string that gets stretched into one.</p>
     *
     * @return a fresh profile key, Base64URL
     */
    public static final String generateProfileKey() {
        byte[] key = new byte[ProfileConstants.PROFILE_KEY_BYTES];
        TKM_CSPRNG.nextBytes(key);
        return TkmSignUtils.fromByteArrayToB64URL(key);
    }

    /**
     * SHA3-256 of the DECODED blob bytes, lowercase hex — the value that goes
     * in {@code blob_hash} and in {@code known_blob_hash}.
     *
     * <p><b>Over BYTES, deliberately, and not via any {@code TkmSignUtils.Hash*}
     * helper.</b> Those take a {@code String} and hash its UTF-8; the
     * {@code Hash*byte} family returns the ASCII of Base64URL TEXT, not a raw
     * digest, and hex-encoding that yields the hex of a string (C51). Hashing
     * the base64 text instead of the bytes is precisely the defect DR-030
     * records — and DR-031 records the check that then failed to enforce it,
     * with four passing tests over a check that did nothing.</p>
     *
     * @param blobBytes the decoded ciphertext bytes ({@code IV || ct || tag})
     * @return lowercase hex of the SHA3-256 digest
     * @throws CryptoMessageException if SHA3-256 is unavailable
     */
    public static final String profileBlobHash(byte[] blobBytes) throws CryptoMessageException {
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            MessageDigest digest = MessageDigest.getInstance("SHA3-256", BouncyCastleProvider.PROVIDER_NAME);
            byte[] out = digest.digest(blobBytes);
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | NoSuchProviderException ex) {
            throw new CryptoMessageException("SHA3-256 unavailable for the profile blob hash", ex);
        }
    }

    /**
     * Enforce the registry §4 card caps at the PRODUCER, before anything is
     * encrypted.
     *
     * <p>This is the only place they can be enforced at all: once the card is
     * sealed the server sees ciphertext and can check nothing inside it (design
     * D8). A client that leaves this to the server's {@code ERR_TOO_LARGE} is
     * non-conformant — that error bounds the whole blob, not a name.</p>
     *
     * <p>Lengths are counted in Unicode CODE POINTS after NFC normalisation.
     * Counting UTF-16 units truncates emoji and non-BMP scripts one glyph
     * early, and two clients that disagree on the rule disagree on whether the
     * same card is conformant.</p>
     *
     * @param card the plaintext card
     * @throws CryptoMessageException if any registry §4 cap is exceeded, or the
     * payload version is not the one this build writes
     */
    public static final void validateProfileCard(ProfileCardBean card) throws CryptoMessageException {
        if (card == null) {
            throw new CryptoMessageException("profile card must not be null");
        }
        if (!ProfileConstants.PAYLOAD_VERSION_1_0.equals(card.getPayloadVersion())) {
            throw new CryptoMessageException("unsupported profile payload_version: " + card.getPayloadVersion());
        }
        int nameChars = nfcCodePointCount(card.getDisplayName());
        if (nameChars > ProfileConstants.MAX_DISPLAY_NAME_CHARS) {
            throw new CryptoMessageException("display_name is " + nameChars + " NFC characters, cap is "
                    + ProfileConstants.MAX_DISPLAY_NAME_CHARS);
        }
        int statusChars = nfcCodePointCount(card.getStatusMessage());
        if (statusChars > ProfileConstants.MAX_STATUS_MESSAGE_CHARS) {
            throw new CryptoMessageException("status_message is " + statusChars + " NFC characters, cap is "
                    + ProfileConstants.MAX_STATUS_MESSAGE_CHARS);
        }
        if (card.getAvatarMediaType() != null
                && card.getAvatarMediaType().length() > ProfileConstants.MAX_AVATAR_MEDIA_TYPE_CHARS) {
            throw new CryptoMessageException("avatar_media_type exceeds "
                    + ProfileConstants.MAX_AVATAR_MEDIA_TYPE_CHARS + " characters");
        }
        if (card.getAvatar() != null) {
            final int avatarBytes;
            try {
                avatarBytes = TkmSignUtils.fromAnyB64ToByteArray(card.getAvatar()).length;
            } catch (RuntimeException ex) {
                throw new CryptoMessageException("avatar is not decodable base64", ex);
            }
            // Bytes, never pixels. MAX_THUMBNAIL_DIMENSION_PX was removed on 2026-08-12 because
            // validating a pixel limit means decoding untrusted image data — a decompression-bomb
            // surface accepted for a cosmetic constraint. Registry §4.5.4; not reopened here.
            if (avatarBytes > ProfileConstants.MAX_AVATAR_BYTES) {
                throw new CryptoMessageException("avatar is " + avatarBytes + " decoded bytes, cap is "
                        + ProfileConstants.MAX_AVATAR_BYTES);
            }
        }
    }

    private static int nfcCodePointCount(String value) {
        if (value == null) {
            return 0;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        return normalized.codePointCount(0, normalized.length());
    }

    /**
     * Seal a profile card: AES-256-GCM under the profile key, emitted as
     * {@code base64url(IV || ciphertext || tag)} — registry §4.1, design D2/D7.
     *
     * <p>The caps in {@link #validateProfileCard} are enforced first, and the
     * resulting blob is checked against
     * {@link ProfileConstants#MAX_BLOB_B64_CHARS}. A client that has fetched
     * {@code serverinfo} should clamp to the manifest's value instead of the
     * compiled-in one (DR-022/DR-023); this is the floor, not the authority.</p>
     *
     * <p><b>Why a raw key and not the estate's {@code toPasswordEncryptedContent}
     * path.</b> That path is PBKDF2-then-AES and serialises an
     * {@code EncMessageBean} carrying its own KDF parameters. The profile
     * envelope has no KDF fields by design — it is one opaque string plus a
     * named cipher — so the key here is used directly. Same primitive family,
     * different envelope, and the difference is on the wire where a reader can
     * see it.</p>
     *
     * @param card the plaintext card
     * @param profileKey the profile key, Base64URL (see
     * {@link #generateProfileKey})
     * @param keyEpoch the key generation this blob belongs to — the
     * {@code nonce_issue_time} of the write that created the key
     * @return the sealed envelope, ready for {@code setuserprofile}
     * @throws CryptoMessageException on a cap violation or any crypto failure
     */
    public static final EncryptedProfileBean sealProfileCard(ProfileCardBean card, String profileKey, long keyEpoch) throws CryptoMessageException {
        validateProfileCard(card);
        try {
            final byte[] key = TkmSignUtils.fromAnyB64ToByteArray(profileKey);
            if (key.length != ProfileConstants.PROFILE_KEY_BYTES) {
                throw new CryptoMessageException("profile key must be " + ProfileConstants.PROFILE_KEY_BYTES
                        + " bytes, was " + key.length);
            }
            final byte[] plaintext = SimpleRequestHelper.getCanonicalJson(card).getBytes(StandardCharsets.UTF_8);
            final byte[] iv = new byte[ProfileConstants.GCM_IV_BYTES];
            TKM_CSPRNG.nextBytes(iv);

            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(ProfileConstants.GCM_TAG_BITS, iv));
            final byte[] sealed = cipher.doFinal(plaintext);

            final byte[] blobBytes = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, blobBytes, 0, iv.length);
            System.arraycopy(sealed, 0, blobBytes, iv.length, sealed.length);

            final String blob = TkmSignUtils.fromByteArrayToB64URL(blobBytes);
            if (blob.length() > ProfileConstants.MAX_BLOB_B64_CHARS) {
                throw new CryptoMessageException("sealed profile blob is " + blob.length()
                        + " base64 characters, cap is " + ProfileConstants.MAX_BLOB_B64_CHARS);
            }
            return new EncryptedProfileBean(
                    keyEpoch,
                    ProfileConstants.BLOB_VERSION_1_0,
                    ProfileConstants.CIPHER_AES_256_GCM,
                    blob,
                    profileBlobHash(blobBytes));
        } catch (JsonProcessingException | GeneralSecurityException ex) {
            throw new CryptoMessageException("unable to seal the profile card", ex);
        }
    }

    /**
     * Open a sealed profile card — the inverse of {@link #sealProfileCard}.
     *
     * <p>GCM authenticates: a tampered blob fails here rather than yielding
     * plausible-looking garbage. {@code blob_hash} is verified too, because a
     * server that swapped a blob for a DIFFERENT valid one under the same key
     * would otherwise pass — the hash is what ties the body to the digest a
     * client cached and to the tickle it acted on.</p>
     *
     * <p>The returned card is UNTRUSTED input from a peer. Sanitise before
     * rendering, decode the avatar by magic bytes, and never let
     * {@code display_name} displace the public key (registry §4.5).</p>
     *
     * @param encryptedProfile the sealed envelope as received
     * @param profileKey the profile key, Base64URL
     * @return the plaintext card
     * @throws ChatMessageException if the envelope is malformed, the hash does
     * not match the bytes, or authentication fails
     */
    public static final ProfileCardBean openProfileCard(EncryptedProfileBean encryptedProfile, String profileKey) throws ChatMessageException {
        try {
            if (encryptedProfile == null || encryptedProfile.getBlob() == null) {
                throw new ChatMessageException("no profile blob to open");
            }
            if (!ProfileConstants.CIPHER_AES_256_GCM.equals(encryptedProfile.getCipher())) {
                throw new ChatMessageException("unsupported profile cipher: " + encryptedProfile.getCipher());
            }
            if (!ProfileConstants.BLOB_VERSION_1_0.equals(encryptedProfile.getBlobVersion())) {
                throw new ChatMessageException("unsupported profile blob_version: " + encryptedProfile.getBlobVersion());
            }
            // Both base64 alphabets, permanently — the F11 read contract
            // (BASE64_ENCODING_CONTRACT.md §0.1), applied at the crypto boundary.
            final byte[] blobBytes = TkmSignUtils.fromAnyB64ToByteArray(encryptedProfile.getBlob());
            if (blobBytes.length <= ProfileConstants.GCM_IV_BYTES) {
                throw new ChatMessageException("profile blob is too short to carry an IV and a tag");
            }
            final String actualHash = profileBlobHash(blobBytes);
            if (encryptedProfile.getBlobHash() != null && !actualHash.equals(encryptedProfile.getBlobHash())) {
                throw new ChatMessageException("profile blob_hash does not match the blob bytes");
            }

            final byte[] key = TkmSignUtils.fromAnyB64ToByteArray(profileKey);
            if (key.length != ProfileConstants.PROFILE_KEY_BYTES) {
                throw new ChatMessageException("profile key must be " + ProfileConstants.PROFILE_KEY_BYTES
                        + " bytes, was " + key.length);
            }
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(ProfileConstants.GCM_TAG_BITS, blobBytes, 0, ProfileConstants.GCM_IV_BYTES));
            final byte[] plaintext = cipher.doFinal(blobBytes,
                    ProfileConstants.GCM_IV_BYTES, blobBytes.length - ProfileConstants.GCM_IV_BYTES);

            return TkmTextUtils.getJacksonMapper()
                    .readValue(new String(plaintext, StandardCharsets.UTF_8), ProfileCardBean.class);
        } catch (CryptoMessageException | GeneralSecurityException | JsonProcessingException ex) {
            throw new ChatMessageException("unable to open the profile card", ex);
        }
    }

    /**
     * Wrap the profile key to one grantee's registered encryption key — design
     * D3, registry §4.2.
     *
     * <p>This DELEGATES to {@link #getInviteForUser}: a profile-key grant is
     * byte-identical in shape to a conversation-key invite, so it is produced by
     * the same code. Reimplementing the RSA wrap here would fork the one place
     * in the estate that owns {@code enc_key}'s encoding, and would inherit the
     * F11 bug instead of the F11 fix.</p>
     *
     * @param grantee the grantee's registration envelope (supplies the identity
     * key and the RSA encryption key)
     * @param profileKey the profile key, Base64URL
     * @param keyEpoch the epoch this grant unwraps
     * @return the grant, ready for {@code setuserprofile} or
     * {@code putprofilegrants}
     * @throws CryptoMessageException if the wrap fails
     */
    public static final ProfileGrantBean getProfileGrantForUser(RegisterUserRequestBean grantee, String profileKey, long keyEpoch) throws CryptoMessageException {
        final TopicKeyDistributionItemBean wrapped = getInviteForUser(grantee, profileKey);
        return new ProfileGrantBean(
                grantee.getFrom(),
                keyEpoch,
                wrapped.getEncryptionKeyHash(),
                wrapped.getEncryptedTopicKey());
    }

    /**
     * Build a signed {@code setuserprofile} request. The signature covers
     * {@code canonical(pl)}.
     *
     * <p>Concurrent nonce-bearing writes from one client race and lose to each
     * other under LWW: serialise them through the ordered-write helper the
     * options channel already uses (design §8, handoff §5).</p>
     */
    public static final SetUserProfileRequestBean getSignedSetUserProfileRequest(
            final NonceResponseBean nonce,
            final EncryptedProfileBean profile,
            final List<ProfileGrantBean> grants,
            final Long clientTimestamp,
            final InstanceWalletKeystoreInterface signIwk,
            final int sigIwkIndex
    ) throws CryptoMessageException {
        try {
            final SetUserProfileSignedContentBean pl = new SetUserProfileSignedContentBean(
                    nonce, profile, grants, clientTimestamp);
            final String canonicalJson = SimpleRequestHelper.getCanonicalJson(pl);
            final String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, sigIwkIndex);
            return new SetUserProfileRequestBean(
                    pl,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.SET_USER_PROFILE.name(),
                    signIwk.getWalletCypher().name());
        } catch (WalletException | MessageException | JsonProcessingException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    /**
     * Build a signed {@code putprofilegrants} request: republish grants for an
     * existing epoch without rewriting the blob.
     */
    public static final PutProfileGrantsRequestBean getSignedPutProfileGrantsRequest(
            final NonceResponseBean nonce,
            final long keyEpoch,
            final List<ProfileGrantBean> grants,
            final Long clientTimestamp,
            final InstanceWalletKeystoreInterface signIwk,
            final int sigIwkIndex
    ) throws CryptoMessageException {
        try {
            final PutProfileGrantsSignedContentBean pl = new PutProfileGrantsSignedContentBean(
                    nonce, keyEpoch, grants, clientTimestamp);
            final String canonicalJson = SimpleRequestHelper.getCanonicalJson(pl);
            final String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, sigIwkIndex);
            return new PutProfileGrantsRequestBean(
                    pl,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.PUT_PROFILE_GRANTS.name(),
                    signIwk.getWalletCypher().name());
        } catch (WalletException | MessageException | JsonProcessingException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    /**
     * Build a signed {@code clearuserprofile} request. The nonce's issue time
     * becomes the tombstone's watermark (design D6).
     */
    public static final ClearUserProfileRequestBean getSignedClearUserProfileRequest(
            final NonceResponseBean nonce,
            final Long clientTimestamp,
            final InstanceWalletKeystoreInterface signIwk,
            final int sigIwkIndex
    ) throws CryptoMessageException {
        try {
            final ClearUserProfileSignedContentBean pl = new ClearUserProfileSignedContentBean(nonce, clientTimestamp);
            final String canonicalJson = SimpleRequestHelper.getCanonicalJson(pl);
            final String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, sigIwkIndex);
            return new ClearUserProfileRequestBean(
                    pl,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.CLEAR_USER_PROFILE.name(),
                    signIwk.getWalletCypher().name());
        } catch (WalletException | MessageException | JsonProcessingException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    /**
     * Build a signed {@code getuserprofile} self-read request (no nonce).
     */
    public static final GetUserProfileRequestBean getSignedGetUserProfileRequest(
            final Long clientTimestamp,
            final InstanceWalletKeystoreInterface signIwk,
            final int sigIwkIndex
    ) throws CryptoMessageException {
        try {
            final GetUserProfileSignedContentBean pl = new GetUserProfileSignedContentBean(clientTimestamp);
            final String canonicalJson = SimpleRequestHelper.getCanonicalJson(pl);
            final String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, sigIwkIndex);
            return new GetUserProfileRequestBean(
                    pl,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.GET_USER_PROFILE.name(),
                    signIwk.getWalletCypher().name());
        } catch (WalletException | MessageException | JsonProcessingException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    /**
     * Build a signed {@code getuserprofilepeer} request (no nonce).
     *
     * @param knownBlobHash the caller's cached {@code blob_hash}, or
     * {@code null} for an unconditional read. Passing it is a conformance
     * requirement wherever a cache exists (design D11).
     */
    public static final GetUserProfilePeerRequestBean getSignedGetUserProfilePeerRequest(
            final String targetPublicKey,
            final String knownBlobHash,
            final Long clientTimestamp,
            final InstanceWalletKeystoreInterface signIwk,
            final int sigIwkIndex
    ) throws CryptoMessageException {
        try {
            final GetUserProfilePeerSignedContentBean pl = new GetUserProfilePeerSignedContentBean(
                    targetPublicKey, knownBlobHash, clientTimestamp);
            final String canonicalJson = SimpleRequestHelper.getCanonicalJson(pl);
            final String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, sigIwkIndex);
            return new GetUserProfilePeerRequestBean(
                    pl,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.GET_USER_PROFILE_PEER.name(),
                    signIwk.getWalletCypher().name());
        } catch (WalletException | MessageException | JsonProcessingException ex) {
            throw new CryptoMessageException(ex);
        }
    }

    /**
     * Build a signed {@code getprofiledigests} batch request (no nonce), at most
     * {@link ProfileConstants#MAX_DIGEST_BATCH} targets.
     */
    public static final GetProfileDigestsRequestBean getSignedGetProfileDigestsRequest(
            final List<String> targetPublicKeys,
            final Long clientTimestamp,
            final InstanceWalletKeystoreInterface signIwk,
            final int sigIwkIndex
    ) throws CryptoMessageException {
        if (targetPublicKeys != null && targetPublicKeys.size() > ProfileConstants.MAX_DIGEST_BATCH) {
            // Refuse at the producer rather than let the server reject the batch: a client that
            // silently truncated instead would read the missing targets as "no profile".
            throw new CryptoMessageException("digest batch is " + targetPublicKeys.size()
                    + " targets, cap is " + ProfileConstants.MAX_DIGEST_BATCH);
        }
        try {
            final GetProfileDigestsSignedContentBean pl = new GetProfileDigestsSignedContentBean(
                    targetPublicKeys, clientTimestamp);
            final String canonicalJson = SimpleRequestHelper.getCanonicalJson(pl);
            final String signature = SimpleRequestHelper.signChatMessage(canonicalJson, signIwk, sigIwkIndex);
            return new GetProfileDigestsRequestBean(
                    pl,
                    signIwk.getPublicKeyAtIndexURL64(sigIwkIndex),
                    signature,
                    CHAT_MESSAGE_TYPES.GET_PROFILE_DIGESTS.name(),
                    signIwk.getWalletCypher().name());
        } catch (WalletException | MessageException | JsonProcessingException ex) {
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
                case "SET_USER_PROFILE" -> {
                    final SetUserProfileRequestBean setUserProfileRequestBean = ChatUtils.fromJsonToSetUserProfileRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(setUserProfileRequestBean.getPl());
                    returnObj = setUserProfileRequestBean;
                }
                case "PUT_PROFILE_GRANTS" -> {
                    final PutProfileGrantsRequestBean putProfileGrantsRequestBean = ChatUtils.fromJsonToPutProfileGrantsRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(putProfileGrantsRequestBean.getPl());
                    returnObj = putProfileGrantsRequestBean;
                }
                case "CLEAR_USER_PROFILE" -> {
                    final ClearUserProfileRequestBean clearUserProfileRequestBean = ChatUtils.fromJsonToClearUserProfileRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(clearUserProfileRequestBean.getPl());
                    returnObj = clearUserProfileRequestBean;
                }
                case "GET_USER_PROFILE" -> {
                    final GetUserProfileRequestBean getUserProfileRequestBean = ChatUtils.fromJsonToGetUserProfileRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(getUserProfileRequestBean.getPl());
                    returnObj = getUserProfileRequestBean;
                }
                case "GET_USER_PROFILE_PEER" -> {
                    final GetUserProfilePeerRequestBean getUserProfilePeerRequestBean = ChatUtils.fromJsonToGetUserProfilePeerRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(getUserProfilePeerRequestBean.getPl());
                    returnObj = getUserProfilePeerRequestBean;
                }
                case "GET_PROFILE_DIGESTS" -> {
                    final GetProfileDigestsRequestBean getProfileDigestsRequestBean = ChatUtils.fromJsonToGetProfileDigestsRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(getProfileDigestsRequestBean.getPl());
                    returnObj = getProfileDigestsRequestBean;
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
                case "DELETE_MESSAGE" -> {
                    final DeleteMessageRequestBean deleteMessageRequestBean = ChatUtils.fromJsonToDeleteMessageRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(deleteMessageRequestBean.getPl());
                    returnObj = deleteMessageRequestBean;
                }
                case "RETRIEVE_DELETIONS" -> {
                    final RetrieveDeletionsRequestBean retrieveDeletionsRequestBean = ChatUtils.fromJsonToRetrieveDeletionsRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(retrieveDeletionsRequestBean.getPl());
                    returnObj = retrieveDeletionsRequestBean;
                }
                case "FCM_TOKEN_REGISTRATION" -> {
                    final FcmTokenRegistrationRequestBean fromJsonToFcmTokenRegistrationRequestBean = ChatUtils.fromJsonToFcmTokenRegistrationRequestBean(messageJson);
                    jsonCanonical = SimpleRequestHelper.getCanonicalJson(fromJsonToFcmTokenRegistrationRequestBean.getFcmTokenRegistrationSignedContentBean());
                    returnObj = fromJsonToFcmTokenRegistrationRequestBean;
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


    /**
     * Enforce the inline byte rule on the way out — for BOTH kinds of payload
     * carried in {@code preview}.
     *
     * <p>Producers MUST NOT emit a placeholder whose DECODED {@code preview}
     * exceeds {@link InlineContentLimits#MAX_INLINE_BYTES}. Oversized content
     * belongs on the regular attachment path, where it is uploaded once and
     * fetched on demand — a {@code preview} is copied into every encrypted
     * message body, every notification fan-out and every history fetch.
     *
     * <p>⚠️ <strong>This used to skip {@code isTheObject == false} entirely</strong>
     * (§PREVIEW-CONFORMANCE W1), so a BLOB's generated thumbnail was bounded by
     * nothing, anywhere: measured previews of ~110 000 base64 chars — 1.9x the
     * file they previewed. The field is the same field and the bloat is the same
     * bloat, so the byte rule applies to both; what differs is only what an
     * ABSENT preview means:
     *
     * <ul>
     *   <li>{@code isTheObject == true} — the preview IS the content. Absent or
     *       undecodable means nothing to deliver: refuse.</li>
     *   <li>{@code isTheObject == false} — the preview is an optional generated
     *       thumbnail of a separately-transferred blob. Absent is legal and
     *       common (non-image blobs, or a thumbnail that did not fit); present
     *       but undecodable is a producer bug: refuse.</li>
     * </ul>
     *
     * @throws ChatMessageException naming the offending file and its actual size
     */
    private static void rejectOversizedInlineContent(BasicMessageEncryptedContentBean content)
            throws ChatMessageException {
        if (content == null || content.getAttachedMedia() == null) {
            return;
        }
        rejectOversizedMediaList(content.getAttachedMedia());
        long totalPreviewBytes = 0L;
        for (ChatMediaPlaceholderBean media : content.getAttachedMedia()) {
            if (media == null) {
                continue;
            }
            final boolean isInline = Boolean.TRUE.equals(media.getIsTheObject());
            final String preview = media.getPreview();
            if (!isInline && (preview == null || preview.isEmpty())) {
                continue; // a blob need not carry a preview at all
            }
            final String what = isInline ? "inline content" : "preview";
            InlineContentLimits.InlineVerdict verdict
                    = InlineContentLimits.checkInlinePayload(preview);
            if (verdict == InlineContentLimits.InlineVerdict.TOO_LARGE) {
                int actual = InlineContentLimits.decodedLengthOrMinusOne(preview);
                throw new ChatMessageException(
                        what + " too large: '" + media.getFileName() + "' is "
                        + (actual < 0 ? "over" : actual + " bytes, over") + " the "
                        + InlineContentLimits.MAX_INLINE_BYTES + "-byte limit. "
                        + (isInline
                                ? "Send it as a regular attachment instead."
                                : "Generate a smaller thumbnail, or send no preview at all."));
            }
            if (verdict == InlineContentLimits.InlineVerdict.UNDECODABLE) {
                throw new ChatMessageException(
                        what + " for '" + media.getFileName()
                        + "' is missing or not valid base64: nothing to deliver.");
            }
            totalPreviewBytes += InlineContentLimits.decodedLengthOrMinusOne(preview);
        }
        if (totalPreviewBytes > InlineContentLimits.MAX_TOTAL_PREVIEW_BYTES) {
            throw new ChatMessageException(
                    "attached_media carries " + totalPreviewBytes
                    + " bytes of preview/inline payload in total, over the "
                    + InlineContentLimits.MAX_TOTAL_PREVIEW_BYTES + "-byte aggregate limit. "
                    + "A per-placeholder limit bounds nothing when the list is unbounded.");
        }
    }

    /**
     * Bound the LENGTH of {@code attached_media}. A per-placeholder byte limit is
     * an unbounded total when the list itself is unbounded — see
     * {@link InlineContentLimits#MAX_ATTACHED_MEDIA}.
     */
    private static void rejectOversizedMediaList(List<ChatMediaPlaceholderBean> media)
            throws ChatMessageException {
        if (media.size() > InlineContentLimits.MAX_ATTACHED_MEDIA) {
            throw new ChatMessageException(
                    "attached_media carries " + media.size() + " placeholders, over the "
                    + InlineContentLimits.MAX_ATTACHED_MEDIA + " limit. "
                    + "Split them across several messages.");
        }
    }

    public static final BasicMessageRequestBean getBasicMessageBean(InstanceWalletKeystoreInterface iwkSign, int index, String conversationHashName, String conversationEncryptionKey, List<String> citedUsers, BasicMessageEncryptedContentBean basicMessageEncryptedContentBean) throws ChatMessageException {
        try {
            // ⭐ PRODUCER GUARD (normative, 2026-08-12): an inline payload over
            // InlineContentLimits.MAX_INLINE_BYTES MUST NOT be sent.
            //
            // Placed here, at the single choke point every Java producer passes
            // through — plain messages, attachments, reactions, forwards — rather
            // than in a bean factory, because ChatMediaPlaceholderBean has a public
            // Lombok builder and a constructor-level check would be bypassable.
            //
            // One-directional by ruling: sending a SMALL file through the regular
            // blob path is NOT a violation, so nothing is ever forced inline. That
            // is what lets forward / share-history pass a placeholder through
            // untouched — re-inlining would mean re-encoding and would change its
            // efh/uch identity.
            rejectOversizedInlineContent(basicMessageEncryptedContentBean);

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
        // N-13: the size check STAYS here, but it no longer speaks worse than the check it pre-empts.
        //
        // The finding was that this check wins by ordering — it runs seven lines before buildAndSign
        // reaches rejectOversizedInlineContent — and said only "inline reaction payload exceeds
        // 51200 bytes": no file, no actual size, no remedy. The first fix deleted it so the better
        // message would surface. That was WRONG, and chat-web-gui's ReactionActionProjectionTest
        // caught it: the choke point throws ChatMessageException, which is a DIFFERENT exception
        // branch (ChatCryptoConstructionException vs MessageException), so deleting this threw away
        // the specific type that chat-web-gui documents as the contract for "wrong MIME / too large"
        // (ReactionInput:13, MessageService:304, MessageActionsController:170,260).
        //
        // So: keep the type, fix the words. Both sites now name the file, the real byte count and
        // what to do instead, and both read MAX_INLINE_BYTES from InlineContentLimits, so the two
        // enforcement points cannot drift apart on the number OR on the wording.
        if (decoded.length > InlineContentLimits.MAX_INLINE_BYTES) {
            final String what = payload.getFileName() != null ? payload.getFileName() : payload.getMediaType();
            throw new InlineContentViolationException(
                    ChatCryptoConstructionException.INLINE_CONTENT_TOO_LARGE,
                    "inline content too large: '" + what + "' is " + decoded.length
                    + " bytes, over the " + InlineContentLimits.MAX_INLINE_BYTES
                    + "-byte inline limit. Send it as a regular attachment instead.");
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
