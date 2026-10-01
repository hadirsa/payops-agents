package io.github.hadirsa.payops.dispute.api.service;

import io.github.hadirsa.payops.dispute.domain.exception.DisputeGatewayException;
import io.github.hadirsa.payops.dispute.domain.exception.InvalidDisputeException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Failures arrive wrapped several layers deep; the caller must still get the right type. */
class DisputeTriageGatewayTest {

    private static Exception wrapped(Throwable root) {
        return new ExecutionException(new RuntimeException("Action failed", new RuntimeException("Invalid LLM return", root)));
    }

    @Test
    void outsideSystemFailureSurfacesAsItself() {
        var result = DisputeTriageGateway.translate(wrapped(new DisputeGatewayException("Stripe down", null)));

        assertInstanceOf(DisputeGatewayException.class, result);
    }

    @Test
    void textThatHoldsNoCompleteDisputeIsTheCallersProblem() {
        var result = DisputeTriageGateway.translate(wrapped(new InvalidDisputeException("respondBy is required")));

        var unreadable = assertInstanceOf(UnreadableDisputeException.class, result);
        assertTrue(unreadable.getMessage().contains("respondBy is required"), "says what was missing");
    }

    @Test
    void anythingElseIsAModelFailure() {
        var result = assertInstanceOf(DisputeTriageException.class,
                DisputeTriageGateway.translate(wrapped(new IllegalStateException("model exploded"))));

        assertFalse(result.isTimeout());
    }

    @Test
    void timeoutsAreRecognisedAnywhereInTheChain() {
        var result = assertInstanceOf(DisputeTriageException.class,
                DisputeTriageGateway.translate(wrapped(new HttpTimeoutException("request timed out"))));

        assertTrue(result.isTimeout());
    }
}
