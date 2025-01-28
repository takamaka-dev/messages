/*
 * Copyright 2025 AiliA SA.
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
package io.takamaka.messages.chat.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.takamaka.messages.chat.requests.RegisterUserRequestBean;
import io.takamaka.messages.exception.ChatMessageSerializationException;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serializer Deserializer for chat messages
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class ChatSD {

    public static final TypeReference<List<RegisterUserRequestBean>> type_listRegisterUserRequestBean = new TypeReference<>() {
    };

    public static final String fromObjectToJson(Object obj) throws ChatMessageSerializationException {
        try {
            return TkmTextUtils.getJacksonMapper().writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            throw new ChatMessageSerializationException(ex);
        }
    }

    public static final List<RegisterUserRequestBean> fromJsonToListRegisterUserRequestBean(String jsonval) throws ChatMessageSerializationException {
        try {
            return TkmTextUtils.getJacksonMapper().readValue(jsonval, type_listRegisterUserRequestBean);
        } catch (JsonProcessingException ex) {
            throw new ChatMessageSerializationException(ex);
        }
    }
}
