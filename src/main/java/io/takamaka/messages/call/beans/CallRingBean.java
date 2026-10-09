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
 * <p>[0.2] (C-16 answered elsewhere, 2026-10-09): the same object with {@code k = "answered"} is the ANSWERED NOTICE —
 * signed by the callee, {@code to = [f]} — that the device which accepted a ring sends so that rschat tells the other
 * devices of its identity to stop ringing (they decline {@code elsewhere} to the service, §7.6). {@code k} absent =
 * a ring; absent from the JSON ({@code NON_NULL}), so every ring and vector is unchanged.
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
    /** [0.2] §7.1: absent (a ring) or {@code answered} (the answered notice). */
    @JsonProperty("k")
    private String k;

    public CallRingBean() {
        super(CallConstants.T_RING);
    }
}
