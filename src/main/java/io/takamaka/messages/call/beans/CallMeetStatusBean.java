package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * [0.4] Meet status (spec §7.10, ruling M9), both directions under the same {@code t}:
 * <ul>
 * <li>the REQUEST — signed by any identity named in {@code inv} or {@code alist} of any era, nonce-bound, audience
 * {@code svc:<id>}, carrying {@code call} only;</li>
 * <li>the ANSWER — signed by the service key, audience {@code id:<identity>} of the asker, carrying {@code call},
 * {@code live}, {@code speakers}, {@code listeners}, {@code since} (the creation {@code ts}). A call the service
 * no longer holds answers {@code live:false} with zero counts.</li>
 * </ul>
 * Clients render «live, n speakers, m listeners» / «ended» from the answer, never from a notice's age.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallMeetStatusBean extends CallSignedObject {

    @JsonProperty("call")
    private String call;
    /** Answer only. */
    @JsonProperty("live")
    private Boolean live;
    /** Answer only: present speaker legs. */
    @JsonProperty("speakers")
    private Integer speakers;
    /** Answer only: present listener legs. */
    @JsonProperty("listeners")
    private Integer listeners;
    /** Answer only: the creation record's {@code ts}. */
    @JsonProperty("since")
    private Long since;

    public CallMeetStatusBean() {
        super(CallConstants.T_MEETSTATUS);
    }

    /** True for the service-signed answer (it carries {@code live}); false for a request. */
    @JsonIgnore
    public boolean isAnswer() {
        return live != null;
    }
}
