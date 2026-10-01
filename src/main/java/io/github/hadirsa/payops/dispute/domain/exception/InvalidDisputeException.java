package io.github.hadirsa.payops.dispute.domain.exception;

/** A dispute message that is incomplete or malformed, whether it came from a caller or was read from text. */
public class InvalidDisputeException extends IllegalArgumentException {

    public InvalidDisputeException(String message) {
        super(message);
    }
}
