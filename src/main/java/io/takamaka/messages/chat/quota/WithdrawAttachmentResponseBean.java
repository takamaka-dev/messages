/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.messages.chat.quota;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Answer to {@code withdrawattachment} (DR-037): one outcome per requested hash, in request order,
 * plus the owner's quota AFTER the withdrawals — so a client can refresh its gauge from the same
 * round-trip. {@code error} is non-null only when the whole request was refused (bad signature,
 * clock outside the window, rate limit); the outcomes are then empty.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WithdrawAttachmentResponseBean {

    public static final String ERR_RATE_LIMITED = "rate_limited";
    public static final String ERR_INVALID_SIGNATURE = "invalid_signature";
    public static final String ERR_CLOCK_WINDOW = "clock_window";
    public static final String ERR_EMPTY = "empty";
    public static final String ERR_INTERNAL = "internal_error";

    @JsonProperty("outcomes")
    private List<WithdrawAttachmentOutcomeBean> outcomes;

    @JsonProperty("quota")
    private StorageQuotaStatusResponseBean quota;

    @JsonProperty("server_time")
    private Long serverTime;

    @JsonProperty("error")
    private String error;

    public static WithdrawAttachmentResponseBean rejected(String error) {
        return new WithdrawAttachmentResponseBean(List.of(), null, System.currentTimeMillis(), error);
    }
}
