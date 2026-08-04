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
package io.takamaka.messages.chat.constant;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
/**
 * Defines the relative endpoint paths for the chat server API. This class
 * provides constant string values for various API endpoints, making it easier
 * to manage and refer to them throughout the application.
 */
public class ChatServerEndpoints {

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
    public static final String SUBMIT_MESSAGE = "messages";
    public static final String SUBMIT_ATTACHMENT = "submitattachment";
    public static final String RETRIEVE_ATTACHMENT = "retrieveattachment";

    /**
     * The relative path for registering an FCM token for push notifications.
     * Requires a signed request with the FCM token and device information.
     */
    public static final String REGISTER_FCM_TOKEN = "registerfcmtoken";

    /**
     * The relative path for unregistering an FCM token.
     * Used when logging out or disabling push notifications.
     */
    public static final String UNREGISTER_FCM_TOKEN = "unregisterfcmtoken";

    /**
     * The relative path for permanently deleting this device's FCM token.
     *
     * <p>Unlike {@link #UNREGISTER_FCM_TOKEN}, which only flips {@code is_active}
     * and leaves the row in place until the stale-token sweep collects it, this
     * removes the row. Because the token hash is the table's sole primary key, a
     * lingering deactivated row blocks any later registration of the same device
     * token under a different identity — so a client that cycles identities on
     * one device should delete, not unregister.</p>
     *
     * <p>Signed envelope (message type {@code FCM_TOKEN_REGISTRATION}, same signed
     * content as registration) plus a server-issued nonce, which this route
     * consumes: each delete envelope is single-use.</p>
     */
    public static final String DELETE_FCM_TOKEN = "deletefcmtoken";

    /**
     * The relative path for permanently deleting every FCM token registered by
     * the signing identity — "stop pushing to all my devices".
     *
     * <p>Scoped to the envelope's {@code from} key, so an identity can only ever
     * clear its own rows. The signed content is the registration content bean;
     * its {@code fcm_token} is not used to select rows and may be blank, since a
     * caller wanting to log out everywhere need not hold a live device token.</p>
     *
     * <p>Signed envelope plus a server-issued nonce, which this route consumes.</p>
     */
    public static final String DELETE_ALL_FCM_TOKENS = "deleteallfcmtokens";

    /**
     * The relative path for the server capabilities endpoint. Public and
     * unauthenticated (no nonce, no signature): returns the server build
     * version and the supported wire-protocol range so clients can negotiate
     * compatibility before signing any request.
     */
    public static final String SERVER_INFO = "serverinfo";

    /**
     * Public, unauthenticated capability manifest of supported user options
     * (versions, schemas, descriptions, visibility). Mirrors {@link #SERVER_INFO}
     * in being open (no nonce, no signature). USER_OPTIONS_DESIGN.md D7.
     */
    public static final String USER_OPTIONS_MANIFEST = "useroptionsmanifest";

    /**
     * Write/update a single user option. Signed envelope + server-issued nonce.
     */
    public static final String SET_USER_OPTION = "setuseroption";

    /**
     * Bulk-reset all user options (bumps the per-user reset watermark). Signed
     * envelope + server-issued nonce.
     */
    public static final String RESET_USER_OPTIONS = "resetuseroptions";

    /**
     * Read this identity's own options. Signed, no nonce (idempotent self-read).
     */
    public static final String GET_USER_OPTIONS = "getuseroptions";

    /**
     * Read another identity's option projection (D10). Signed, no nonce,
     * rate-limited; subject to the option's visibility tier.
     */
    public static final String GET_USER_OPTION_PEER = "getuseroptionpeer";

    /**
     * Submit a read receipt (request-response, signed). Server verifies, stores
     * best-effort, and queues for the coalesced fan-out. READ_RECEIPT_DESIGN.md D10.
     */
    public static final String SUBMIT_READ_RECEIPT = "submitreadreceipt";

    /**
     * Subscribe to the dedicated read-receipt stream (request-stream, signed +
     * server-issued nonce). The nonce closes the F3 subscription-replay residual
     * (§8). Emits a catch-up snapshot then live coalesced batches.
     */
    public static final String RETRIEVE_READ_RECEIPTS = "retrievereadreceipts";

    /**
     * Open the transient typing stream (request-stream, signed once). The server
     * verifies the Ed25519 signature and binds the identity to the connection
     * (D3). TYPING_INDICATOR_DESIGN.md D2.
     */
    public static final String TYPING_SUBSCRIBE = "typingsubscribe";

    /**
     * Emit a plain fire-and-forget typing frame (carries only the conversation
     * hash). The sender is attributed from the connection-bound identity, never
     * the frame (D3).
     */
    public static final String TYPING_EMIT = "typingemit";

}
