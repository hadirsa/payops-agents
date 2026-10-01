package io.github.hadirsa.payops.dispute.api.service;

/** The agent run failed (model provider error, timeout, planning failure). */
public class DisputeTriageException extends RuntimeException {

    private final boolean timeout;

    public DisputeTriageException(String message, Throwable cause, boolean timeout) {
        super(message, cause);
        this.timeout = timeout;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
