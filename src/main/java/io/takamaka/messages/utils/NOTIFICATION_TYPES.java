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
    MESSAGE_DELETED,
    /**
     * Unsigned invalidation tickle: "this identity's profile changed — refetch".
     * Payload is {@code {owner_public_key, key_epoch, blob_hash}} and carries NO
     * profile content, which is what lets it ride the plaintext notification
     * sink at all — the same reasoning that let {@code SETTINGS_UPDATE} sidestep
     * the read-receipt blocker (USER_PROFILE_DESIGN.md D10).
     *
     * <p><b>A hint, never a source of truth.</b> A forged or suppressed tickle
     * causes stale UX and nothing worse: the authenticated re-fetch is the
     * truth. Never render from the tickle.</p>
     *
     * <p>Fan-out is the CURRENT epoch's grantees that have a live sink, plus the
     * owner's own sinks so a second device refreshes after an edit — not "all
     * co-members". A peer with no grant has nothing to refetch. Offline peers
     * converge on their next digest poll, the same wake-to-resync posture as
     * {@link #MESSAGE_DELETED}.</p>
     *
     * <p>Align this literal with the Flutter client.</p>
     */
    PROFILE_UPDATE
}
