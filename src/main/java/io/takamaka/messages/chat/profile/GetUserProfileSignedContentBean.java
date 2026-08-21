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
package io.takamaka.messages.chat.profile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Signed content for a {@code getuserprofile} self-read — design §6.2.
 *
 * <p>Nonce-free: reads are idempotent and replay-harmless, exactly as
 * {@code getuseroptions} is (design D4). The envelope is still SIGNED, because
 * the server must know whose profile row to return.</p>
 *
 * <p>Design §6.2 draws this bean as {@code { }}. It carries
 * {@code client_timestamp} instead of nothing at all for two reasons: a
 * genuinely empty bean is not serialisable by the estate's default
 * {@code ObjectMapper} ({@code FAIL_ON_EMPTY_BEANS}), and every other signed
 * content in this channel and in the options channel it copies carries the same
 * advisory field. It is audit-only and never authoritative for anything.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetUserProfileSignedContentBean {

    /**
     * ADVISORY/audit only.
     */
    @JsonProperty("client_timestamp")
    private Long clientTimestamp;
}
