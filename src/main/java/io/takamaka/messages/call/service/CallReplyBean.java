package io.takamaka.messages.call.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallError;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The answer of every request-response route of the call service. {@code ok} is always present; on refusal
 * {@code error} carries the §11 code and every other field is absent. On success only the fields of that route are
 * set:
 *
 * <ul>
 * <li>{@code nonce}: {@code n} (hex, spec §8.1) and {@code aud} (the audience it is bound to, {@code svc:<id>});</li>
 * <li>{@code create}: {@code call} = call_id, {@code era} = era_hash(0);</li>
 * <li>{@code era}: {@code era} = the accepted era_hash(n);</li>
 * <li>{@code channel}: {@code delivered} = whether the {@code to} leg had a live stream (an undelivered channel open is
 * dropped, not queued: the service stores nothing; catch-up is the handover of §6.4);</li>
 * <li>{@code presence}: {@code presence} = the per-leg states of §7.2;</li>
 * <li>rschat {@code callnonce} (C182 build 3): {@code n} and {@code aud} ({@code rschat:<net>});</li>
 * <li>rschat {@code callring}: nothing (never whether a callee was live: the caller learns nothing about presence);</li>
 * <li>rschat {@code calllookup}: {@code reg} = the registration record verbatim, or the error {@code not_registered};</li>
 * <li>{@code records} [0.2]: {@code create} = the creation record, {@code eras} = era records 1..n in order
 * ({@code []} in era 0) — verify the chain yourself (owner signatures, {@code prev} links) and compute the current
 * era_hash from the last record.</li>
 * </ul>
 *
 * Not signed: the service's answers are bound to the request on the same RSocket stream; the objects that need the
 * service's authority (grant, manifest) are signed by the service key.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallReplyBean {

    @JsonProperty("ok")
    private Boolean ok;
    @JsonProperty("error")
    private CallErrorBean error;
    @JsonProperty("n")
    private String n;
    @JsonProperty("aud")
    private String aud;
    @JsonProperty("call")
    private String call;
    @JsonProperty("era")
    private String era;
    @JsonProperty("delivered")
    private Boolean delivered;
    @JsonProperty("presence")
    private List<CallPresenceBean> presence;
    /** {@code records}: the creation record (era 0). */
    @JsonProperty("create")
    private io.takamaka.messages.call.beans.CallCreateBean create;
    /** {@code records}: era records 1..n, in order. */
    @JsonProperty("eras")
    private List<io.takamaka.messages.call.beans.CallEraBean> eras;
    /**
     * rschat {@code calllookup} (spec §8.3, C182 build 3): the identity's registration record VERBATIM — the stored
     * canonical JSON of its signed {@code registeruser} request (identity, RSA-4096 encryption key, signature). The
     * service verifies it itself (chat signature under the looked-up identity, Design §4.4) before trusting it.
     */
    @JsonProperty("reg")
    private String reg;

    public static CallReplyBean ok() {
        CallReplyBean r = new CallReplyBean();
        r.setOk(true);
        return r;
    }

    public static CallReplyBean refused(CallErrorBean error) {
        CallReplyBean r = new CallReplyBean();
        r.setOk(false);
        r.setError(error);
        return r;
    }

    public static CallReplyBean refused(CallError error) {
        return refused(CallErrorBean.of(error));
    }
}
