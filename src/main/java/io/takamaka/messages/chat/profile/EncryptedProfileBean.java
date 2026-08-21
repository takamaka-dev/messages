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
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The sealed profile as it exists ON THE WIRE and AT REST — registry §4.1.
 * Everything the server ever sees of a profile is in this bean, and none of it
 * is readable: {@link #blob} is AES-256-GCM ciphertext under a key the server
 * never holds (design D2).
 *
 * <p>The server validates size and structure only — length, base64
 * decodability, {@link #cipher}, {@link #blobVersion} — and echoes the rest
 * (design D8).</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EncryptedProfileBean {

    /**
     * Which profile-key generation this blob is sealed under: the
     * {@code nonce_issue_time} of the write that created the key. A grant
     * unwraps a blob iff their epochs match (design D12).
     */
    @JsonProperty("key_epoch")
    private long keyEpoch;

    /**
     * Envelope schema version, {@code "1.0"} — how to decrypt. Validated
     * server-side; an unknown value is {@code ERR_UNKNOWN_VERSION}. Distinct
     * from the card's {@code payload_version}, which is inside the ciphertext
     * and which the server cannot see (registry §3).
     */
    @JsonProperty("blob_version")
    private String blobVersion;

    /**
     * {@link ProfileConstants#CIPHER_AES_256_GCM}. An unknown value is
     * {@code ERR_UNKNOWN_CIPHER}.
     */
    @JsonProperty("cipher")
    private String cipher;

    /**
     * {@code base64url(IV || ciphertext || GCM tag)}.
     *
     * <p><b>Base64URL</b> — the same alphabet as {@code enc_key}, signatures,
     * hashes and addresses. Readers accept BOTH alphabets permanently
     * ({@code TkmSignUtils.fromAnyB64ToByteArray}); that is the read contract,
     * not a migration window. The one field in this estate that disagreed with
     * its neighbours on encoding made ~30% of test-VM conversations
     * permanently unreadable and failed silently at six separate points (F11,
     * {@code security/BASE64_ENCODING_CONTRACT.md} §0.1).</p>
     */
    @JsonProperty("blob")
    private String blob;

    /**
     * SHA3-256 hex of the DECODED blob bytes — the cache validator behind
     * {@code known_blob_hash} and the digest batch (design D11).
     *
     * <p><b>Over BYTES, never over an encoding.</b> Not the hash of the base64
     * text and not the hash of the card plaintext. DR-030 is the write-up of
     * what happens otherwise; DR-031 is the write-up of the check that then
     * failed to enforce it — whose four tests all passed while the check did
     * nothing. Assert the enforcement, not the error message.</p>
     */
    @JsonProperty("blob_hash")
    private String blobHash;
}
