package io.takamaka.messages.call.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The in-call text object of spec §10.2, exactly as the spec writes it:
 * {@code { "x":"text", "leg":…, "epoch":e, "ctr":c, "ct":"<AEAD(text_key_e, nonce = BE32(e)‖BE64(c), plaintext)>" }}.
 * Not signed: the service relays it from the connection that holds {@code leg}'s stream; the AEAD under
 * {@code text_key_e} of the sending leg (derived from {@code broadcast_e}) authenticates it to every member. The service
 * reads nothing of {@code ct}; it checks only the sender binding, the size (max 4 kB) and the rate limit (§8.2).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallTextBean {

    public static final String X_TEXT = "text";

    @JsonProperty("x")
    private String x;
    /** The sending leg_id (hex). */
    @JsonProperty("leg")
    private String leg;
    @JsonProperty("epoch")
    private Long epoch;
    @JsonProperty("ctr")
    private Long ctr;
    /** Hex of the AEAD output (ciphertext ‖ tag). */
    @JsonProperty("ct")
    private String ct;
}
