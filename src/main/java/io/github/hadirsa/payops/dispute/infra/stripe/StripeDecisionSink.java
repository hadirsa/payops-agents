package io.github.hadirsa.payops.dispute.infra.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Dispute;
import com.stripe.param.DisputeUpdateParams;
import io.github.hadirsa.payops.dispute.domain.decision.DisputeDecision;
import io.github.hadirsa.payops.dispute.domain.decision.Recommendation;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeConflictException;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeGatewayException;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeNotFoundException;
import io.github.hadirsa.payops.dispute.domain.port.DecisionSink;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Writes the drafted response to the Stripe dispute as evidence, and submits it when a person approves.
 *
 * <p>Nothing is stored here. The triage outcome is kept on the Stripe dispute itself as metadata, so
 * {@link #approve} can refuse to submit a dispute that was never triaged, or one where the recommendation
 * was to accept. Accepting a decision never submits: Stripe treats submission as final.
 */
@Component
@ConditionalOnProperty(name = "dispute.provider", havingValue = "stripe")
public class StripeDecisionSink implements DecisionSink {

    static final String RECOMMENDATION_KEY = "triage_recommendation";
    static final String NEEDS_REVIEW_KEY = "triage_needs_review";

    private final StripeClient stripe;

    public StripeDecisionSink(StripeClient stripe) {
        this.stripe = stripe;
    }

    @Override
    public void accept(DisputeDecision decision) {
        var params = DisputeUpdateParams.builder()
                .putMetadata(RECOMMENDATION_KEY, decision.recommendation().name())
                .putMetadata(NEEDS_REVIEW_KEY, String.valueOf(decision.requiresHumanReview()))
                .setSubmit(false);
        if (decision.recommendation() == Recommendation.REPRESENT && !decision.responseLetter().isBlank()) {
            params.setEvidence(DisputeUpdateParams.Evidence.builder()
                    .setUncategorizedText(decision.responseLetter())
                    .build());
        }
        update(decision.disputeId(), params.build());
    }

    @Override
    public void approve(String disputeId) {
        var dispute = retrieve(disputeId);
        var recommendation = dispute.getMetadata() == null ? null : dispute.getMetadata().get(RECOMMENDATION_KEY);
        if (recommendation == null) {
            throw new DisputeConflictException("Dispute " + disputeId + " has not been triaged; no saved draft to submit");
        }
        if (!Recommendation.REPRESENT.name().equals(recommendation)) {
            throw new DisputeConflictException("Dispute " + disputeId + " was triaged as " + recommendation
                    + ", so there is no response to submit");
        }
        update(disputeId, DisputeUpdateParams.builder().setSubmit(true).build());
    }

    private Dispute retrieve(String disputeId) {
        try {
            return stripe.v1().disputes().retrieve(disputeId);
        } catch (StripeException e) {
            throw translate(disputeId, e);
        }
    }

    private void update(String disputeId, DisputeUpdateParams params) {
        try {
            stripe.v1().disputes().update(disputeId, params);
        } catch (StripeException e) {
            throw translate(disputeId, e);
        }
    }

    private static RuntimeException translate(String disputeId, StripeException e) {
        if (Integer.valueOf(404).equals(e.getStatusCode()) || "resource_missing".equals(e.getCode())) {
            return new DisputeNotFoundException(disputeId);
        }
        return new DisputeGatewayException("Stripe call failed for dispute " + disputeId, e);
    }
}
