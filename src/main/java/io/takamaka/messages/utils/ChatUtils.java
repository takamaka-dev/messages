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
import com.fasterxml.jackson.databind.DeserializationFeature;
import io.takamaka.messages.beans.BaseBean;
import io.takamaka.messages.chat.SignedMessageBean;
import io.takamaka.messages.chat.requests.RegisterUserRequestBean;
import io.takamaka.messages.chat.requests.RegisterUserRequestSignedContentBean;
import io.takamaka.messages.exception.ChatMessageException;
import io.takamaka.messages.exception.InvalidChatMessageSignatureException;
import io.takamaka.messages.exception.MessageException;
import io.takamaka.messages.exception.UnsupportedChatMessageTypeException;
import io.takamaka.messages.exception.UnsupportedSignatureCypherException;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.TkmCypherProviderBCED25519;
import io.takamaka.wallet.beans.TkmCypherBean;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.KeyContexts;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class ChatUtils {

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

    public static final SignedMessageBean fromJsonToSignedMessageBean(String jsonMessage) throws JsonProcessingException {
        return TkmTextUtils
                .getJacksonMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(jsonMessage, SignedMessageBean.class);
    }

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
            }
            else{
                throw new InvalidChatMessageSignatureException("invalid message signature");
            }

        } catch (JsonProcessingException ex) {
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
}
