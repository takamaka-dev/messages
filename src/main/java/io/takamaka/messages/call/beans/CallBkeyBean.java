package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * [0.4] Broadcast-key distribution (spec §6.6, ruling M6 of 2026-11-03): one committer-signed object per epoch
 * carrying {@code broadcast_e} to every PRESENT listener, one AEAD box per listener under that listener's channel
 * chain at its current {@code k} (the same box shape as a commit's, {@link CallCommitBoxBean}). The HEADER — every
 * field but {@code boxes} and {@code sg} — is the AAD of every box. The service relays each box to its own leg,
 * accepts one {@code bkey} per epoch from the leg whose fresh commit it accepted, and counts it as one flow unit.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallBkeyBean extends CallSignedObject {

    @JsonProperty("call")
    private String call;
    /** The era HASH of the epoch's commit. */
    @JsonProperty("era")
    private String era;
    /** The epoch of the last accepted fresh commit (the one this key belongs to). */
    @JsonProperty("epoch")
    private Long epoch;
    /** One box per present listener leg, ordered by recipient {@code leg_id}. */
    @JsonProperty("boxes")
    private List<CallCommitBoxBean> boxes;

    public CallBkeyBean() {
        super(CallConstants.T_BKEY);
    }
}
