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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ACK for a {@code submitreadreceipt} request-response (READ_RECEIPT_DESIGN.md
 * D10): echoes the request signature for correlation, with {@code applied} +
 * machine-readable {@code error}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReadReceiptResponseBean {

    public static final String ERR_INVALID_SIGNATURE = "invalid_signature";
    public static final String ERR_NOT_A_MEMBER = "not_a_member";
    public static final String ERR_OFF_SCHEMA = "off_schema";
    public static final String ERR_RATE_LIMITED = "rate_limited";
    public static final String ERR_INTERNAL = "internal_error";

    @JsonProperty("request_signature")
    private String requestSignature;

    @JsonProperty("applied")
    private boolean applied;

    @JsonProperty("error")
    private String error;

    public static ReadReceiptResponseBean applied(String requestSignature) {
        return new ReadReceiptResponseBean(requestSignature, true, null);
    }

    public static ReadReceiptResponseBean rejected(String requestSignature, String error) {
        return new ReadReceiptResponseBean(requestSignature, false, error);
    }
}
