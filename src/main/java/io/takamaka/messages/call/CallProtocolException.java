package io.takamaka.messages.call;

/**
 * A refusal under the call protocol: a typed {@link CallError} plus a short ASCII reason for tests and logs
 * (never user content).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class CallProtocolException extends Exception {

    private final CallError error;
    private final String reason;

    public CallProtocolException(CallError error, String reason) {
        super(error.code() + ": " + reason);
        this.error = error;
        this.reason = reason;
    }

    public CallProtocolException(CallError error, String reason, Throwable cause) {
        super(error.code() + ": " + reason, cause);
        this.error = error;
        this.reason = reason;
    }

    public CallError getError() {
        return error;
    }

    public String getReason() {
        return reason;
    }
}
