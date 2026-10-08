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
    KEY_REFUSED("key_refused");

    private final String code;

    CallError(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
