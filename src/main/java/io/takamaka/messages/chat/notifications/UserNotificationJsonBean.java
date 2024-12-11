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
import io.takamaka.messages.utils.NOTIFICATION_TYPES;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserNotificationJsonBean {
    
    @JsonProperty("notification_hash")
    private String notificationHash;
    @JsonProperty("sender_key")
    private String senderKey;
    @JsonProperty("receiver_key")
    private String receiverKey;
    @JsonProperty("submission_time")
    private Long submissionTime;
    @JsonProperty("notification_type")
    private String notificationType;
    @JsonProperty("conversation_hash_name")
    private String conversationHashName;
    @JsonProperty("read")
    private boolean read;

}
