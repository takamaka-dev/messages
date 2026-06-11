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
package io.takamaka.messages.chat.receipt;

/**
 * The single deterministic monotonic-merge function for peer read cursors
 * (READ_RECEIPT_DESIGN.md D5/§12.2). Every read-position path — explicit
 * receipt, soft inference, backfill resolution — funnels through {@link #merge}
 * so the Java reference and the Flutter {@code PeerReadCursorRepository} agree
 * byte-for-byte (parity vectors, §12.3).
 *
 * <p>Monotonic: a cursor only ever advances. Regressions and replays are
 * ignored (they can never move it backward or fabricate a read). The
 * equal-timestamp tiebreaker is by lexicographically higher {@code candidateSig}
 * — a <em>client-side</em> rule (the server imposes no total order).</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class ReadReceiptCursorMerge {

    private ReadReceiptCursorMerge() {
    }

    /**
     * Merge a candidate watermark (already resolved to a LOCAL message) into the
     * cursor. Advances iff the candidate is strictly newer (by timestamp, then
     * by signature). Mutates {@code cursor} on advance.
     *
     * @param cursor       the cursor to merge into (mutated on advance)
     * @param candidateSig the candidate message signature
     * @param candidateTs  the candidate's local message_timestamp
     * @return {@code true} iff the cursor advanced
     */
    public static boolean merge(PeerReadCursor cursor, String candidateSig, long candidateTs) {
        final boolean advance
                = cursor.getLastReadTimestamp() == null
                || candidateTs > cursor.getLastReadTimestamp()
                || (candidateTs == cursor.getLastReadTimestamp()
                    && compareSig(candidateSig, cursor.getLastReadMessageSignature()) > 0);
        if (advance) {
            cursor.setLastReadMessageSignature(candidateSig);
            cursor.setLastReadTimestamp(candidateTs);
        }
        return advance;
    }

    /**
     * Apply an explicit receipt whose watermark {@code W} (server ts {@code T})
     * has been decrypted. If {@code resolvedTs} is non-null, {@code W} resolved
     * to a local message and is merged (and the unknown slot cleared if it held
     * {@code W}); otherwise {@code W} is parked in the single unknown slot
     * (overwrite, never a set) and the cursor is left untouched.
     *
     * @param cursor     the cursor (mutated)
     * @param watermark  the decrypted last-read message signature
     * @param resolvedTs the local timestamp of {@code watermark}, or {@code null} if not resolvable
     * @return {@code true} iff the cursor advanced
     */
    public static boolean applyExplicit(PeerReadCursor cursor, String watermark, Long resolvedTs) {
        if (resolvedTs != null) {
            boolean advanced = merge(cursor, watermark, resolvedTs);
            if (watermark.equals(cursor.getUnknownSignature())) {
                cursor.setUnknownSignature(null);
            }
            return advanced;
        }
        // unresolved: park in the single slot (last-unknown-wins), cursor untouched
        cursor.setUnknownSignature(watermark);
        return false;
    }

    /**
     * On message backfill, attempt to resolve the parked unknown slot. If it now
     * maps to a local message, merge it and clear the slot.
     *
     * @param cursor     the cursor (mutated)
     * @param resolvedTs the local timestamp the parked unknown now resolves to,
     *                   or {@code null} if still unresolvable
     * @return {@code true} iff the cursor advanced
     */
    public static boolean resolveUnknownOnBackfill(PeerReadCursor cursor, Long resolvedTs) {
        if (cursor.getUnknownSignature() == null || resolvedTs == null) {
            return false;
        }
        boolean advanced = merge(cursor, cursor.getUnknownSignature(), resolvedTs);
        cursor.setUnknownSignature(null);
        return advanced;
    }

    private static int compareSig(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }
}
