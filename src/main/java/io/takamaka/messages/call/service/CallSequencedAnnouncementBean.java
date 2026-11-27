package io.takamaka.messages.call.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.beans.CallAnnounceBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An announcement as the service sequenced it (spec §4.3, §6.4 step 1): the signed announcement verbatim, plus the
 * {@code leg_index} (16-bit, unique in the call, never reused) and the join sequence number the service assigned.
 * {@code kind} tells the receiver what to do with it:
 *
 * <ul>
 * <li>{@code join} — a new leg: every speaker applies the step (§6.4 step 2) when the role is speaker;</li>
 * <li>{@code reannounce} — the same leg, same keys, bound to the new era (§4.2): no step, replace the held
 * announcement for that leg once verified against the new era.</li>
 * </ul>
 *
 * A re-attach (§6.4 step 4: same leg, same era, back inside {@code grace}) is not fanned out at all: the roster keeps
 * the original announcement and its ann_hash.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallSequencedAnnouncementBean {

    public static final String KIND_JOIN = "join";
    public static final String KIND_REANNOUNCE = "reannounce";

    @JsonProperty("ann")
    private CallAnnounceBean ann;
    @JsonProperty("leg_index")
    private Integer legIndex;
    /** Join sequence number, from 0 in the call. */
    @JsonProperty("join")
    private Long join;
    @JsonProperty("kind")
    private String kind;
}
