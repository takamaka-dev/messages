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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ACK for a {@code deletemessage} request-response (DR-025): echoes the
 * TARGET message signature for correlation, with {@code accepted} +
 * machine-readable {@code error}. Mirrors {@code ReadReceiptResponseBean}'s
 * accepted/rejected factory-method style.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeleteMessageResponseBean {

    public static final String ERR_RATE_LIMITED = "rate_limited";
    public static final String ERR_INVALID_SIGNATURE = "invalid_signature";
    public static final String ERR_NOT_OWNER = "not_owner";
    public static final String ERR_NOT_MEMBER = "not_member";
    public static final String ERR_WINDOW_EXPIRED = "window_expired";
    public static final String ERR_NOT_FOUND = "not_found";
    public static final String ERR_INTERNAL = "internal_error";

    @JsonProperty("target_message_signature")
    private String targetMessageSignature;

    @JsonProperty("accepted")
    private boolean accepted;

    @JsonProperty("server_delete_time")
    private Long serverDeleteTime;

    @JsonProperty("error")
    private String error;

    @JsonProperty("purged_attachment_count")
    private int purgedAttachmentCount;

    public static DeleteMessageResponseBean accepted(String targetMessageSignature, long serverDeleteTime, int purgedAttachmentCount) {
        return new DeleteMessageResponseBean(targetMessageSignature, true, serverDeleteTime, null, purgedAttachmentCount);
    }

    public static DeleteMessageResponseBean rejected(String targetMessageSignature, String error) {
        return new DeleteMessageResponseBean(targetMessageSignature, false, null, error, 0);
    }
}
