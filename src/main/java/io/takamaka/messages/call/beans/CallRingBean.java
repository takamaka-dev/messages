package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.takamaka.messages.call.CallConstants;
import io.takamaka.messages.call.CallStrictStringDeserializer;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Ring request to rschat (spec §7.1); the nonce is rschat's.
 *
 * <p>[0.2] (C-16 answered elsewhere, 2026-10-09): the same object with {@code k = "answered"} is the ANSWERED NOTICE —
 * signed by the callee, {@code to = [f]} — that the device which accepted a ring sends so that rschat tells the other
 * devices of its identity to stop ringing (they decline {@code elsewhere} to the service, §7.6). {@code k} absent =
 * a ring; absent from the JSON ({@code NON_NULL}), so every ring and vector is unchanged.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallRingBean extends CallSignedObject {

    @JsonProperty("call")
    private String call;
    @JsonProperty("svc")
    private String svc;
    @JsonProperty("to")
    private List<String> to;
    @JsonProperty("mode")
    private String mode;
    /**
     * [0.2] §7.1: absent (a ring) or {@code answered} (the answered notice); P2 (2026-11-05) also {@code cancelled}
     * (the caller's cancel of its unanswered ring).
     */
    @JsonProperty("k")
    private String k;
    /**
     * Audio-only calls S0 (design AUDIO_ONLY_CALLS_DESIGN_2026-11-19 §7, ruling R-3; protocol 1.1,
     * {@link CallConstants#PROTOCOL_VERSION}): the CALLER's own media intent at ring time. Optional; the ONLY allowed
     * value is {@code audio} ({@link CallConstants#RING_MEDIA_AUDIO}). Absent = a video ring, absent from the JSON
     * ({@code NON_NULL}), so a ring without it canonicalizes and signs byte-identically to a pre-S0 ring. Only a ring
     * (no {@code k}) may carry it: an {@code answered} or {@code cancelled} object with {@code media} is refused
     * ({@link io.takamaka.messages.call.rschat.CallRschat#checkRing}). It is not a call mode and, in a group call,
     * says nothing about the other members. Read it ONLY from the verified signed ring: no notice or push field
     * carries it.
     */
    @JsonProperty("media")
    @JsonDeserialize(using = CallStrictStringDeserializer.class)
    private String media;

    public CallRingBean() {
        super(CallConstants.T_RING);
    }
}
