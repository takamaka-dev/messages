package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The call parameters (spec §4.1 {@code params}, Design §7.8.3.6). Copied by the owner from the manifest so that
 * they are signed; all durations in milliseconds.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallParamsBean {

    @JsonProperty("grace")
    private Long grace;
    @JsonProperty("rotation")
    private Long rotation;
    @JsonProperty("commit_delay")
    private Long commitDelay;
    @JsonProperty("refuse_below")
    private Long refuseBelow;
    @JsonProperty("absence_window")
    private Long absenceWindow;
    @JsonProperty("committer_timeout")
    private Long committerTimeout;
    @JsonProperty("retention")
    private Long retention;
    @JsonProperty("backstop")
    private Long backstop;
    @JsonProperty("cap")
    private Integer cap;
    @JsonProperty("devices_per_identity")
    private Integer devicesPerIdentity;

    /** The initial values of spec §4.1. */
    public static CallParamsBean initialValues() {
        return new CallParamsBean(60_000L, 60_000L, 66_000L, 54_000L, 5_000L, 2_000L, 10_000L, 300_000L, 50, 3);
    }
}
