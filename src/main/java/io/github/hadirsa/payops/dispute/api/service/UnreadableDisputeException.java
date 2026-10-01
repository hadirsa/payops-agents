package io.github.hadirsa.payops.dispute.api.service;

/** Free text was sent but no complete dispute (id, payment, reason, amount, deadline) could be read from it. */
public class UnreadableDisputeException extends RuntimeException {

    public UnreadableDisputeException(Throwable cause) {
        super("A complete dispute could not be read from the text: " + cause.getMessage(), cause);
    }
}
