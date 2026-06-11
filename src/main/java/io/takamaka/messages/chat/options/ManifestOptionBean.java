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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One option entry in the public capability manifest (D7). Built from the
 * authoritative {@code user-options-registry.md}. Additive discovery only:
 * clients ship their own baseline schema for {@code security_relevant} options
 * and never trust the manifest as authority over privacy semantics.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ManifestOptionBean {

    @JsonProperty("pn")
    private String parameterName;

    @JsonProperty("v")
    private String version;

    /**
     * The strict JSON schema the server validates the value against.
     */
    @JsonProperty("json_schema")
    private String jsonSchema;

    /**
     * The default value (when the row is absent or below the reset watermark).
     */
    @JsonProperty("default")
    private String defaultValue;

    @JsonProperty("description")
    private String description;

    @JsonProperty("security_relevant")
    private boolean securityRelevant;

    /**
     * {@code Protected} | {@code MembersOnly} | {@code Public}.
     */
    @JsonProperty("visibility")
    private String visibility;

    /**
     * For {@code MembersOnly}/{@code Public}: the exact projection peers may
     * read. {@code null} for {@code Protected}.
     */
    @JsonProperty("public_projection")
    private String publicProjection;
}
