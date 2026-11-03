package io.takamaka.messages.call;

/**
 * Constants of the Takamaka E2EE calls protocol, version 1 ({@code tkm-call/v1}).
 *
 * <p>Normative source: {@code rschat-docs/security/E2EE_CALLS_PROTOCOL_SPEC_v1.md}. Every label below is a
 * domain-separation string of that specification; they are ASCII and are used as raw octets.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class CallConstants {

    private CallConstants() {
    }

    /** Protocol major carried in {@code v}. */
    public static final int VERSION = 1;

    /** Prefix of every label; the signature context is {@code PREFIX + t} (spec §3.2). */
    public static final String PREFIX = "tkm-call/v1/";

    /** The hybrid suite carried in announcements and channel opens (spec §4.3, §5.2). */
    public static final String SUITE_HYBRID_1 = "tkm-call/v1/hybrid-1";

    /** The SFrame suite name of the manifest (spec §8.6). */
    public static final String SFRAME_SUITE_NAME = "AES_256_GCM_SHA512";

    // ---- object types (spec §3.3) ----
    public static final String T_CREATE = "create";
    public static final String T_ERA = "era";
    public static final String T_ANNOUNCE = "announce";
    public static final String T_CHANNEL = "channel";
    public static final String T_COMMIT = "commit";
    public static final String T_GOODBYE = "goodbye";
    public static final String T_DECLINE = "decline";
    public static final String T_RING = "ring";
    public static final String T_GRANT = "grant";
    public static final String T_MANIFEST = "manifest";
    public static final String T_LOOKUP = "lookup";
    public static final String T_MUTE = "mute";
    public static final String T_UNMUTE = "unmute";
    /** [0.2] Read-only fetch of the creation record and every era record before announcing (§7.3 step 0). */
    public static final String T_RECORDS = "records";

    // ---- audiences (spec §3.1) ----
    public static final String AUD_SVC = "svc:";
    public static final String AUD_RSCHAT = "rschat:";
    public static final String AUD_LEG = "leg:";
    /**
     * Audience of the manifest. The spec (§3.3) says "any" without giving the string; this implementation uses
     * the literal {@code "any"} (ambiguity A-3 of the C182 status report).
     */
    public static final String AUD_ANY = "any";

    // ---- hash prefixes (spec §4) ----
    public static final String H_ID = PREFIX + "id";
    public static final String H_ERA = PREFIX + "era";
    public static final String H_ANN = PREFIX + "ann";

    // ---- channel labels (spec §5) ----
    public static final String L_CHANNEL = PREFIX + "channel";
    public static final String L_MSG = PREFIX + "msg";
    public static final String L_CHAIN = PREFIX + "chain";
    public static final String DIR_A_TO_B = "A>B";
    public static final String DIR_B_TO_A = "B>A";

    // ---- epoch labels (spec §6.1) ----
    public static final String L_EPOCH = PREFIX + "epoch";
    public static final String L_STEP = PREFIX + "step";
    public static final String L_SENDER = PREFIX + "sender";
    public static final String L_TEXT = PREFIX + "text";
    public static final String L_BROADCAST = PREFIX + "broadcast";
    public static final String L_CODE = PREFIX + "code";
    public static final String L_CONFIRM = PREFIX + "confirm";

    // ---- conversation seed labels (spec §10.1) ----
    public static final String L_CONV_TITLE = PREFIX + "conv/title";
    public static final String L_CONV_SALT = PREFIX + "conv/salt";
    public static final String L_CONV_KEY = PREFIX + "conv/key";
    public static final String CONV_TITLE_PREFIX = "call-";

    // ---- enumerations ----
    public static final String MODE_STANDARD = "standard";
    public static final String MODE_CONFERENCE = "conference";
    /** [0.2] §7.1 (C-16): {@code k} of the answered notice — a {@code ring} signed by the callee to its own devices. */
    public static final String RING_K_ANSWERED = "answered";
    public static final String ROLE_SPEAKER = "speaker";
    public static final String ROLE_LISTENER = "listener";
    public static final String CAP_BASIC = "basic";
    public static final String CAP_BASIC_HQ = "basic_hq";
    public static final String KIND_INITIAL = "initial";
    public static final String KIND_LEAVE = "leave";
    public static final String KIND_ERA = "era";
    public static final String KIND_BACKSTOP = "backstop";
    public static final String KIND_RESTART = "restart";
    /**
     * [0.2] §6.5 {@code rotation} row (K-3/J-2, 2026-10-08): the committer's own periodic rekey, {@code rotation} ms
     * after the last fresh commit it applied, same roster; budgeted like {@code leave} and {@code backstop}.
     */
    public static final String KIND_ROTATION = "rotation";

    /** Every {@code kind} of a fresh commit (§6.2 + the [0.2] §6.5 {@code rotation} row); anything else is refused. */
    public static final java.util.Set<String> COMMIT_KINDS = java.util.Set.of(KIND_INITIAL, KIND_LEAVE, KIND_ERA,
            KIND_BACKSTOP, KIND_RESTART, KIND_ROTATION);

    /**
     * §6.2 budget (R11): a receiver refuses a live commit of a BUDGETED kind ({@code leave}, {@code backstop},
     * [0.2] {@code rotation}) whose SIGNED {@code ts} is less than {@code refuse_below} after the signed {@code ts} of the
     * previous fresh commit it applied ([0.2] X-1/X-2, 2026-10-08: measured on the committers' timestamps, never on
     * arrival); the service enforces the same window on its own clock. {@code era}, {@code restart} and
     * {@code initial} are exempt (and reset the window).
     */
    public static boolean isBudgetedKind(String kind) {
        return KIND_LEAVE.equals(kind) || KIND_BACKSTOP.equals(kind) || KIND_ROTATION.equals(kind);
    }

    // ---- sizes ----
    public static final int HASH_LEN = 32;
    public static final int LEG_ID_LEN = 8;
    public static final int SIG_LEN = 64;
    public static final int KEY_LEN = 32;
    public static final int GCM_NONCE_LEN = 12;
    public static final int GCM_TAG_LEN = 16;
    public static final int CONFIRM_LEN = 16;
    public static final int NONCE_MAC_LEN = 16;
    /** [0.2] Length of the per-request random field {@code r} of the service nonce (spec §8.1). */
    public static final int NONCE_RAND_LEN = 8;
    /**
     * [0.2] §10.2 (J-3): the AAD domain of the in-call text AEAD, followed by a 0x00 separator, call_id (32 raw bytes),
     * leg_id (8 raw bytes) and BE32(e).
     */
    public static final String AAD_TEXT = PREFIX + "text";
    /** [0.2] §10.2/§9.2: at most this many epochs (the current one included) whose texts a receiver still opens. */
    public static final int TEXT_MAX_EPOCHS = 4;
    /** Maximum size of an in-call text object (spec §10.2: max 4 kB). */
    public static final int TEXT_MAX_BYTES = 4096;
    /** Validity of a service nonce (spec §8.1). */
    public static final long NONCE_VALIDITY_MS = 60_000L;
    /**
     * [0.2] §6.2 {@code ts_tolerance} (X-1/X-2, 2026-10-08): how far a commit's signed {@code ts} may be AHEAD of the
     * verifier's wall clock — the receiver refuses beyond it, the service refuses beyond it on its own clock. The spec
     * names no separate value; it is the §8.1 time tolerance, the nonce validity (60 s).
     */
    public static final long TS_TOLERANCE_MS = NONCE_VALIDITY_MS;
    /**
     * [0.2] §6.2 catch-up (X-1): the {@code h} of the channel-message body a leg that is behind sends to the committer
     * to ask for a catch-up handover ({@code {"h":"catchup","epoch":e}}, e = the epoch it holds). The answer is the
     * §6.4 step 4 handover. The spec names the request, not its object: resolution C-1 of the X-1/X-2 fix report.
     */
    public static final String H_CATCHUP = "catchup";
    /** [0.2] §6.2: at most this many {@code epoch_taken} refusals in a row before a leg asks for the catch-up. */
    public static final int EPOCH_TAKEN_MAX_IN_ROW = 3;
    /** Lifetime of a grant (spec §8.4: {@code exp = ts + 60000}). */
    public static final long GRANT_LIFETIME_MS = 60_000L;
    /**
     * [0.2] Grant refresh (§8.4): a client asks a fresh grant before (re)connecting to the relay when its grant has
     * less than this left (or is past {@code exp}); the service answers at most one refresh per leg per this interval
     * ({@code rate_limited grant_refresh} otherwise).
     */
    public static final long GRANT_REFRESH_MARGIN_MS = 10_000L;
    /** Maximum lifetime of a creation record (spec §4.1: at most 4 h). */
    public static final long CREATE_MAX_LIFETIME_MS = 4L * 3600_000L;
    // ---- [0.4] the meet (spec draft 0.4, rulings M1–M10 of 2026-11-03) ----
    /** [0.4] §6.6 batched broadcast-key distribution, committer → every present listener. */
    public static final String T_BKEY = "bkey";
    /** [0.4] §7.8 raise / lower hand, a listener leg → owner + moderators. */
    public static final String T_HAND = "hand";
    /** [0.4] §7.9 remove a listener leg (no key change). */
    public static final String T_DROP = "drop";
    /** [0.4] §7.10 meet status request (identity → service) and its service-signed answer (same {@code t}). */
    public static final String T_MEETSTATUS = "meetstatus";
    /** [0.4] §7.11 the ad-hoc meet notice, owner → rschat (never rings). */
    public static final String T_MEETNOTICE = "meetnotice";
    /** [0.4] §7.10: audience of the service-signed {@code meetstatus} answer, {@code id:<identity>}. */
    public static final String AUD_ID = "id:";

    /** Era reasons (§4.2); [0.4] {@link #REASON_SUCCEED} = the acting owner's first era (§4.4). */
    public static final String REASON_ADD = "add";
    public static final String REASON_REMOVE = "remove";
    public static final String REASON_PROMOTE = "promote";
    public static final String REASON_DEMOTE = "demote";
    public static final String REASON_MODE = "mode";
    public static final String REASON_MODS = "mods";
    public static final String REASON_SUCCEED = "succeed";
    public static final java.util.Set<String> ERA_REASONS = java.util.Set.of(REASON_ADD, REASON_REMOVE, REASON_PROMOTE,
            REASON_DEMOTE, REASON_MODE, REASON_MODS, REASON_SUCCEED);
    /** [0.4] §6.5: the era reasons that ENLARGE the roster and open or extend the era gate (a {@code succeed} era counts). */
    public static boolean isEnlargingReason(String reason) {
        return REASON_ADD.equals(reason) || REASON_PROMOTE.equals(reason) || REASON_SUCCEED.equals(reason);
    }

    /** Audience policies (§4.1); [0.4] M8: {@code open} is refused {@code apol_unsupported} in release 1. */
    public static final String APOL_INVITED = "invited";
    public static final String APOL_OPEN = "open";

    /** [0.4] §4.1 initial {@code owner_timeout}: the wait after the commit that first left the creator out (§4.4). */
    public static final long OWNER_TIMEOUT_MS = 30_000L;
    /** [0.4] §4.1 initial {@code era_gate}: the coalescing window of enlarging era commits (§6.5). */
    public static final long ERA_GATE_MS = 10_000L;
    /** [0.4] §8.2 M1: listeners per meet on the manifest (the ceiling clients bound; served 50 until the staging gates). */
    public static final int LISTENERS_MAX = 200;
    /** [0.4] §8.2 M1: the served value of {@code listeners_max} during the staging (§8.2, §8.6). */
    public static final int LISTENERS_SERVED_STAGING = 50;
    /** [0.4] §8.2: audience list size. */
    public static final int ALIST_MAX = 200;
    /** [0.4] §7.8: the raised-hands queue per call. */
    public static final int HAND_QUEUE_MAX = 25;
    /** [0.4] §7.11: {@code to} of a meet notice. */
    public static final int MEETNOTICE_TO_MAX = 100;
    /** [0.4] §11 the {@code rate_limited} limit names of the meet. */
    public static final String LIMIT_LISTENERS_MAX = "listeners_max";
    public static final String LIMIT_CAP = "cap";
    public static final String LIMIT_ALIST_MAX = "alist_max";
    public static final String LIMIT_ERA_GATE = "era_gate";
    public static final String LIMIT_BKEY = "bkey";
    public static final String LIMIT_HANDS = "hands";
}
