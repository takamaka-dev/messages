package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Grant, service &rarr; leg (spec §8.4), signed by the service key.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallGrantBean extends CallSignedObject {

    /** ts + 60000. */
    @JsonProperty("exp")
    private Long exp;
    @JsonProperty("call")
    private String call;
    @JsonProperty("leg_index")
    private Integer legIndex;
    @JsonProperty("relay")
    private CallRelayBean relay;
    @JsonProperty("layers")
    private List<String> layers;
    @JsonProperty("caps")
    private CallCapsBean caps;
    /** {@code free} | {@code premium}. */
    @JsonProperty("tier")
    private String tier;
    @JsonProperty("params")
    private CallParamsBean params;

    public CallGrantBean() {
        super(CallConstants.T_GRANT);
    }
}
