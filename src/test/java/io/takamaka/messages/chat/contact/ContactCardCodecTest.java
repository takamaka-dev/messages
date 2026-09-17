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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

/**
 * DR-044 contact card v1.0: the committed vectors
 * ({@code contact-card/contact_card_vectors.json}) pin the canonical bytes and
 * the content hash every platform must produce, and the cards every platform
 * must refuse.
 */
public class ContactCardCodecTest {

    private static JsonNode vectors() throws Exception {
        try (InputStream in = ContactCardCodecTest.class
                .getResourceAsStream("/contact-card/contact_card_vectors.json")) {
            return new ObjectMapper().readTree(in);
        }
    }

    @Test
    public void encodesEveryVectorToItsCanonicalBytesAndHash() throws Exception {
        for (JsonNode c : vectors().get("cases")) {
            final JsonNode card = c.get("card");
            final ContactCardBean bean = new ContactCardBean(
                    card.get("v").asText(),
                    card.get("address").asText(),
                    card.has("name") ? card.get("name").asText() : null,
                    card.has("phone") ? card.get("phone").asText() : null);
            final byte[] bytes = ContactCardCodec.encode(bean);
            assertArrayEquals(c.get("canonical").asText().getBytes(StandardCharsets.UTF_8), bytes,
                    c.get("id").asText());
            assertEquals(c.get("sha3_256_hex").asText(), ContactCardCodec.contentHash(bytes),
                    c.get("id").asText());
            assertEquals(bean, ContactCardCodec.decode(bytes), c.get("id").asText());
        }
    }

    @Test
    public void refusesEveryInvalidVector() throws Exception {
        for (JsonNode c : vectors().get("invalid")) {
            final byte[] bytes = c.get("json").asText().getBytes(StandardCharsets.UTF_8);
            assertThrows(ContactCardCodec.InvalidContactCardException.class,
                    () -> ContactCardCodec.decode(bytes), c.get("id").asText());
        }
    }

    @Test
    public void mediaTypesAreDistinct() {
        assertEquals("application/vnd.takamaka.contact+json", ContactCardConstants.MEDIA_TYPE);
        assertEquals("vnd.takamaka.contact+json", ContactCardConstants.LEGACY_MEDIA_TYPE);
    }
}
