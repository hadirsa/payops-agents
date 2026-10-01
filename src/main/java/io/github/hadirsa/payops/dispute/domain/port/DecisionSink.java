package io.github.hadirsa.payops.dispute.domain.port;

import io.github.hadirsa.payops.dispute.api.service.DisputeService;
import io.github.hadirsa.payops.dispute.domain.decision.DisputeDecision;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeConflictException;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeNotFoundException;

/**
 * Port for whatever should happen with a decision: save a draft at the payment provider, forward it
 * to a queue, open a ticket. The agent never calls this; {@code DisputeService} does, behind the
 * human-review rule.
 */
public interface DecisionSink {

    /** A decision was made. Must be safe to call again for the same dispute (replace, don't duplicate). */
    void accept(DisputeDecision decision);

    /**
     * A person approved sending the response for real. This cannot be undone.
     *
     * @throws DisputeNotFoundException if the dispute does not exist
     * @throws DisputeConflictException if there is nothing to approve, or it was already approved
     */
    void approve(String disputeId);
}
