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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.chat.core.NonceResponseBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Signed content ({@code pl}) of a {@code retrievereadreceipts} subscribe
 * (READ_RECEIPT_DESIGN.md §8 F3 / D10). The server-issued nonce is validated
 * (issued-to-{@code from}, unused, unexpired) and consumed on subscribe — closing
 * the F3 subscription-replay residual.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReadReceiptSubscribeSignedContentBean {

    /** Server-issued nonce; validated + consumed on subscribe (F3 fix, D4 precedent). */
    @JsonProperty("nonce")
    private NonceResponseBean nonce;

    /** Resync cursor (server-time); receipts newer than this are replayed. {@code null} = full snapshot. */
    @JsonProperty("not_before")
    private Long notBefore;
}
