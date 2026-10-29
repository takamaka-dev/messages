package io.takamaka.messages.call;

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
 *   <li>{@code cap} ∈ [2, {@value #CAP_MAX}]; {@code devices_per_identity} ∈ [1, {@value #DEVICES_MAX}].</li>
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
        return null;
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
