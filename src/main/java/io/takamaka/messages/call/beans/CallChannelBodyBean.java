package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    /** Handover kind: {@code epoch} | {@code broadcast}; absent when the body carries no handover. */
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
    /** broadcast_e, hex (broadcast handover). */
    @JsonProperty("key")
    private String key;
    /** conv_seed, hex (spec §10.1). */
    @JsonProperty("conv_seed")
    private String convSeed;
}
