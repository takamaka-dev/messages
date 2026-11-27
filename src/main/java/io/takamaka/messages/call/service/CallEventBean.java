package io.takamaka.messages.call.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.beans.CallSignedObject;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One element of a leg's event stream (the {@code announce} route, spec §8.5 fan-out). {@code e} names the event and
 * says which one other field is set:
 *
 * <table>
 * <caption>Events</caption>
 * <tr><th>{@code e}</th><th>Field</th><th>Meaning</th></tr>
 * <tr><td>{@value #JOINED}</td><td>{@code joined}</td><td>first element of a stream: chain, announcements, grant</td></tr>
 * <tr><td>{@value #OK}</td><td>—</td><td>a re-announcement accepted while the leg's stream is live on this
 * connection; this stream completes, the live one continues</td></tr>
 * <tr><td>{@value #ERROR}</td><td>{@code error}</td><td>the announcement was refused; the stream completes</td></tr>
 * <tr><td>{@value #ANNOUNCE}</td><td>{@code ann}</td><td>a sequenced announcement (join or re-announcement)</td></tr>
 * <tr><td>{@value #ERA}, {@value #COMMIT}, {@value #CHANNEL}, {@value #GOODBYE}, {@value #DECLINE}, {@value #MUTE},
 * {@value #UNMUTE}</td><td>{@code obj}</td><td>the signed object verbatim (verify it yourself)</td></tr>
 * <tr><td>{@value #TEXT}</td><td>{@code text}</td><td>an in-call text object (§10.2) of another leg, verbatim</td></tr>
 * <tr><td>{@value #PRESENCE}</td><td>{@code presence}</td><td>the presence list after a change (absences coalesced per
 * {@code absence_window})</td></tr>
 * <tr><td>{@value #REPLACED}</td><td>—</td><td>another stream took over this leg (re-attach); this one completes</td></tr>
 * <tr><td>{@value #GONE}</td><td>—</td><td>this leg was excluded by a fresh commit or its era; the stream completes</td></tr>
 * <tr><td>{@value #END}</td><td>—</td><td>the call ended (§7.7); the service dropped everything; the stream completes</td></tr>
 * <tr><td>{@value #BKEY}</td><td>{@code obj}</td><td>[0.4] the committer's {@code bkey} of an epoch with only this
 * listener's box (§6.6)</td></tr>
 * <tr><td>{@value #HAND}</td><td>{@code obj}</td><td>[0.4] a listener's signed {@code hand} (§7.8), owner + moderators
 * only; replayed for the open hands when an owner / moderator leg joins</td></tr>
 * <tr><td>{@value #DROPPED}</td><td>—</td><td>[0.4] this listener leg was dropped (§7.9); the stream completes</td></tr>
 * </table>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallEventBean {

    public static final String JOINED = "joined";
    public static final String OK = "ok";
    public static final String ERROR = "error";
    public static final String ANNOUNCE = "announce";
    public static final String ERA = "era";
    public static final String COMMIT = "commit";
    public static final String CHANNEL = "channel";
    public static final String GOODBYE = "goodbye";
    public static final String DECLINE = "decline";
    public static final String MUTE = "mute";
    public static final String UNMUTE = "unmute";
    public static final String PRESENCE = "presence";
    public static final String TEXT = "text";
    public static final String REPLACED = "replaced";
    public static final String GONE = "gone";
    public static final String END = "end";
    /** [0.4] §6.6: a {@code bkey} with ONLY this listener's box (the header is what the committer signed). */
    public static final String BKEY = "bkey";
    /** [0.4] §7.8: a raised / lowered hand (the signed {@code hand} object), to the owner's and the moderators' legs. */
    public static final String HAND = "hand";
    /** [0.4] §7.9: this listener leg was dropped by the owner / a moderator; the stream completes. */
    public static final String DROPPED = "dropped";

    @JsonProperty("e")
    private String e;
    @JsonProperty("obj")
    private CallSignedObject obj;
    @JsonProperty("ann")
    private CallSequencedAnnouncementBean ann;
    @JsonProperty("joined")
    private CallJoinedBean joined;
    @JsonProperty("presence")
    private List<CallPresenceBean> presence;
    @JsonProperty("error")
    private CallErrorBean error;
    @JsonProperty("text")
    private CallTextBean text;

    public static CallEventBean of(String e) {
        CallEventBean ev = new CallEventBean();
        ev.setE(e);
        return ev;
    }

    public static CallEventBean object(String e, CallSignedObject obj) {
        CallEventBean ev = of(e);
        ev.setObj(obj);
        return ev;
    }

    public static CallEventBean error(CallErrorBean error) {
        CallEventBean ev = of(ERROR);
        ev.setError(error);
        return ev;
    }
}
