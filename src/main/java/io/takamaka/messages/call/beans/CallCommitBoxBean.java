package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One box of a commit (spec §6.2): the commit secret for one roster leg under that channel's current k_msg.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallCommitBoxBean {

    /** Recipient leg_id. */
    @JsonProperty("to")
    private String to;
    /** nonce ‖ ciphertext ‖ tag, hex. */
    @JsonProperty("box")
    private String box;
}
