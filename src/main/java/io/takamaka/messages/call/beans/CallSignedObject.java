package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The common envelope of every signed call object (spec §3.1): {@code v}, {@code t}, {@code f}, {@code aud},
 * {@code ts}, {@code n}, {@code sg}.
 *
 * <p>No {@code @JsonPropertyOrder}: the signed form is RFC 8785 JCS, which sorts keys (spec §2.3). Nulls are
 * omitted; an empty list is emitted as {@code []}. The concrete class is chosen by {@code t} when parsing
 * ({@link io.takamaka.messages.call.CallJson#parse(String)}).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "t", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = CallCreateBean.class, name = "create"),
    @JsonSubTypes.Type(value = CallEraBean.class, name = "era"),
    @JsonSubTypes.Type(value = CallAnnounceBean.class, name = "announce"),
    @JsonSubTypes.Type(value = CallChannelBean.class, name = "channel"),
    @JsonSubTypes.Type(value = CallCommitBean.class, name = "commit"),
    @JsonSubTypes.Type(value = CallGoodbyeBean.class, name = "goodbye"),
    @JsonSubTypes.Type(value = CallDeclineBean.class, name = "decline"),
    @JsonSubTypes.Type(value = CallRingBean.class, name = "ring"),
    @JsonSubTypes.Type(value = CallGrantBean.class, name = "grant"),
    @JsonSubTypes.Type(value = CallManifestBean.class, name = "manifest"),
    @JsonSubTypes.Type(value = CallLookupBean.class, name = "lookup"),
    @JsonSubTypes.Type(value = CallMuteBean.class, names = {"mute", "unmute"}),
    @JsonSubTypes.Type(value = CallRecordsBean.class, name = "records")
})
public abstract class CallSignedObject {

    /** Protocol major, {@code 1}. */
    @JsonProperty("v")
    private Integer v;

    /** Object type (spec §3.3); part of the signature context. */
    @JsonProperty("t")
    private String t;

    /** The signer's identity (registration string form), always inside the payload (CK-2). */
    @JsonProperty("f")
    private String f;

    /** Audience: {@code svc:<id>}, {@code rschat:<net>}, {@code leg:<leg_id>} (CS-1), or {@code any}. */
    @JsonProperty("aud")
    private String aud;

    /** Signer's time, ms since the Unix epoch. */
    @JsonProperty("ts")
    private Long ts;

    /** Nonce, hex, on the types that require one (spec §8.1). */
    @JsonProperty("n")
    private String n;

    /** Ed25519 signature, 64 bytes, lowercase hex. Excluded from the canonical form. */
    @JsonProperty("sg")
    private String sg;

    protected CallSignedObject(String t) {
        this.v = io.takamaka.messages.call.CallConstants.VERSION;
        this.t = t;
    }
}
