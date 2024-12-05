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

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public enum CHAT_MESSAGE_TYPES {
    REGISTER_USER_SIGNED_REQUEST,
    REQUEST_USER_KEYS,
    TOPIC_CREATION,
    TOPIC_MESSAGE,
    NOTIFICATION_REQUEST,
    RETRIEVE_MESSAGE_FROM_CONVERSATION_LAST_N,
    RETRIEVE_MESSAGE_FROM_CONVERSATION_BY_SIGNATURE
    
}
