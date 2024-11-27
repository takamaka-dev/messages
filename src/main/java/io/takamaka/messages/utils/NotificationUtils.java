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

import io.takamaka.messages.chat.notifications.ConversationRequestNotificationBean;
import java.util.Date;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class NotificationUtils {

    public static final ConversationRequestNotificationBean getConversationRequestNotificationBean(String from, String to, String conversationName, Long timestamp) {
        return new ConversationRequestNotificationBean(conversationName, to, from, timestamp, NOTIFICATION_TYPES.CONVERSATION_REQUEST.name());
    }

    public static final ConversationRequestNotificationBean getConversationRequestNotificationBean(String from, String to, String conversationName) {
        return getConversationRequestNotificationBean(from, to, conversationName, new Date().getTime());
    }
}
