package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.takamaka.messages.call.CallStrictStringDeserializer;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Plaintext of a channel box (spec §5.2 {@code body}, §6.4 handover, §10.1 conv_seed). Not signed itself: it
 * travels inside the AEAD box of a signed {@code channel} object.
 *
 * <p>Encoding (this implementation's resolution of ambiguity A-6 of the C182 status report): the plaintext is
 * the UTF-8 (ASCII) of the JCS canonical form of this object; the handover fields of §6.4 sit at the top level;
 * {@code conv_seed} (hex) is added when the recipient is an era-0 {@code inv} leg; an "empty" body is {@code {}}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallChannelBodyBean {

    /**
     * Body kind: the handovers {@code epoch} | {@code broadcast}, the catch-up request {@code catchup}
     * ({@link io.takamaka.messages.call.CallConstants#H_CATCHUP}) and, from protocol 1.1, the media notice
     * {@code media} ({@link io.takamaka.messages.call.CallConstants#H_MEDIA}); absent when the body carries none.
     */
    @JsonProperty("h")
    private String h;
    @JsonProperty("epoch")
    private Long epoch;
    /** Era hash. */
    @JsonProperty("era")
    private String era;
    /** Sorted ann_hash list of the roster (epoch handover). */
    @JsonProperty("roster")
    private List<String> roster;
    /** epoch_secret_e, hex (epoch handover). */
    @JsonProperty("secret")
    private String secret;
    @JsonProperty("chain_idx")
    private Long chainIdx;
    /**
     * [0.2] Epoch handover only: the committer's milliseconds elapsed since the last fresh commit it applied (§6.4,
     * shell oracle finding O-11). The newcomer starts its budget window at {@code now − since}.
     */
    @JsonProperty("since")
    private Long since;
    /**
     * [0.2] (2026-10-08) Epoch handover only, required: the SIGNED {@code ts} (ms since the Unix epoch) of the fresh
     * commit that produced the handed-over secret (§6.4 step 3). The adopting leg measures its §6.2 budget window from
     * it — a committer timestamp — never from its own clock minus {@code since}, so a late handover cannot make it
     * refuse the next commit.
     */
    @JsonProperty("prev_ts")
    private Long prevTs;
    /**
     * [0.3] S-1 (2026-10-13): the catch-up REQUEST carries a fresh random {@code req} (16 bytes hex); the catch-up
     * handover echoes it, and the requester accepts a catch-up handover only with the {@code req} of its outstanding
     * request. Absent on a join handover (gated by the phase and the sender instead).
     */
    @JsonProperty("req")
    private String req;
    /** broadcast_e, hex (broadcast handover). */
    @JsonProperty("key")
    private String key;
    /** conv_seed, hex (spec §10.1). */
    @JsonProperty("conv_seed")
    private String convSeed;
    /**
     * Audio-only calls S0 (design AUDIO_ONLY_CALLS_DESIGN_2026-11-19 §5/§6, ruling R-1; spec §5.3; protocol 1.1): the
     * field of the media notice {@code {"h":"media","media":"video"|"audio"}} — the SENDER's own declared media
     * intent ({@link io.takamaka.messages.call.CallConstants#MEDIA_VIDEO} /
     * {@link io.takamaka.messages.call.CallConstants#MEDIA_AUDIO}).
     *
     * <p>LABELLING ONLY: a receiver uses it to say why no video arrives («by choice»); it never gates rendering,
     * decryption or any key-plane step. Speakers only: a listener never sends it and never receives one, and a
     * receiver clears a leg's declaration on a role change. It is a string so that other values can come later: this
     * bean does NOT refuse an unknown value, and a receiver treats one as «unknown cause». No counter: the channel's
     * own {@code seq} orders and de-duplicates the notices.
     */
    @JsonProperty("media")
    @JsonDeserialize(using = CallStrictStringDeserializer.class)
    private String media;
}
