package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallConstants;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Creation record, era 0 (spec §4.1). {@code call_id} and {@code era_hash(0)} are hashes of its canonical form
 * ({@link io.takamaka.messages.call.CallHashes}).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallCreateBean extends CallSignedObject {

    /** Expiry, ms; at most 4 h after {@code ts}. */
    @JsonProperty("exp")
    private Long exp;
    /** Network name (e.g. {@code prod}). */
    @JsonProperty("net")
    private String net;
    /** The call service id. */
    @JsonProperty("svc")
    private String svc;
    /** 32 random bytes, hex. */
    @JsonProperty("rand")
    private String rand;
    /** {@code standard} | {@code conference}. */
    @JsonProperty("mode")
    private String mode;
    /** Speakers: sorted, no duplicates, owner included. */
    @JsonProperty("inv")
    private List<String> inv;
    /** Conference only: {@code invited} | {@code open}. */
    @JsonProperty("apol")
    private String apol;
    /** Conference, apol = invited: the audience list. */
    @JsonProperty("alist")
    private List<String> alist;
    /** Conference, apol = open: H(invitation secret), hex. */
    @JsonProperty("asec")
    private String asec;
    /** Moderators; may be empty (emitted as {@code []}). */
    @JsonProperty("mods")
    private List<String> mods;
    /** H(conv_seed), hex (CS-5). */
    @JsonProperty("cseed")
    private String cseed;
    @JsonProperty("params")
    private CallParamsBean params;

    public CallCreateBean() {
        super(CallConstants.T_CREATE);
    }
}
