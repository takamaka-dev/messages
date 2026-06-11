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
package io.takamaka.messages.chat.options;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for {@code getuseroptionpeer} (D10). Carries ONLY the option's
 * declared {@code public_projection} for the target, never the whole row.
 *
 * <p>{@code status}:
 * <ul>
 *   <li>{@code visible} — the projection (or the {@code default} projection for
 *       unset/below-watermark) is in {@code val};</li>
 *   <li>{@code not_visible} — the option is {@code Protected}, or the reader is
 *       not a co-member of a {@code MembersOnly} option; {@code val} is null and
 *       reveals no existence information (uniform response).</li>
 * </ul>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserOptionPeerResponseBean {

    public static final String STATUS_VISIBLE = "visible";
    public static final String STATUS_NOT_VISIBLE = "not_visible";

    /**
     * The queried option's wire key.
     */
    @JsonProperty("pn")
    private String parameterName;

    /**
     * The projection JSON (e.g. {@code {"enabled":false}}), or {@code null} when
     * {@code status == not_visible}.
     */
    @JsonProperty("val")
    private String projectionJson;

    /**
     * {@code visible} or {@code not_visible}.
     */
    @JsonProperty("status")
    private String status;

    public static UserOptionPeerResponseBean visible(String parameterName, String projectionJson) {
        return new UserOptionPeerResponseBean(parameterName, projectionJson, STATUS_VISIBLE);
    }

    public static UserOptionPeerResponseBean notVisible(String parameterName) {
        return new UserOptionPeerResponseBean(parameterName, null, STATUS_NOT_VISIBLE);
    }
}
