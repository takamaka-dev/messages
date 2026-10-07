package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Channel open, the first message of a pairwise hybrid channel (spec §5.2). Signed by the opener's identity over
 * the whole transcript.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallChannelBean extends CallSignedObject {

    @JsonProperty("call")
    private String call;
    /** The opener's leg_id. */
    @JsonProperty("from")
    private String from;
    /** The recipient's leg_id. */
    @JsonProperty("to")
    private String to;
    /** ann_hash of the opener (A). */
    @JsonProperty("annf")
    private String annf;
    /** ann_hash of the recipient (B). */
    @JsonProperty("annt")
    private String annt;
    /** ML-KEM-768 ciphertext, 1088 bytes hex. */
    @JsonProperty("ct")
    private String ct;
    @JsonProperty("suite")
    private String suite;
    /** AEAD box of the first body under k_msg_0 of the A&gt;B chain: nonce ‖ ciphertext ‖ tag, hex. */
    @JsonProperty("box")
    private String box;

    public CallChannelBean() {
        super(CallConstants.T_CHANNEL);
    }
}
