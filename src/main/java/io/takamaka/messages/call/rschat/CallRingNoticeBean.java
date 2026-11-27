package io.takamaka.messages.call.rschat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What a callee receives for a ring (spec §7.1), live on its notification stream ({@code ring} of a
 * {@code CALL_RING} notification) and, field for field, in the FCM push data: {@code call} (call_id), {@code svc}
 * (service id), {@code f} (the caller) and {@code mode} — and nothing else. Not signed and not trusted: the callee
 * shows the caller as verified only after fetching the creation record from the PINNED service named by its own
 * configuration (never by this notice, N14) and verifying it (N7).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallRingNoticeBean {

    /** call_id, 32 bytes lowercase hex. */
    @JsonProperty("call")
    private String call;
    /** The service id of the creation record ({@code svc}). */
    @JsonProperty("svc")
    private String svc;
    /** The caller's identity (the verified signer of the ring). */
    @JsonProperty("f")
    private String f;
    /** {@code standard} or {@code conference}. */
    @JsonProperty("mode")
    private String mode;
}
