package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * [0.4] The ad-hoc MEET NOTICE to rschat (spec §7.11, ruling M9 of 2026-11-03): the same shape and checks as a ring
 * ({@link CallRingBean}) under its own type {@code meetnotice} — signed by the owner or acting owner with rschat's
 * nonce, {@code mode} always {@code conference}, {@code to} ≤ 100 — charged as ring units but NEVER ringing and never
 * occupying the callee's receive slots: rschat delivers {@code CALL_MEET} live or as a silent push, stores no row. The
 * recipient fetches and verifies the records before showing it (N7) and learns its role from the verified era, never
 * from the notice. Group meets use the group's signed chat message instead; rings (§7.1) remain the speakers' path.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallMeetNoticeBean extends CallSignedObject {

    @JsonProperty("call")
    private String call;
    @JsonProperty("svc")
    private String svc;
    @JsonProperty("to")
    private List<String> to;
    /** Always {@code conference}. */
    @JsonProperty("mode")
    private String mode;

    public CallMeetNoticeBean() {
        super(CallConstants.T_MEETNOTICE);
    }
}
