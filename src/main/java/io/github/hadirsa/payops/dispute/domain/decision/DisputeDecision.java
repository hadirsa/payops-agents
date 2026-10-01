package io.github.hadirsa.payops.dispute.domain.decision;

import com.embabel.agent.domain.library.HasContent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceItem;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NonNull;

/**
 * Goal type of the dispute agent: the LLM's draft after the policy has run.
 *
 * <p>Implements {@link HasContent} because Embabel's A2A {@code message/stream} never sends the result
 * artifact; it only puts {@code getContent()} in the final status message.
 */
public record DisputeDecision(
        String disputeId,
        DisputeReason reason,
        Money amount,
        Instant respondBy,
        List<EvidenceItem> evidence,
        Recommendation recommendation,
        double confidence,
        boolean requiresHumanReview,
        List<ReviewReason> reviewReasons,
        String rationale,
        String responseLetter
) implements HasContent {

    public DisputeDecision {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        reviewReasons = reviewReasons == null ? List.of() : List.copyOf(reviewReasons);
    }

    /** May this decision be approved without a person looking at it? */
    public boolean canAutoApprove() {
        return !requiresHumanReview && recommendation == Recommendation.REPRESENT;
    }

    @JsonIgnore
    @Override
    public @NonNull String getContent() {
        var text = new StringBuilder()
                .append("Dispute ").append(disputeId).append(" (").append(reason.label()).append(", ")
                .append(amount.display()).append("): ").append(recommendation)
                .append(String.format(" (confidence %.2f)", confidence));
        if (requiresHumanReview) {
            text.append("\nNeeds human review: ").append(reviewReasons);
        }
        text.append("\n").append(rationale);
        return text.toString();
    }
}
