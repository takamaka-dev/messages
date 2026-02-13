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
package io.takamaka.messages.chat.attachment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.takamaka.extra.beans.StreamEncryptedDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ChatMediaPlaceholderBean} serialization and new fields.
 */
class ChatMediaPlaceholderBeanTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Test
    void testRoundTripAllFields() throws Exception {
        ChatMediaPlaceholderBean bean = ChatMediaPlaceholderBean.builder()
                .mediaType("image/webp")
                .size(12345L)
                .unencryptedContentHash("abc123hash")
                .encryptedFileHash("def456hash")
                .preview("base64previewdata==")
                .isTheObject(false)
                .fileName("photo.webp")
                .originalSize(9876L)
                .packHash("pack001")
                .stickerId("sticker042")
                .emoji("\uD83D\uDE00")
                .build();

        String json = mapper.writeValueAsString(bean);
        ChatMediaPlaceholderBean deserialized = mapper.readValue(json, ChatMediaPlaceholderBean.class);

        assertEquals(bean.getMediaType(), deserialized.getMediaType());
        assertEquals(bean.getSize(), deserialized.getSize());
        assertEquals(bean.getUnencryptedContentHash(), deserialized.getUnencryptedContentHash());
        assertEquals(bean.getEncryptedFileHash(), deserialized.getEncryptedFileHash());
        assertEquals(bean.getPreview(), deserialized.getPreview());
        assertEquals(bean.getIsTheObject(), deserialized.getIsTheObject());
        assertEquals(bean.getFileName(), deserialized.getFileName());
        assertEquals(bean.getOriginalSize(), deserialized.getOriginalSize());
        assertEquals(bean.getPackHash(), deserialized.getPackHash());
        assertEquals(bean.getStickerId(), deserialized.getStickerId());
        assertEquals(bean.getEmoji(), deserialized.getEmoji());
    }

    @Test
    void testBackwardCompatDeserializeOldJson() throws Exception {
        // Old JSON format without new fields
        String oldJson = "{\"media_type\":\"image/png\",\"size\":5000,"
                + "\"unencrypted_content_hash\":\"hash1\","
                + "\"encrypted_file_hash\":\"hash2\"}";

        ChatMediaPlaceholderBean bean = mapper.readValue(oldJson, ChatMediaPlaceholderBean.class);

        assertEquals("image/png", bean.getMediaType());
        assertEquals(5000L, bean.getSize());
        assertEquals("hash1", bean.getUnencryptedContentHash());
        assertEquals("hash2", bean.getEncryptedFileHash());
        assertNull(bean.getSed());
        assertNull(bean.getPreview());
        assertNull(bean.getIsTheObject());
        assertNull(bean.getFileName());
        assertNull(bean.getOriginalSize());
        assertNull(bean.getPackHash());
        assertNull(bean.getStickerId());
        assertNull(bean.getEmoji());
    }

    @Test
    void testForwardCompatIgnoreUnknownFields() throws Exception {
        // JSON with future unknown fields
        String futureJson = "{\"media_type\":\"image/png\",\"size\":5000,"
                + "\"unencrypted_content_hash\":\"hash1\","
                + "\"encrypted_file_hash\":\"hash2\","
                + "\"some_future_field\":\"value\","
                + "\"another_future_field\":42}";

        // Should not throw
        ChatMediaPlaceholderBean bean = mapper.readValue(futureJson, ChatMediaPlaceholderBean.class);

        assertEquals("image/png", bean.getMediaType());
        assertEquals(5000L, bean.getSize());
    }

    @Test
    void testNullFieldsOmittedFromJson() throws Exception {
        ChatMediaPlaceholderBean bean = ChatMediaPlaceholderBean.builder()
                .mediaType("image/png")
                .size(5000L)
                .unencryptedContentHash("hash1")
                .encryptedFileHash("hash2")
                .build();

        String json = mapper.writeValueAsString(bean);

        // Null fields should NOT appear in JSON (thanks to @JsonInclude NON_NULL)
        assertFalse(json.contains("preview"));
        assertFalse(json.contains("is_the_object"));
        assertFalse(json.contains("file_name"));
        assertFalse(json.contains("original_size"));
        assertFalse(json.contains("pack_hash"));
        assertFalse(json.contains("sticker_id"));
        assertFalse(json.contains("emoji"));

        // Required fields should appear
        assertTrue(json.contains("media_type"));
        assertTrue(json.contains("size"));
    }

    @Test
    void testIsTheObjectTrueSemantics() throws Exception {
        // When isTheObject=true: no encryptedFileHash, no sed, preview contains content
        ChatMediaPlaceholderBean bean = ChatMediaPlaceholderBean.builder()
                .mediaType("image/webp")
                .size(2048L)
                .unencryptedContentHash("contentHash")
                .preview("base64fullcontent==")
                .isTheObject(true)
                .fileName("sticker.webp")
                .originalSize(2048L)
                .build();

        assertNull(bean.getEncryptedFileHash());
        assertNull(bean.getSed());
        assertTrue(bean.getIsTheObject());
        assertNotNull(bean.getPreview());
        assertEquals(bean.getSize(), bean.getOriginalSize());

        // Round-trip
        String json = mapper.writeValueAsString(bean);
        ChatMediaPlaceholderBean deserialized = mapper.readValue(json, ChatMediaPlaceholderBean.class);

        assertTrue(deserialized.getIsTheObject());
        assertNull(deserialized.getEncryptedFileHash());
        assertNull(deserialized.getSed());
        assertEquals("base64fullcontent==", deserialized.getPreview());
    }

    @Test
    void testIsTheObjectFalseWithPreview() throws Exception {
        // Regular attachment with preview thumbnail
        ChatMediaPlaceholderBean bean = ChatMediaPlaceholderBean.builder()
                .mediaType("image/jpeg")
                .size(150000L)
                .unencryptedContentHash("contentHash")
                .encryptedFileHash("encHash")
                .preview("base64thumbnail==")
                .isTheObject(false)
                .fileName("vacation.jpg")
                .originalSize(100000L)
                .build();

        assertFalse(bean.getIsTheObject());
        assertNotNull(bean.getEncryptedFileHash());
        assertNotNull(bean.getPreview());
        assertTrue(bean.getSize() > bean.getOriginalSize());

        String json = mapper.writeValueAsString(bean);
        ChatMediaPlaceholderBean deserialized = mapper.readValue(json, ChatMediaPlaceholderBean.class);

        assertFalse(deserialized.getIsTheObject());
        assertEquals("encHash", deserialized.getEncryptedFileHash());
        assertEquals("base64thumbnail==", deserialized.getPreview());
        assertEquals(100000L, deserialized.getOriginalSize());
    }

    @Test
    void testBuilderPattern() {
        ChatMediaPlaceholderBean bean = ChatMediaPlaceholderBean.builder()
                .mediaType("application/pdf")
                .size(50000L)
                .unencryptedContentHash("pdfHash")
                .encryptedFileHash("encPdfHash")
                .fileName("document.pdf")
                .originalSize(35000L)
                .build();

        assertEquals("application/pdf", bean.getMediaType());
        assertEquals("document.pdf", bean.getFileName());
        assertEquals(35000L, bean.getOriginalSize());
    }

    @Test
    void testCrossPlatformJsonVector() throws Exception {
        // Generate a test vector that Dart tests can validate against
        ChatMediaPlaceholderBean bean = ChatMediaPlaceholderBean.builder()
                .mediaType("image/webp")
                .size(3072L)
                .unencryptedContentHash("Ks8jf92kLmN_crossplatform_hash")
                .encryptedFileHash("Xp3mQ7r_encrypted_hash")
                .preview("iVBORw0KGgoAAAANSUhEUg==")
                .isTheObject(false)
                .fileName("test_image.webp")
                .originalSize(2048L)
                .build();

        // Canonical JSON (sorted keys)
        ObjectMapper canonicalMapper = new ObjectMapper();
        canonicalMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        String canonical = canonicalMapper.writeValueAsString(bean);

        // Verify expected keys are present
        assertTrue(canonical.contains("\"encrypted_file_hash\""));
        assertTrue(canonical.contains("\"file_name\""));
        assertTrue(canonical.contains("\"is_the_object\""));
        assertTrue(canonical.contains("\"media_type\""));
        assertTrue(canonical.contains("\"original_size\""));
        assertTrue(canonical.contains("\"preview\""));
        assertTrue(canonical.contains("\"size\""));
        assertTrue(canonical.contains("\"unencrypted_content_hash\""));

        // Round-trip
        ChatMediaPlaceholderBean roundTripped = canonicalMapper.readValue(canonical, ChatMediaPlaceholderBean.class);
        assertEquals(bean.getMediaType(), roundTripped.getMediaType());
        assertEquals(bean.getFileName(), roundTripped.getFileName());
        assertEquals(bean.getOriginalSize(), roundTripped.getOriginalSize());
        assertEquals(bean.getIsTheObject(), roundTripped.getIsTheObject());
    }
}
