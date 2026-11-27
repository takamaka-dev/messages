package io.takamaka.messages.call.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.takamaka.messages.call.CallError;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A typed error result of spec §11 as it travels from the call service: the code, plus the two qualifiers the table
 * names — the limit name of {@code rate_limited} and the keys of {@code not_registered}. Never free text (§8.7 rule
 * 4): nothing a client sent is echoed except the identity keys {@code not_registered} must name, which the client
 * itself put in the record.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallErrorBean {

    /** The §11 code, e.g. {@code bad_nonce}. */
    @JsonProperty("err")
    private String err;
    /** {@code rate_limited} only: the limit name (a key of the manifest limits, or {@code nonce_per_minute}). */
    @JsonProperty("limit")
    private String limit;
    /** {@code not_registered} only: the identities that are not (or could not be confirmed) registered. */
    @JsonProperty("keys")
    private List<String> keys;

    public static CallErrorBean of(CallError error) {
        return new CallErrorBean(error.code(), null, null);
    }

    public static CallErrorBean rateLimited(String limit) {
        return new CallErrorBean(CallError.RATE_LIMITED.code(), limit, null);
    }

    public static CallErrorBean notRegistered(List<String> keys) {
        return new CallErrorBean(CallError.NOT_REGISTERED.code(), null, List.copyOf(keys));
    }
}
