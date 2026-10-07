package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Announcement of one leg (spec §4.3): the per-call ephemeral hybrid public keys, bound to the era hash.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallAnnounceBean extends CallSignedObject {

    @JsonProperty("call")
    private String call;
    /** The era HASH this announcement is bound to. */
    @JsonProperty("era")
    private String era;
    /** leg_id, 8 bytes hex. */
    @JsonProperty("leg")
    private String leg;
    /** {@code speaker} | {@code listener}. */
    @JsonProperty("role")
    private String role;
    /** X25519 public key, 32 bytes hex. */
    @JsonProperty("x25519")
    private String x25519;
    /** ML-KEM-768 encapsulation key, 1184 bytes hex. */
    @JsonProperty("mlkem")
    private String mlkem;
    @JsonProperty("suite")
    private String suite;
    /** {@code basic} | {@code basic_hq} (R17). */
    @JsonProperty("cap")
    private String cap;

    public CallAnnounceBean() {
        super(CallConstants.T_ANNOUNCE);
    }
}
