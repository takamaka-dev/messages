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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.takamaka.extra.beans.EncMessageBean;
import io.takamaka.extra.utils.TkmEncryptionUtils;
import io.takamaka.messages.beans.BaseBean;
import io.takamaka.messages.chat.message.BasicMessageEncryptedContentBean;
import io.takamaka.messages.chat.message.BasicMessageSignedContentBean;
import io.takamaka.messages.chat.conversation.ConversationNameHashBean;
import io.takamaka.messages.chat.core.SignedContentTopicBean;
import io.takamaka.messages.chat.core.SignedMessageBean;
import io.takamaka.messages.chat.conversation.TopicKeyDistributionItemBean;
import io.takamaka.messages.chat.conversation.TopicTitleKeyBean;
import io.takamaka.messages.chat.attachment.UploadRequestBean;
import io.takamaka.messages.chat.message.BasicMessageRequestBean;
import io.takamaka.messages.chat.conversation.CreateConversationRequestBean;
import io.takamaka.messages.chat.user.RegisterUserRequestBean;
import io.takamaka.messages.chat.user.RegisterUserRequestSignedContentBean;
import io.takamaka.messages.chat.user.RequestUserKeyRequestBean;
import io.takamaka.messages.chat.user.RequestUserKeyRequestSignedContentBean;
import io.takamaka.messages.chat.conversation.RetrieveAllConversationsRequestBean;
import io.takamaka.messages.chat.conversation.RetrieveConversationRequestBean;
import io.takamaka.messages.chat.conversation.RetrieveConversationRequestContentBean;
import io.takamaka.messages.chat.message.RetrieveMessageRequestBean;
import io.takamaka.messages.chat.attachment.SignedDownloadRequestBean;
import io.takamaka.messages.chat.core.SignedTimestampRequestBean;
import io.takamaka.messages.chat.attachment.SignedUploadRequestBean;
import io.takamaka.messages.chat.notification.UserNotificationRequestBean;
import io.takamaka.messages.chat.options.GetUserOptionPeerRequestBean;
import io.takamaka.messages.chat.options.GetUserOptionsRequestBean;
import io.takamaka.messages.chat.options.ResetUserOptionsRequestBean;
import io.takamaka.messages.chat.options.SetUserOptionRequestBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.exception.InvalidChatMessageSignatureException;
import io.takamaka.messages.exception.InvalidParameterException;
import io.takamaka.messages.exception.MessageException;
import io.takamaka.messages.exception.UnsupportedChatMessageTypeException;
import io.takamaka.messages.exception.UnsupportedSignatureCypherException;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.TkmCypherProviderBCED25519;
import io.takamaka.wallet.beans.TkmCypherBean;
import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.KeyContexts;
import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class ChatUtils {

    public static final String CONVERSATION_HASH_NAME_PARAM_STRING = "^[0-9a-fA-F]{64}$";
    public static final Pattern CONVERSATION_HASH_NAME_PARAM_PATTERN = Pattern.compile(CONVERSATION_HASH_NAME_PARAM_STRING);
    public static final String CONVERSATION_SIGNATURE_PARAM_STRING = "^[0-9a-zA-Z\\-_]{86}\\.\\.$";
    public static final Pattern CONVERSATION_SIGNATURE_PARAM_PATTERN = Pattern.compile(CONVERSATION_SIGNATURE_PARAM_STRING);

    public static final String parseSafeSIgnatureName(String conversationSignature) throws InvalidParameterException {
        if (CONVERSATION_SIGNATURE_PARAM_PATTERN.matcher(conversationSignature).matches()) {
            return conversationSignature;
        }
        throw new InvalidParameterException("invalid conversation hash name");
    }

    public static final String parseSafeConversationHashName(String conversationHashName) throws InvalidParameterException {
        if (CONVERSATION_HASH_NAME_PARAM_PATTERN.matcher(conversationHashName).matches()) {
            return conversationHashName;
        }
        throw new InvalidParameterException("invalid conversation hash name");
    }

    public static final TypeReference<List<RequestUserKeyRequestSignedContentBean>> type_ListRequestUserKeyRequestBeanSignedContent = new TypeReference<>() {
    };

    public static final TypeReference<Map<String, TopicKeyDistributionItemBean>> type_MapTopicKeyDistributionItemBean = new TypeReference<>() {
    };

    /**
     * pretty printer for visualization
     *
     * @param baseBean
     * @return
     * @throws JsonProcessingException
     */
    public static final String getObjectJsonPretty(Object baseBean) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(baseBean);
    }

    public static final RegisterUserRequestBean fromJsonToRegisterUserRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, RegisterUserRequestBean.class);
    }

    public static final List<RequestUserKeyRequestSignedContentBean> fromJsonToListRequestUserKeyRequestBeanSignedContent(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, type_ListRequestUserKeyRequestBeanSignedContent);
    }

    public static final Map<String, TopicKeyDistributionItemBean> fromJsonToMapTopicKeyDistributionItemBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, type_MapTopicKeyDistributionItemBean);
    }

    public static final RequestUserKeyRequestBean fromJsonToRequestUserKeyRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, RequestUserKeyRequestBean.class);
    }

    public static final CreateConversationRequestBean fromJsonToCreateConversationRequest(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, CreateConversationRequestBean.class);
    }

    public static final BasicMessageRequestBean fromJsonToBasicMessageBeanRequest(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, BasicMessageRequestBean.class);
    }

    public static final BasicMessageRequestBean fromJsonToBasicMessageBeanRequest(String jsonMessage, int maxChar) throws JsonProcessingException {
        JsonFactory streamReadConstraints
                = TkmTextUtils
                        .getJacksonMapper()
                        .getFactory()
                        .setStreamReadConstraints(StreamReadConstraints.builder().maxStringLength(maxChar).build());
        return JsonMapper.builder(streamReadConstraints).build().readValue(jsonMessage, BasicMessageRequestBean.class);
    }

    public static final UserNotificationRequestBean fromJsonToUserNotificationRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, UserNotificationRequestBean.class);
    }

    public static final RetrieveAllConversationsRequestBean fromJsonToRetrieveAllConversationsRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, RetrieveAllConversationsRequestBean.class);
    }

    public static final RetrieveMessageRequestBean fromJsonToRetrieveMessageRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, RetrieveMessageRequestBean.class);
    }

    public static final SignedTimestampRequestBean fromJsonToTimestampSignedRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, SignedTimestampRequestBean.class);
    }

    public static final SignedUploadRequestBean fromJsonToSignedUploadRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, SignedUploadRequestBean.class);
    }

    public static final UploadRequestBean fromJsonToUploadRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, UploadRequestBean.class);
    }

    public static final SignedDownloadRequestBean fromJsonToSignedDownloadRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, SignedDownloadRequestBean.class);
    }

    public static final RetrieveConversationRequestBean fromJsonToRetrieveConversationRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, RetrieveConversationRequestBean.class);
    }

    public static final SetUserOptionRequestBean fromJsonToSetUserOptionRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, SetUserOptionRequestBean.class);
    }

    public static final ResetUserOptionsRequestBean fromJsonToResetUserOptionsRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, ResetUserOptionsRequestBean.class);
    }

    public static final GetUserOptionsRequestBean fromJsonToGetUserOptionsRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, GetUserOptionsRequestBean.class);
    }

    public static final GetUserOptionPeerRequestBean fromJsonToGetUserOptionPeerRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, GetUserOptionPeerRequestBean.class);
    }

    public static final SignedMessageBean fromJsonToSignedMessageBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils
                .getJacksonMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(jsonMessage, SignedMessageBean.class);
    }

    public static final String getConversationName(String[] partecipants, String topicHash) throws ChatMessageException {
        try {
            Arrays.sort(partecipants);
            ConversationNameHashBean conversationNameHashBean = new ConversationNameHashBean(Arrays.asList(partecipants), topicHash);
            String canonicalJson = SimpleRequestHelper.getCanonicalJson(conversationNameHashBean);
            return TkmSignUtils.Hash256ToHex(canonicalJson);
        } catch (JsonProcessingException | HashEncodeException | HashAlgorithmNotFoundException | HashProviderNotFoundException ex) {
            throw new ChatMessageException(ex);
        }
    }

    public static final String getUserConversationHash(String memberKey, String conversationName) throws HashAlgorithmNotFoundException, HashEncodeException, HashProviderNotFoundException {
        return TkmSignUtils.Hash256B64URL(memberKey + conversationName);
    }
}
