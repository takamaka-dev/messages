package io.takamaka.messages.call;

/**
 * The typed error results of spec §11 (never free text), plus {@link #KEY_REFUSED}: the client-local refusal of a
 * key-plane object (a commit, a channel open, a handover) that the user sees as "key rotation refused" (§6.2).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public enum CallError {
    BAD_SIGNATURE("bad_signature"),
    BAD_NONCE("bad_nonce"),
    NOT_REGISTERED("not_registered"),
    NOT_INVITED("not_invited"),
    ERA_MISMATCH("era_mismatch"),
    ERA_CONFLICT("era_conflict"),
    EPOCH_TAKEN("epoch_taken"),
    TOO_MANY_DEVICES("too_many_devices"),
    FROZEN("frozen"),
    RATE_LIMITED("rate_limited"),
    /**
     * [0.2 amendment, C182 build 1] A creation record whose {@code svc} or {@code net} is not this service's own (the
     * client reads {@code net} in the signed manifest and {@code svc} from the nonce's {@code aud} before signing).
     */
    WRONG_SERVICE("wrong_service"),
    EXISTS_MEMBER("exists_member"),
    /** Client-local: not a wire code of §11. */
    KEY_REFUSED("key_refused"),
    /**
     * [0.3] S-4 (2026-10-29): the served {@code params} (manifest, creation record or grant) are outside the pinned
     * bounds of {@link CallParamsBounds} — a hostile or misconfigured service. Client-local (refused before any ring,
     * at create and at join); callsvc checks its own profile at boot with the same bounds.
     */
    BAD_PARAMS("bad_params"),
    /** [0.4] M8: {@code apol:"open"} (link meets) is release 2; a create/era carrying it is refused. */
    APOL_UNSUPPORTED("apol_unsupported"),
    /** [0.4] §4.4: the era / drop signer is neither the creator nor the acting owner the service computes. */
    NOT_OWNER("not_owner"),
    /** [0.4] §7.9: the drop / mute signer is not the owner, the acting owner or in {@code mods}. */
    NOT_MODERATOR("not_moderator"),
    /** [0.4] §7.9: the signal to the dropped listener leg. */
    DROPPED("dropped");

    private final String code;

    CallError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
