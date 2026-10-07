package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Service manifest (spec §8.6), served on connect, signed by the service key; audience {@code any}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallManifestBean extends CallSignedObject {

    @JsonProperty("ver")
    private CallVersionRangeBean ver;
    @JsonProperty("suites")
    private List<String> suites;
    @JsonProperty("sframe")
    private List<String> sframe;
    @JsonProperty("params")
    private CallParamsBean params;
    @JsonProperty("limits")
    private CallLimitsBean limits;
    @JsonProperty("regions")
    private List<String> regions;

    public CallManifestBean() {
        super(CallConstants.T_MANIFEST);
    }
}
