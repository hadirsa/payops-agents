package io.github.hadirsa.payops.dispute.domain.exception;

/** The dispute is not in a state that allows the call, e.g. submitting with no saved evidence. */
public class DisputeConflictException extends RuntimeException {

    public DisputeConflictException(String message) {
        super(message);
    }
}
