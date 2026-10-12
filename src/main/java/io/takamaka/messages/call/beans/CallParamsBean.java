package io.takamaka.messages.call.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    /**
     * [0.3] (C-27, 2026-10-12) OPTIONAL: the ceiling of the committer's ADAPTIVE rotation (§6.5). Present and above
     * {@code rotation}: the committer's own {@code rotation} timer doubles after every quiet rotation (no join, leave,
     * era, handover, catch-up, presence change or refusal since the previous fresh commit) up to
     * {@code min(rotation_max, backstop)} and collapses back to {@code rotation} on any of those events. Absent (the
     * draft 0.2 initial values, every vector): a fixed cadence, as before. Receivers need nothing: a stretched commit is
     * always at least {@code rotation ≥ refuse_below} after the previous one.
     */
    @JsonProperty("rotation_max")
    private Long rotationMax;

    /** The ten draft 0.2 fields (the signed objects of the vectors carry exactly these); {@code rotation_max} absent. */
    public CallParamsBean(Long grace, Long rotation, Long commitDelay, Long refuseBelow, Long absenceWindow,
            Long committerTimeout, Long retention, Long backstop, Integer cap, Integer devicesPerIdentity) {
        this.grace = grace;
        this.rotation = rotation;
        this.commitDelay = commitDelay;
        this.refuseBelow = refuseBelow;
        this.absenceWindow = absenceWindow;
        this.committerTimeout = committerTimeout;
        this.retention = retention;
        this.backstop = backstop;
        this.cap = cap;
        this.devicesPerIdentity = devicesPerIdentity;
    }

    /** The initial values of spec §4.1. */
    public static CallParamsBean initialValues() {
        return new CallParamsBean(60_000L, 60_000L, 66_000L, 54_000L, 5_000L, 2_000L, 10_000L, 300_000L, 50, 3);
    }
}
