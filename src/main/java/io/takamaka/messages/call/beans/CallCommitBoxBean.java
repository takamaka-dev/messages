package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One box of a commit (spec §6.2, A-8 RULED 2026-10-07): the commit secret for one roster leg under that channel's
 * current k_msg; {@code k} is that channel's chain index (per channel, not per commit).
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
    /** The chain index of the channel to {@code to} that sealed this box. */
    @JsonProperty("k")
    private Long k;
    /** nonce ‖ ciphertext ‖ tag, hex. */
    @JsonProperty("box")
    private String box;
}
