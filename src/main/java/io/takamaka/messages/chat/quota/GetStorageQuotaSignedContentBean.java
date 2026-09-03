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
 * Signed payload of {@code getstoragequota}: the caller asks for its OWN storage-quota status.
 *
 * <p>There is nothing to select — the subject is always the signer ({@code from}) — so the
 * payload carries only the client clock, which binds the signature to a moment (the read is
 * nonce-free and idempotent, like {@code getuseroptions}). The signature is over the JCS
 * canonical form of this bean.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetStorageQuotaSignedContentBean {

    /** Client clock at signing time, epoch millis. */
    @JsonProperty("cts")
    private Long clientTimestamp;
}
