package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Moderator mute / unmute request (spec §7.5); {@code t} is {@code mute} or {@code unmute}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallMuteBean extends CallSignedObject {

    @JsonProperty("call")
    private String call;
    @JsonProperty("leg")
    private String leg;

    public CallMuteBean() {
        super(CallConstants.T_MUTE);
    }

    public static CallMuteBean unmute() {
        CallMuteBean b = new CallMuteBean();
        b.setT(CallConstants.T_UNMUTE);
        return b;
    }
}
