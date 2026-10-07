package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The limits of the manifest (spec §8.2, §8.6). The spec gives the values but not the key names (except
 * {@code inv_max}, §4.1); the names below are this implementation's proposal (ambiguity A-11 of the C182 status
 * report). {@code listeners_max} is omitted until test (f) fixes it (D25).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallLimitsBean {

    @JsonProperty("inv_max")
    private Integer invMax;
    @JsonProperty("listeners_max")
    private Integer listenersMax;
    @JsonProperty("calls_per_hour")
    private Integer callsPerHour;
    @JsonProperty("eras_per_hour")
    private Integer erasPerHour;
    @JsonProperty("text_per_minute")
    private Integer textPerMinute;
    @JsonProperty("lookups_per_minute_inviter")
    private Integer lookupsPerMinuteInviter;
    @JsonProperty("lookups_per_minute_instance")
    private Integer lookupsPerMinuteInstance;
    @JsonProperty("negative_cache_ms")
    private Long negativeCacheMs;
    @JsonProperty("positive_cache_ms")
    private Long positiveCacheMs;

    /** The initial values of spec §8.2. */
    public static CallLimitsBean initialValues() {
        return new CallLimitsBean(100, null, 10, 60, 10, 20, 100, 60_000L, 7L * 24 * 3600_000L);
    }
}
