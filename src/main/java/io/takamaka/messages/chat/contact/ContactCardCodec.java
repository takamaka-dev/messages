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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.takamaka.messages.chat.mention.AddressValidation;
import io.takamaka.wallet.utils.FixedParameters;
import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import org.erdtman.jcs.JsonCanonicalizer;

/**
 * Encodes and decodes a {@link ContactCardBean}.
 *
 * <p>The encoded form is RFC 8785 (JCS) canonical JSON in UTF-8, so the same
 * card has the same bytes — and the same {@code unencrypted_content_hash} — on
 * every platform. Decoding validates; it never repairs.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class ContactCardCodec {

    private ContactCardCodec() {
    }

    /**
     * Thrown when a card is not a valid version-1.0 contact card.
     */
    public static class InvalidContactCardException extends Exception {

        public InvalidContactCardException(String message) {
            super(message);
        }
    }

    /**
     * Canonical UTF-8 bytes of a valid card.
     *
     * @param card the card; validated first
     * @return the bytes to carry as the inline object
     * @throws InvalidContactCardException if the card is not valid
     */
    public static byte[] encode(ContactCardBean card) throws InvalidContactCardException {
        validate(card);
        try {
            final String compact = TkmTextUtils.getJacksonMapper().writeValueAsString(card);
            final byte[] bytes = new JsonCanonicalizer(compact).getEncodedString()
                    .getBytes(StandardCharsets.UTF_8);
            if (bytes.length > ContactCardConstants.MAX_CARD_BYTES) {
                throw new InvalidContactCardException("card larger than "
                        + ContactCardConstants.MAX_CARD_BYTES + " bytes");
            }
            return bytes;
        } catch (IOException ex) {
            throw new InvalidContactCardException("card not serializable: " + ex.getMessage());
        }
    }

    /**
     * Parse and validate card bytes.
     *
     * @param bytes the inline object's decoded bytes
     * @return the card
     * @throws InvalidContactCardException if the bytes are not a valid card
     */
    public static ContactCardBean decode(byte[] bytes) throws InvalidContactCardException {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidContactCardException("empty card");
        }
        if (bytes.length > ContactCardConstants.MAX_CARD_BYTES) {
            throw new InvalidContactCardException("card larger than "
                    + ContactCardConstants.MAX_CARD_BYTES + " bytes");
        }
        final ContactCardBean card;
        try {
            card = TkmTextUtils.getJacksonMapper()
                    .readValue(new String(bytes, StandardCharsets.UTF_8), ContactCardBean.class);
        } catch (JsonProcessingException ex) {
            throw new InvalidContactCardException("not a JSON contact card");
        }
        validate(card);
        return card;
    }

    /**
     * {@code unencrypted_content_hash} of card bytes: SHA3-256, hex (the inline
     * content hash contract).
     *
     * @param bytes encoded card
     * @return 64 hex chars
     */
    public static String contentHash(byte[] bytes) {
        try {
            return TkmSignUtils.fromByteArrayToHexString(
                    TkmSignUtils.Hash256Byte(bytes, FixedParameters.HASH_256_ALGORITHM));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA3-256 unavailable", ex);
        }
    }

    /**
     * Validate a card against version 1.0.
     *
     * @param card the card
     * @throws InvalidContactCardException on the first violation
     */
    public static void validate(ContactCardBean card) throws InvalidContactCardException {
        if (card == null) {
            throw new InvalidContactCardException("no card");
        }
        if (!ContactCardConstants.VERSION_1_0.equals(card.getVersion())) {
            throw new InvalidContactCardException("unsupported card version: " + card.getVersion());
        }
        if (!AddressValidation.isValidPublicKey(card.getAddress())) {
            throw new InvalidContactCardException("invalid address");
        }
        final String name = card.getName();
        if (name != null) {
            if (name.isEmpty()) {
                throw new InvalidContactCardException("empty name (omit the field instead)");
            }
            if (name.codePointCount(0, name.length()) > ContactCardConstants.MAX_NAME_CODE_POINTS) {
                throw new InvalidContactCardException("name longer than "
                        + ContactCardConstants.MAX_NAME_CODE_POINTS + " code points");
            }
        }
        final String phone = card.getPhone();
        if (phone != null) {
            if (phone.isEmpty()) {
                throw new InvalidContactCardException("empty phone (omit the field instead)");
            }
            if (phone.length() > ContactCardConstants.MAX_PHONE_CHARS) {
                throw new InvalidContactCardException("phone longer than "
                        + ContactCardConstants.MAX_PHONE_CHARS + " chars");
            }
        }
    }
}
