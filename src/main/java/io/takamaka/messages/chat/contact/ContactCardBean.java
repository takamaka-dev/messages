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
package io.takamaka.messages.chat.contact;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A shared contact: an address, optionally with the name the SENDER suggests
 * for it. Travels as an inline object inside the encrypted message body —
 * {@code attached_media[]} entry with {@code is_the_object = true},
 * {@code media_type = }{@link ContactCardConstants#MEDIA_TYPE}, and the
 * canonical JSON bytes of this bean ({@link ContactCardCodec#encode}) as the
 * standard-Base64 {@code preview}. The server never sees it.
 *
 * <p><b>A card is a claim by the sender, not an identity.</b> The name is
 * peer-authored text: a receiver sanitises it, shows it as "shared by", and
 * never lets it overwrite a nickname the user gave the address. No encryption
 * key is carried — the receiver obtains it through verified discovery.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContactCardBean {

    /**
     * Card schema version, {@link ContactCardConstants#VERSION_1_0}. REQUIRED.
     */
    @JsonProperty("v")
    private String version;

    /**
     * The shared address (a public key accepted by
     * {@code AddressValidation.isValidPublicKey}). REQUIRED.
     */
    @JsonProperty("address")
    private String address;

    /**
     * The name the sender suggests, at most
     * {@link ContactCardConstants#MAX_NAME_CODE_POINTS} code points. Optional:
     * absent when the sender shares the address alone (never an empty string).
     */
    @JsonProperty("name")
    private String name;

    /**
     * A phone number the sender attaches, free text, at most
     * {@link ContactCardConstants#MAX_PHONE_CHARS} characters. Optional.
     * Local data of the receiver once saved — never used for discovery.
     */
    @JsonProperty("phone")
    private String phone;
}
