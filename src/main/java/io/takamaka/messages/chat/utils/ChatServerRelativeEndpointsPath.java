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

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
/**
 * Defines the relative endpoint paths for the chat server API. This class
 * provides constant string values for various API endpoints, making it easier
 * to manage and refer to them throughout the application.
 */
public class ChatServerRelativeEndpointsPath {

    /**
     * The relative path for the nonce endpoint, used for nonce challenge
     * retrival.
     */
    public static final String NONCE = "nonce";

    /**
     * The relative path for the user encryption/decryption keys registration
     * endpoint.
     */
    public static final String REGISTER_USER = "registeruser";

    /**
     * The relative path for requesting user public encryption/decryption keys.
     */
    public static final String REQUEST_KEYS = "requestkeys";

    /**
     * The relative path for submit a new conversation bean.
     */
    public static final String CREATE_CONVERSATION = "createconversation";

    /**
     * The relative path for sending or receiving notifications.
     */
    public static final String NOTIFICATION = "notification";

    /**
     * The relative path for retrieving notification history.
     */
    public static final String NOTIFICATION_HISTORY = "notificationhistory";

    /**
     * The relative path for retrieving all conversations hashes for a user.
     */
    public static final String RETRIEVE_ALL_CONVERSATIONS = "retrieveallconversations";

    /**
     * The relative path for retrieving a specific conversation key bean.
     */
    public static final String RETRIEVE_CONVERSATION = "retrieveconversation";
    public static final String RETRIEVE_MESSAGES = "retrievemessages";
    public static final String RETRIEVE_ALL_MESSAGES = "retrieveallmessages";
}
