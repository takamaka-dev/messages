/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.chat.quota;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Live storage-quota status of one identity, as the server holds it ({@code getstoragequota}).
 *
 * <p>Every byte figure is the ENCRYPTED, base64-wrapped size the server actually stores and
 * charges (the same number {@code UploadStatusBean} reports on completion and
 * {@code verified_attachment.size_bytes} carries), never the plaintext size — see
 * {@code ATTACHMENT_PROTOCOL.md} §4.2. A client sizing an upload against
 * {@code available_bytes} must compare the encrypted size, roughly {@code plaintext × 1.368}.
 *
 * <p>{@code initialized == false} means the server holds no row for this identity yet: it has
 * never had an accepted upload (or the row was never created on a pre-0.9.1 server); the figures
 * are then the defaults the first upload will be admitted against. {@code enforced == false}
 * means the operator disabled quota enforcement: the numbers are informational and no upload is
 * refused for exceeding them.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageQuotaStatusResponseBean {

    /** The assignee — always the signer of the request. */
    @JsonProperty("user_public_key")
    private String userPublicKey;

    /** Bytes charged to this identity (encrypted, wrapped sizes). */
    @JsonProperty("total_bytes_used")
    private Long totalBytesUsed;

    /** Attachments currently charged. */
    @JsonProperty("file_count")
    private Integer fileCount;

    /** The ceiling {@code total_bytes_used} may not exceed. */
    @JsonProperty("quota_limit_bytes")
    private Long quotaLimitBytes;

    /** {@code max(0, quota_limit_bytes - total_bytes_used)} — what one more upload may weigh. */
    @JsonProperty("available_bytes")
    private Long availableBytes;

    /** Server clock (epoch millis) of the last change to this row; {@code null} when uninitialized. */
    @JsonProperty("last_updated")
    private Long lastUpdated;

    /** Whether the server refuses uploads that would exceed the limit. */
    @JsonProperty("enforced")
    private Boolean enforced;

    /** Whether a row exists for this identity (false = never uploaded; figures are the defaults). */
    @JsonProperty("initialized")
    private Boolean initialized;

    /** Server clock (epoch millis) when this status was read. */
    @JsonProperty("server_time")
    private Long serverTime;
}
