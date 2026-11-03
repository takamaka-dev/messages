package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * [0.4] Raise / lower hand (spec §7.8, ruling M4): a service request from a PRESENT listener leg, relayed to the
 * owner's (or acting owner's) and the moderators' legs only. Nonce-bound. {@code up:true} raises, {@code false}
 * lowers; 6 per minute per leg; the service's queue holds 25.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallHandBean extends CallSignedObject {

    @JsonProperty("call")
    private String call;
    /** The raising listener's own leg_id (8 bytes hex). */
    @JsonProperty("leg")
    private String leg;
    @JsonProperty("up")
    private Boolean up;

    public CallHandBean() {
        super(CallConstants.T_HAND);
    }
}
