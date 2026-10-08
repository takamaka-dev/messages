package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * [0.2] Read-only records request (spec §7.3 step 0, §3.3 {@code records}): signed by an invited identity, audience
 * {@code svc:<id>}, with a service nonce. The service answers the creation record and every era record, before any
 * announcement, to an identity that is in the invitation list or the audience list of ANY era of the call (else
 * {@code not_invited}). This is how a callee verifies the owner's record before the call is shown (N7) and learns the
 * current {@code era_hash} to announce under (shell oracle finding O-1).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallRecordsBean extends CallSignedObject {

    @JsonProperty("call")
    private String call;

    public CallRecordsBean() {
        super(CallConstants.T_RECORDS);
    }
}
