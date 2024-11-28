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
package io.takamaka.messages.chat.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.chat.UserNotificationBean;
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
public class MessageNotificationBean extends UserNotificationBean {

    public MessageNotificationBean(String conversationName, String to, String from, Long timestamp, String notificationType) {
        super(to, from, timestamp, notificationType);
        this.conversationName = conversationName;
    }

    @JsonProperty("conversation-name")
    private String conversationName;
}