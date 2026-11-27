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
package io.takamaka.messages.chat.conversation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.chat.conversation.CreateConversationRequestBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateConversationResponseBean {

    @JsonProperty("conversation_hash_name")
    private String conversationHashName;
    @JsonProperty("create_conversation_request")
    private CreateConversationRequestBean createConversationRequestBean;
    /**
     * C182 (tkm-call/v1 §10.1, §11, Design §6.1 rule 6): {@value #RESULT_EXISTS_MEMBER} when the conversation hash
     * already existed and the requester is one of its members — a typed success: {@code create_conversation_request}
     * is then the STORED creation request (not an echo of this one) and no notification was sent. Absent on a fresh
     * creation, so that answer's JSON is unchanged.
     */
    @JsonProperty("result")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String result;

    /** {@code result} of an existing conversation answered to one of its members (§11 {@code exists_member}). */
    public static final String RESULT_EXISTS_MEMBER = "exists_member";

    /** A fresh creation (no {@code result}). */
    public CreateConversationResponseBean(String conversationHashName, CreateConversationRequestBean createConversationRequestBean) {
        this(conversationHashName, createConversationRequestBean, null);
    }

    /** The typed {@code exists_member} success carrying the stored topic. */
    public static CreateConversationResponseBean existsMember(String conversationHashName, CreateConversationRequestBean stored) {
        return new CreateConversationResponseBean(conversationHashName, stored, RESULT_EXISTS_MEMBER);
    }

    /** True when this answer is the typed {@code exists_member} result. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isExistsMember() {
        return RESULT_EXISTS_MEMBER.equals(result);
    }
}
