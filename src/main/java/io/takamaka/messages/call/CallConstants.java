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
    public static final String ROLE_SPEAKER = "speaker";
    public static final String ROLE_LISTENER = "listener";
    public static final String CAP_BASIC = "basic";
    public static final String CAP_BASIC_HQ = "basic_hq";
    public static final String KIND_INITIAL = "initial";
    public static final String KIND_LEAVE = "leave";
    public static final String KIND_ERA = "era";
    public static final String KIND_BACKSTOP = "backstop";
    public static final String KIND_RESTART = "restart";

    // ---- sizes ----
    public static final int HASH_LEN = 32;
    public static final int LEG_ID_LEN = 8;
    public static final int SIG_LEN = 64;
    public static final int KEY_LEN = 32;
    public static final int GCM_NONCE_LEN = 12;
    public static final int GCM_TAG_LEN = 16;
    public static final int CONFIRM_LEN = 16;
    public static final int NONCE_MAC_LEN = 16;
    /** Validity of a service nonce (spec §8.1). */
    public static final long NONCE_VALIDITY_MS = 60_000L;
    /** Lifetime of a grant (spec §8.4: {@code exp = ts + 60000}). */
    public static final long GRANT_LIFETIME_MS = 60_000L;
    /** Maximum lifetime of a creation record (spec §4.1: at most 4 h). */
    public static final long CREATE_MAX_LIFETIME_MS = 4L * 3600_000L;
}
