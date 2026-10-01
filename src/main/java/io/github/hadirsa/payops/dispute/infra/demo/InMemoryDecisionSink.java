package io.github.hadirsa.payops.dispute.infra.demo;

import io.github.hadirsa.payops.dispute.domain.decision.DisputeDecision;
import io.github.hadirsa.payops.dispute.domain.decision.Recommendation;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeConflictException;
import io.github.hadirsa.payops.dispute.domain.port.DecisionSink;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Keeps decisions in memory and follows the same approval rules as a real provider. Active unless
 * {@code dispute.provider=stripe}. Also the fake to use in tests.
 */
@Component
@ConditionalOnProperty(name = "dispute.provider", havingValue = "demo", matchIfMissing = true)
public class InMemoryDecisionSink implements DecisionSink {

    private final Map<String, DisputeDecision> decisions = new ConcurrentHashMap<>();
    private final Set<String> approved = ConcurrentHashMap.newKeySet();

    @Override
    public void accept(DisputeDecision decision) {
        if (approved.contains(decision.disputeId())) {
            throw new DisputeConflictException("Dispute " + decision.disputeId() + " was already approved");
        }
        decisions.put(decision.disputeId(), decision);
    }

    @Override
    public void approve(String disputeId) {
        var decision = decisions.get(disputeId);
        if (decision == null) {
            throw new DisputeConflictException("Dispute " + disputeId + " has not been triaged; nothing to approve");
        }
        if (decision.recommendation() != Recommendation.REPRESENT) {
            throw new DisputeConflictException("Dispute " + disputeId + " was triaged as "
                    + decision.recommendation() + ", so there is no response to send");
        }
        if (!approved.add(disputeId)) {
            throw new DisputeConflictException("Dispute " + disputeId + " was already approved");
        }
    }

    public Optional<DisputeDecision> decision(String disputeId) {
        return Optional.ofNullable(decisions.get(disputeId));
    }

    public boolean isApproved(String disputeId) {
        return approved.contains(disputeId);
    }
}
