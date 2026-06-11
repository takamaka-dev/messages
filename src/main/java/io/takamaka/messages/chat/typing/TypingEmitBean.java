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
package io.takamaka.messages.chat.typing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Plain fire-and-forget typing-emit frame (TYPING_INDICATOR_DESIGN.md D2/D3).
 * Carries <b>only</b> the conversation hash — <b>no {@code from} field</b>: the
 * server attributes the sender from the connection-bound identity (D3), never
 * from the frame. Sent ~1/sec while composing; lossy-OK, never persisted.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TypingEmitBean {

    /** 64-hex conversation routing key. */
    @JsonProperty("conv")
    private String conversationHashName;

    /** Feature/format protocol version (e.g. "1.0"). */
    @JsonProperty("pv")
    private String protocolVersion;
}
