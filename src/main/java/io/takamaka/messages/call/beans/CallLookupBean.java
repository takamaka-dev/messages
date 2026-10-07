package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Registration lookup, service &rarr; rschat (spec §8.3); one identity per request.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallLookupBean extends CallSignedObject {

    @JsonProperty("id")
    private String id;

    public CallLookupBean() {
        super(CallConstants.T_LOOKUP);
    }
}
