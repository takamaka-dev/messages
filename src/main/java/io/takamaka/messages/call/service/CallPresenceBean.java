package io.takamaka.messages.call.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The service's presence claim about one leg (spec §7.2). Per leg; the client groups legs by identity from its own
 * verified announcements (the service never repeats identities here). {@code state} is a server claim except
 * {@code leaving} after a verified goodbye, which the client also holds as a signed fact.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallPresenceBean {

    public static final String PRESENT = "present";
    public static final String DISCONNECTED = "disconnected";
    public static final String LEAVING = "leaving";
    public static final String GONE = "gone";

    /** leg_id, hex. */
    @JsonProperty("leg")
    private String leg;
    @JsonProperty("leg_index")
    private Integer legIndex;
    /** {@code speaker} | {@code listener}. */
    @JsonProperty("role")
    private String role;
    /** {@code present} | {@code disconnected} | {@code leaving} | {@code gone}. */
    @JsonProperty("state")
    private String state;
}
