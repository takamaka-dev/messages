package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Ring request to rschat (spec §7.1); the nonce is rschat's.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallRingBean extends CallSignedObject {

    @JsonProperty("call")
    private String call;
    @JsonProperty("svc")
    private String svc;
    @JsonProperty("to")
    private List<String> to;
    @JsonProperty("mode")
    private String mode;

    public CallRingBean() {
        super(CallConstants.T_RING);
    }
}
