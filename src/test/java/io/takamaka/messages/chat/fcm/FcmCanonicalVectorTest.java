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
package io.takamaka.messages.chat.fcm;

import io.takamaka.messages.chat.core.NonceResponseBean;
import io.takamaka.messages.utils.SimpleRequestHelper;
import lombok.extern.slf4j.Slf4j;
import static org.junit.Assert.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * Golden canonical-JSON vector the server signs/verifies for a FIXED FCM signed
 * content. The Flutter client's {@code TkmCanonicalJson.encode} MUST produce the
 * byte-identical strings below (see the mirrored Dart test in takamaka_sdk_wrap).
 *
 * Locks the two cross-platform gotchas:
 * - keys sorted (RFC 8785 JCS), including the nested nonce object;
 * - a NULL device_id is INCLUDED as {@code "device_id":null}, NOT omitted — so the
 *   Dart client must send device_id:null, never drop the key.
 */
@Slf4j
public class FcmCanonicalVectorTest {

    // Shared fixed input (keep identical in the Dart mirror test).
    private static final NonceResponseBean NONCE = new NonceResponseBean(
            "11111111-2222-3333-4444-555555555555", 1700000000000L, 60000L);
    private static final String TOKEN = "fixed-fcm-token-XYZ";
    private static final String PLATFORM = "android";

    static final String VECTOR_WITH_DEVICE =
            "{\"device_id\":\"device-01\",\"fcm_token\":\"fixed-fcm-token-XYZ\",\"nonce\":{\"liveness\":60000,\"nonce\":\"11111111-2222-3333-4444-555555555555\",\"timestamp\":1700000000000},\"platform\":\"android\"}";
    static final String VECTOR_NULL_DEVICE =
            "{\"device_id\":null,\"fcm_token\":\"fixed-fcm-token-XYZ\",\"nonce\":{\"liveness\":60000,\"nonce\":\"11111111-2222-3333-4444-555555555555\",\"timestamp\":1700000000000},\"platform\":\"android\"}";

    @Test
    public void canonicalJson_withDevice_matchesVector() throws Exception {
        FcmTokenRegistrationSignedContentBean content = new FcmTokenRegistrationSignedContentBean(
                NONCE, TOKEN, PLATFORM, "device-01");
        assertEquals(VECTOR_WITH_DEVICE, SimpleRequestHelper.getCanonicalJson(content));
    }

    @Test
    public void canonicalJson_nullDevice_includesNullKey() throws Exception {
        FcmTokenRegistrationSignedContentBean content = new FcmTokenRegistrationSignedContentBean(
                NONCE, TOKEN, PLATFORM, null);
        assertEquals(VECTOR_NULL_DEVICE, SimpleRequestHelper.getCanonicalJson(content));
    }
}
