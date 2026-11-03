package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The limits of the manifest (spec §8.2, §8.6). The spec gives the values but not the key names (except
 * {@code inv_max}, §4.1); the names below are this implementation's proposal (ambiguity A-11 of the C182 status
 * report). [0.4] (M1, 2026-11-03) {@code listeners_max} is served (50 during the staging, 200 at most);
 * {@code alist_max} and {@code ring_to_max} are new; see {@link #meetValues(int)}.
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
    /** [0.4] §8.2: audience list size (named listeners), 200. */
    @JsonProperty("alist_max")
    private Integer alistMax;
    /** [0.4] §8.2 (M3): rschat's bound on a ring's {@code to}, informative, 25. */
    @JsonProperty("ring_to_max")
    private Integer ringToMax;
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
        return new CallLimitsBean(100, null, null, null, 10, 60, 10, 20, 100, 60_000L, 7L * 24 * 3600_000L);
    }

    /**
     * [0.4] (M1, M3) The initial values plus the meet keys of spec §8.2/§8.6: {@code listeners_max} as served (the
     * staging value 50 by default; the manifest ceiling is 200), {@code alist_max} 200, {@code ring_to_max} 25.
     */
    public static CallLimitsBean meetValues(int listenersServed) {
        CallLimitsBean l = initialValues();
        l.setListenersMax(listenersServed);
        l.setAlistMax(io.takamaka.messages.call.CallConstants.ALIST_MAX);
        l.setRingToMax(io.takamaka.messages.call.rschat.CallRschat.RING_TO_MAX);
        return l;
    }
}
