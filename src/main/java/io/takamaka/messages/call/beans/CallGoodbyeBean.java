package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Signed leave of one leg (spec §7.4). Final for that leg; carries the epoch so a replay is recognisable.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallGoodbyeBean extends CallSignedObject {

    @JsonProperty("call")
    private String call;
    @JsonProperty("leg")
    private String leg;
    @JsonProperty("epoch")
    private Long epoch;

    public CallGoodbyeBean() {
        super(CallConstants.T_GOODBYE);
    }
}
