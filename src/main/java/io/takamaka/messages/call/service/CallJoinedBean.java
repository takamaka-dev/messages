package io.takamaka.messages.call.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.beans.CallCreateBean;
import io.takamaka.messages.call.beans.CallEraBean;
import io.takamaka.messages.call.beans.CallGrantBean;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The answer to an accepted announcement (spec §7.3 step 1): the creation record, every era record in order, the
 * current era's accepted announcements (sequenced), this leg's {@code leg_index} and join number, a fresh grant
 * (§8.4) and the presence list (§7.2). The device verifies the record chain ({@code prev} links, owner signatures) and
 * every announcement itself (§7.3 step 2); nothing here is trusted because the service sent it, except the presence
 * claims, which are claims.
 *
 * <p>{@code anns} lists, for a speaker, every non-gone speaker leg; for a listener, only the committer's leg
 * (R18: a listener opens one channel, to the committer).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallJoinedBean {

    @JsonProperty("create")
    private CallCreateBean create;
    /** Era records 1..n, in order; {@code []} when the call is still in era 0. */
    @JsonProperty("eras")
    private List<CallEraBean> eras;
    @JsonProperty("anns")
    private List<CallSequencedAnnouncementBean> anns;
    @JsonProperty("leg_index")
    private Integer legIndex;
    @JsonProperty("join")
    private Long join;
    /** The service's epoch counter (the last sequenced step or accepted commit); absent before the initial commit. */
    @JsonProperty("epoch")
    private Long epoch;
    @JsonProperty("grant")
    private CallGrantBean grant;
    @JsonProperty("presence")
    private List<CallPresenceBean> presence;
}
