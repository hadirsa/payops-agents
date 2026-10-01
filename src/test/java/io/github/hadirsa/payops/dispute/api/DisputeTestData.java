package io.github.hadirsa.payops.dispute.api;

import io.github.hadirsa.payops.dispute.domain.decision.DisputeDecision;
import io.github.hadirsa.payops.dispute.domain.decision.Recommendation;
import io.github.hadirsa.payops.dispute.domain.decision.ReviewReason;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceItem;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceType;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import java.time.Instant;
import java.util.List;

public final class DisputeTestData {

    private DisputeTestData() {}

    public static DisputeDecision decision(boolean needsReview, Recommendation recommendation) {
        return new DisputeDecision("dp_1", DisputeReason.PRODUCT_NOT_RECEIVED, new Money(24_000, "USD"),
                Instant.parse("2026-10-14T00:00:00Z"),
                List.of(EvidenceItem.found(EvidenceType.DELIVERY_PROOF, "signed"),
                        EvidenceItem.missing(EvidenceType.CUSTOMER_COMMUNICATION, "order system not connected")),
                recommendation, 0.86, needsReview,
                needsReview ? List.of(ReviewReason.MISSING_REQUIRED_EVIDENCE) : List.of(),
                "Delivered and signed.", "Dear issuer...");
    }
}
