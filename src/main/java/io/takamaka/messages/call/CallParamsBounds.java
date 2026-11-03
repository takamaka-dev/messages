package io.takamaka.messages.call;

import io.takamaka.messages.call.beans.CallLimitsBean;
import io.takamaka.messages.call.beans.CallParamsBean;

/**
 * [0.3] S-4 (ruled 2026-10-29): the pinned client-side bounds on the service-served {@code params}. The service
 * decides the leave window and the rekey cadence; without bounds a hostile or misconfigured service could serve a
 * {@code backstop} of hours with {@code rotation} 0 (leavers keep the keys), {@code refuse_below} 0 (no R11 budget)
 * or a large {@code retention} (old keys stay alive). The bounds are RATIOS between the parameters plus absolute
 * caps, so the loop profile (rotation 6 s) and the default profile (rotation 60 s) both pass:
 * <ul>
 *   <li>{@code rotation} = 0 (adaptive) or ≥ {@value #ROTATION_MIN_MS} ms;</li>
 *   <li>{@code commit_delay} ∈ [max({@value #COMMIT_DELAY_MIN_MS}, rotation), {@value #COMMIT_DELAY_MAX_MS}];</li>
 *   <li>{@code refuse_below} ∈ [commit_delay / 2, commit_delay];</li>
 *   <li>{@code backstop} ∈ [commit_delay, {@value #BACKSTOP_MAX_MS}];</li>
 *   <li>{@code rotation_max} (optional, C-27) ∈ [rotation, backstop];</li>
 *   <li>{@code retention} ∈ [0, {@value #RETENTION_MAX_MS}];</li>
 *   <li>{@code committer_timeout} ∈ [{@value #COMMITTER_TIMEOUT_MIN_MS}, {@value #COMMITTER_TIMEOUT_MAX_MS}];</li>
 *   <li>{@code grace} ∈ [{@value #GRACE_MIN_MS}, {@value #GRACE_MAX_MS}]; {@code absence_window} ∈ [0, grace];</li>
 *   <li>{@code cap} ∈ [2, {@value #CAP_MAX}]; {@code devices_per_identity} ∈ [1, {@value #DEVICES_MAX}];</li>
 *   <li>[0.4] {@code owner_timeout} (optional) ∈ [committer_timeout, {@value #OWNER_TIMEOUT_MAX_MS}];
 *   {@code era_gate} (optional) ∈ [committer_timeout, min({@value #ERA_GATE_MAX_MS}, refuse_below)] — and the meet
 *   limits of {@link #limitsViolation}.</li>
 * </ul>
 * Checked by the owner before signing the creation record, by every joiner on the verified record (and on the
 * grant, which must equal the record), and at first contact on the signed manifest — so a bad profile is refused
 * {@link CallError#BAD_PARAMS} before any ring. All durations are milliseconds. Versioned with the spec: a change
 * here is a spec amendment, and the cross-platform vector file {@code call_params_bounds_v1.json} pins it.
 */
public final class CallParamsBounds {

    public static final long ROTATION_MIN_MS = 1_000;
    public static final long COMMIT_DELAY_MIN_MS = 3_000;
    public static final long COMMIT_DELAY_MAX_MS = 120_000;
    public static final long BACKSTOP_MAX_MS = 300_000;
    public static final long RETENTION_MAX_MS = 30_000;
    public static final long COMMITTER_TIMEOUT_MIN_MS = 500;
    public static final long COMMITTER_TIMEOUT_MAX_MS = 10_000;
    public static final long GRACE_MIN_MS = 1_000;
    public static final long GRACE_MAX_MS = 120_000;
    public static final int CAP_MAX = 100;
    public static final int DEVICES_MAX = 5;
    /** [0.4] (M4) {@code owner_timeout} ≤ 300 s (and ≥ {@code committer_timeout}). */
    public static final long OWNER_TIMEOUT_MAX_MS = 300_000;
    /** [0.4] (M5) {@code era_gate} ≤ 30 s (and ≥ {@code committer_timeout}, ≤ {@code refuse_below}). */
    public static final long ERA_GATE_MAX_MS = 30_000;
    /** [0.4] (M1) the manifest ceiling of {@code listeners_max}: 200 (served 50 until the staging gates pass). */
    public static final int LISTENERS_MAX_BOUND = 200;
    public static final int ALIST_MAX_BOUND = 1_000;
    public static final int TOTAL_LEGS_BOUND = 250;
    public static final int RING_TO_MAX_BOUND = 50;

    private CallParamsBounds() {
    }

    /** The first violated rule as a short reason, or {@code null} when every rule holds. Null fields are violations. */
    public static String violation(CallParamsBean p) {
        if (p == null) {
            return "params missing";
        }
        Long rotation = p.getRotation();
        Long cd = p.getCommitDelay();
        Long rb = p.getRefuseBelow();
        Long backstop = p.getBackstop();
        Long rotMax = p.getRotationMax();
        Long retention = p.getRetention();
        Long ct = p.getCommitterTimeout();
        Long grace = p.getGrace();
        Long absence = p.getAbsenceWindow();
        Integer cap = p.getCap();
        Integer dev = p.getDevicesPerIdentity();
        if (rotation == null || (rotation != 0 && rotation < ROTATION_MIN_MS)) {
            return "rotation must be 0 or >= " + ROTATION_MIN_MS + " ms";
        }
        if (cd == null || cd < Math.max(COMMIT_DELAY_MIN_MS, rotation) || cd > COMMIT_DELAY_MAX_MS) {
            return "commit_delay must be in [max(" + COMMIT_DELAY_MIN_MS + ", rotation), " + COMMIT_DELAY_MAX_MS + "] ms";
        }
        if (rb == null || rb < cd / 2 || rb > cd) {
            return "refuse_below must be in [commit_delay/2, commit_delay]";
        }
        if (backstop == null || backstop < cd || backstop > BACKSTOP_MAX_MS) {
            return "backstop must be in [commit_delay, " + BACKSTOP_MAX_MS + "] ms";
        }
        if (rotMax != null && (rotMax < rotation || rotMax > backstop)) {
            return "rotation_max must be in [rotation, backstop]";
        }
        if (retention == null || retention < 0 || retention > RETENTION_MAX_MS) {
            return "retention must be in [0, " + RETENTION_MAX_MS + "] ms";
        }
        if (ct == null || ct < COMMITTER_TIMEOUT_MIN_MS || ct > COMMITTER_TIMEOUT_MAX_MS) {
            return "committer_timeout must be in [" + COMMITTER_TIMEOUT_MIN_MS + ", " + COMMITTER_TIMEOUT_MAX_MS + "] ms";
        }
        if (grace == null || grace < GRACE_MIN_MS || grace > GRACE_MAX_MS) {
            return "grace must be in [" + GRACE_MIN_MS + ", " + GRACE_MAX_MS + "] ms";
        }
        if (absence == null || absence < 0 || absence > grace) {
            return "absence_window must be in [0, grace]";
        }
        if (cap == null || cap < 2 || cap > CAP_MAX) {
            return "cap must be in [2, " + CAP_MAX + "]";
        }
        if (dev == null || dev < 1 || dev > DEVICES_MAX) {
            return "devices_per_identity must be in [1, " + DEVICES_MAX + "]";
        }
        // [0.4] the meet keys (optional on the wire; bounded when present — spec §8.6)
        Long ot = p.getOwnerTimeout();
        if (ot != null && (ot < ct || ot > OWNER_TIMEOUT_MAX_MS)) {
            return "owner_timeout must be in [committer_timeout, " + OWNER_TIMEOUT_MAX_MS + "] ms";
        }
        Long eg = p.getEraGate();
        if (eg != null && (eg < ct || eg > ERA_GATE_MAX_MS || eg > rb)) {
            return "era_gate must be in [committer_timeout, min(" + ERA_GATE_MAX_MS + ", refuse_below)] ms";
        }
        return null;
    }

    /**
     * [0.4] (M1) Spec §8.6: the bounds on the manifest's meet LIMITS, checked beside {@link #violation}:
     * {@code listeners_max} absent or in [0, {@value #LISTENERS_MAX_BOUND}] (absent or 0 = conference mode not
     * served); when served, {@code alist_max} in [listeners_max, {@value #ALIST_MAX_BOUND}] and
     * {@code cap + listeners_max} ≤ {@value #TOTAL_LEGS_BOUND}; {@code ring_to_max} absent or in
     * [1, {@value #RING_TO_MAX_BOUND}]. Returns the first violated rule or {@code null}.
     */
    public static String limitsViolation(CallLimitsBean l, CallParamsBean p) {
        if (l == null) {
            return "limits missing";
        }
        Integer lm = l.getListenersMax();
        if (lm != null && (lm < 0 || lm > LISTENERS_MAX_BOUND)) {
            return "listeners_max must be in [0, " + LISTENERS_MAX_BOUND + "]";
        }
        if (lm != null && lm > 0) {
            Integer am = l.getAlistMax();
            if (am == null || am < lm || am > ALIST_MAX_BOUND) {
                return "alist_max must be in [listeners_max, " + ALIST_MAX_BOUND + "]";
            }
            Integer cap = p == null ? null : p.getCap();
            if (cap == null || cap + lm > TOTAL_LEGS_BOUND) {
                return "cap + listeners_max must be <= " + TOTAL_LEGS_BOUND;
            }
        }
        Integer rt = l.getRingToMax();
        if (rt != null && (rt < 1 || rt > RING_TO_MAX_BOUND)) {
            return "ring_to_max must be in [1, " + RING_TO_MAX_BOUND + "]";
        }
        return null;
    }

    /** [0.4] Throws {@link CallError#BAD_PARAMS} naming the first violated limits rule. */
    public static void checkLimits(CallLimitsBean l, CallParamsBean p, String where) throws CallProtocolException {
        String v = limitsViolation(l, p);
        if (v != null) {
            throw new CallProtocolException(CallError.BAD_PARAMS, where + ": " + v);
        }
    }

    /** True when every rule holds. */
    public static boolean inBounds(CallParamsBean p) {
        return violation(p) == null;
    }

    /** Throws {@link CallError#BAD_PARAMS} naming the first violated rule and where the params came from. */
    public static void check(CallParamsBean p, String where) throws CallProtocolException {
        String v = violation(p);
        if (v != null) {
            throw new CallProtocolException(CallError.BAD_PARAMS, where + ": " + v);
        }
    }
}
