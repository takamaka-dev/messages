package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * [0.4] Remove a LISTENER leg (spec §7.9, ruling M4): signed by the owner, the acting owner (§4.4) or a moderator
 * ({@code mods}), nonce-bound. The service marks the leg gone and removes its relay participant; no key change — the
 * next tick's {@code bkey} omits it. A speaker is removed by an era, never by a drop.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallDropBean extends CallSignedObject {

    @JsonProperty("call")
    private String call;
    /** The listener leg to drop (8 bytes hex). */
    @JsonProperty("leg")
    private String leg;

    public CallDropBean() {
        super(CallConstants.T_DROP);
    }
}
