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
 * One blob's outcome inside a {@link WithdrawAttachmentResponseBean} (DR-037).
 *
 * <p>{@code status} is one of the {@code STATUS_*} constants: {@code withdrawn} (blob purged, quota
 * credited by {@code freed_bytes}), {@code already_withdrawn} (idempotent repeat, nothing changed),
 * {@code not_owned} (the row belongs to another identity; nothing touched, nothing revealed beyond
 * that), {@code not_found} (no such row), {@code failed} (the purge threw; the row is unchanged).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WithdrawAttachmentOutcomeBean {

    public static final String STATUS_WITHDRAWN = "withdrawn";
    public static final String STATUS_ALREADY_WITHDRAWN = "already_withdrawn";
    public static final String STATUS_NOT_OWNED = "not_owned";
    public static final String STATUS_NOT_FOUND = "not_found";
    public static final String STATUS_FAILED = "failed";

    @JsonProperty("efh")
    private String encryptedFileHash;

    @JsonProperty("status")
    private String status;

    /** Bytes credited back to the owner's quota (the row's stored encrypted size); 0 unless withdrawn. */
    @JsonProperty("freed_bytes")
    private Long freedBytes;
}
