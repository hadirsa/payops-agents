package io.github.hadirsa.payops.dispute.domain.exception;

public class DisputeNotFoundException extends RuntimeException {

    private final String disputeId;

    public DisputeNotFoundException(String disputeId) {
        super("No dispute with id " + disputeId);
        this.disputeId = disputeId;
    }

    public String disputeId() {
        return disputeId;
    }
}
