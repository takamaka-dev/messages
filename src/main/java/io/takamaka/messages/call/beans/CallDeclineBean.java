package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Decline / busy / answered elsewhere (spec §7.6).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallDeclineBean extends CallSignedObject {

    @JsonProperty("call")
    private String call;
    /** {@code declined} | {@code busy} | {@code elsewhere}. */
    @JsonProperty("reason")
    private String reason;

    public CallDeclineBean() {
        super(CallConstants.T_DECLINE);
    }
}
