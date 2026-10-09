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
    PROFILE_UPDATE,
    /**
     * C182 (tkm-call/v1 §7.1, DR-055): an incoming call. Delivered on the live notification stream or as an FCM
     * push, and <b>never stored</b> (N9): no {@code user_notifications} row, so it never appears in
     * {@code notificationhistory}. The notification carries {@code ring} = {@code call}, {@code svc}, {@code f},
     * {@code mode} and nothing else; the callee shows the caller as verified only after fetching and verifying the
     * creation record from the call service (N7). Align this literal with the Flutter client.
     */
    CALL_RING,
    /**
     * C182 [0.2] (tkm-call/v1 §7.1, C-16 answered elsewhere): a device of THIS identity accepted the call named by
     * {@code ring.call}; a device still ringing for it dismisses the ring ({@code elsewhere}). Delivered like
     * {@link #CALL_RING} — live or as a push, never stored — to the callee's own devices only. A separate type so that
     * a client that knows only {@code CALL_RING} ignores it instead of ringing. Align this literal with the Flutter
     * client.
     */
    CALL_ANSWERED
}
