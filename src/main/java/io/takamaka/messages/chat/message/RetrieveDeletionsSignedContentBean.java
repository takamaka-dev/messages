/*
 * Copyright 2026 AiliA SA.
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
package io.takamaka.messages.chat.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The signed content ({@code pl}) of a {@code retrievedeletions} request — the DR-025 catch-up read.
 *
 * <p><b>Why this exists.</b> A delete is fanned out live, and a member offline at that moment had no way to
 * learn of it: the tombstoned message comes back from history with {@code message_json = ''} (no verifiable
 * envelope), and the owner-signed delete envelope sat in {@code message_deletions} which nothing could read.
 * So "delete for everyone" reached only whoever happened to be connected. This request lets a reconnecting
 * client fetch the signed envelopes it missed and verify them itself.</p>
 *
 * <p>All fields are cleartext — they are routing/cursor metadata, not content. The server returns the delete
 * envelopes VERBATIM, so the client re-verifies the owner's signature rather than trusting the relay; the
 * deletion log exists precisely to make that possible.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RetrieveDeletionsSignedContentBean {

    /** The conversation whose deletions to replay — membership is checked against it server-side. */
    @JsonProperty("conversation_hash_name")
    private String conversationHashName;

    /**
     * Cursor: replay deletions whose SERVER delete-time is strictly greater than this. {@code null} or
     * {@code 0} = everything the server still holds (bounded by the deletion-log retention window).
     */
    @JsonProperty("since")
    private Long since;

    /** Client-authored timestamp — advisory freshness / anti-replay symmetry only, never authoritative. */
    @JsonProperty("client_ts")
    private Long clientTimestamp;
}
