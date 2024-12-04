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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import io.takamaka.extra.beans.EncMessageBean;
import io.takamaka.extra.utils.TkmEncryptionUtils;
import io.takamaka.messages.beans.BaseBean;
import io.takamaka.messages.chat.BasicMessageEncryptedContentBean;
import io.takamaka.messages.chat.BasicMessageSignedContentBean;
import io.takamaka.messages.chat.ConversationNameHashBean;
import io.takamaka.messages.chat.SignedContentTopicBean;
import io.takamaka.messages.chat.SignedMessageBean;
import io.takamaka.messages.chat.TopicKeyDistributionItemBean;
import io.takamaka.messages.chat.TopicTitleKeyBean;
import io.takamaka.messages.chat.requests.BasicMessageRequestBean;
import io.takamaka.messages.chat.requests.CreateConversationRequestBean;
import io.takamaka.messages.chat.requests.RegisterUserRequestBean;
import io.takamaka.messages.chat.requests.RegisterUserRequestSignedContentBean;
import io.takamaka.messages.chat.requests.RequestUserKeyRequestBean;
import io.takamaka.messages.chat.requests.RequestUserKeyRequestBeanSignedContent;
import io.takamaka.messages.chat.requests.RetrieveMessageRequestBean;
import io.takamaka.messages.chat.requests.UserNotificationRequestBean;
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

    public static final String parseSafeConversationHashName(String conversationHashName) throws InvalidParameterException {
        if (CONVERSATION_HASH_NAME_PARAM_PATTERN.matcher(conversationHashName).matches()) {
            return conversationHashName;
        }
        throw new InvalidParameterException("invalid conversation hash name");
    }

    public static final TypeReference<List<RequestUserKeyRequestBeanSignedContent>> type_ListRequestUserKeyRequestBeanSignedContent = new TypeReference<>() {
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

    public static final List<RequestUserKeyRequestBeanSignedContent> fromJsonToListRequestUserKeyRequestBeanSignedContent(String jsonMessage) throws JsonProcessingException {
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

    public static final UserNotificationRequestBean fromJsonToUserNotificationRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, UserNotificationRequestBean.class);
    }

    public static final RetrieveMessageRequestBean fromJsonToRetrieveMessageRequestBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(jsonMessage, RetrieveMessageRequestBean.class);
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
