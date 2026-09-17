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

/**
 * Literals and caps of the shared contact card ({@link ContactCardBean}).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class ContactCardConstants {

    private ContactCardConstants() {
    }

    /** {@code media_type} of a contact card inline object. */
    public static final String MEDIA_TYPE = "application/vnd.takamaka.contact+json";

    /**
     * {@code media_type} of the UNVERSIONED card another Takamaka app already
     * sends ({@code {"name":"","address":"…","phone":""}}, empty strings for
     * absent fields, no {@code v}). Not produced here; reading it is a tracked
     * follow-up, so it is only named for now.
     */
    public static final String LEGACY_MEDIA_TYPE = "vnd.takamaka.contact+json";

    /** Card schema version 1.0. */
    public static final String VERSION_1_0 = "1.0";

    /** {@code file_name} producers give the inline object. */
    public static final String FILE_NAME = "contact.json";

    /** Longest suggested name, in Unicode code points (the profile cap). */
    public static final int MAX_NAME_CODE_POINTS = 64;

    /** Longest phone field, in chars. */
    public static final int MAX_PHONE_CHARS = 32;

    /**
     * Largest encoded card, in bytes: room for a qTesla address (19 840 chars)
     * and well under {@code InlineContentLimits.MAX_INLINE_BYTES}.
     */
    public static final int MAX_CARD_BYTES = 32 * 1024;
}
