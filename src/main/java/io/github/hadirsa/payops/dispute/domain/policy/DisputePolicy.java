package io.github.hadirsa.payops.dispute.domain.policy;

import io.github.hadirsa.payops.dispute.domain.decision.DisputeDecision;
import io.github.hadirsa.payops.dispute.domain.decision.DisputeDraft;
import io.github.hadirsa.payops.dispute.domain.decision.Recommendation;
import io.github.hadirsa.payops.dispute.domain.decision.ReviewReason;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidencePack;
import io.github.hadirsa.payops.dispute.domain.model.CaseOrigin;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import java.time.Instant;
import java.util.ArrayList;

/**
 * Turns the LLM's {@link DisputeDraft} into a {@link DisputeDecision}. The draft can only ask for
 * more caution here, never less: this code decides whether a person must look at the case.
 */
public final class DisputePolicy {

    private final PolicyLimits limits;

    public DisputePolicy(PolicyLimits limits) {
        this.limits = limits;
    }

    public DisputeDecision apply(DisputeCase dispute, EvidencePack evidence, DisputeDraft draft, Instant now) {
        var reasons = new ArrayList<ReviewReason>();
        if (dispute.origin() == CaseOrigin.EXTRACTED) {
            reasons.add(ReviewReason.FIGURES_FROM_TEXT);
        }
        if (dispute.amount().major().compareTo(limits.autoDecisionLimit()) > 0) {
            reasons.add(ReviewReason.AMOUNT_ABOVE_AUTO_LIMIT);
        }
        if (draft.recommendation() == Recommendation.REPRESENT && !evidence.missingTypes().isEmpty()) {
            reasons.add(ReviewReason.MISSING_REQUIRED_EVIDENCE);
        }
        if (draft.confidence() < limits.minConfidence()) {
            reasons.add(ReviewReason.LOW_CONFIDENCE);
        }
        if (!dispute.respondBy().isAfter(now)) {
            reasons.add(ReviewReason.DEADLINE_PASSED);
        } else if (dispute.respondBy().isBefore(now.plus(limits.deadlineWarning()))) {
            reasons.add(ReviewReason.DEADLINE_SOON);
        }
        return new DisputeDecision(
                dispute.disputeId(), dispute.reason(), dispute.amount(), dispute.respondBy(),
                evidence.items(), draft.recommendation(), draft.confidence(),
                !reasons.isEmpty(), reasons, draft.rationale(), draft.responseLetter());
    }
}
