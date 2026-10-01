package io.github.hadirsa.payops.dispute.domain.exception;

/** The payment provider or another outside system could not be reached or rejected the call. */
public class DisputeGatewayException extends RuntimeException {

    public DisputeGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
