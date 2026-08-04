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
public enum NOTIFICATION_TYPES {
    CONVERSATION_REQUEST,
    NEW_MESSAGE,
    QUOTE_IN_CONVERSATION,
    /**
     * Unsigned invalidation tickle: "your user options changed at T, re-fetch".
     * Carries no value (USER_OPTIONS_DESIGN.md D8). Align this literal with the
     * Flutter client.
     */
    SETTINGS_UPDATE,
    /**
     * DR-025 "delete for everyone" fan-out to OFFLINE recipients: "a message in
     * this conversation was deleted — re-sync". Its purpose is to WAKE an offline
     * device to sync the tombstone (deleted=true in history), NOT to render a
     * "new message". Never pushed to the deleter (NotificationService no-self-push).
     * The Flutter client should treat it as a sync trigger, not a display banner.
     */
    MESSAGE_DELETED
}
