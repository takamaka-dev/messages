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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Client-owned monotonic read cursor for one {@code (conversation, local
 * identity, peer)} (READ_RECEIPT_DESIGN.md D5/§12.2). The authoritative read
 * <em>position</em> lives here (the server store is best-effort only).
 *
 * <p>Mirrors the Flutter {@code PeerReadCursors} Drift row; this is the Java
 * reference whose {@link ReadReceiptCursorMerge#merge} behaviour the Flutter
 * port must reproduce byte-for-byte (parity vectors, §12.3).</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PeerReadCursor {

    /** Resolved watermark: the message signature the cursor points to. {@code null} until first resolve. */
    private String lastReadMessageSignature;

    /** Local message_timestamp of the watermark — the monotonic ordering key. {@code null} until first resolve. */
    private Long lastReadTimestamp;

    /**
     * The SINGLE parked unknown watermark (received but not yet resolvable to a
     * local message). Overwritten last-unknown-wins; re-checked on backfill.
     * Never a set (REJECTED-C: unbounded, fan-out-amplified DoS).
     */
    private String unknownSignature;
}
