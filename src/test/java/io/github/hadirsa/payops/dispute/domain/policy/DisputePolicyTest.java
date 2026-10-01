package io.github.hadirsa.payops.dispute.domain.policy;

import io.github.hadirsa.payops.dispute.domain.decision.DisputeDraft;
import io.github.hadirsa.payops.dispute.domain.decision.Recommendation;
import io.github.hadirsa.payops.dispute.domain.decision.ReviewReason;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceItem;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidencePack;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceType;
import io.github.hadirsa.payops.dispute.domain.model.CaseOrigin;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DisputePolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    private final DisputePolicy policy =
            new DisputePolicy(new PolicyLimits(new BigDecimal("1000"), 0.7, Duration.ofDays(2)));

    private final EvidencePack complete = new EvidencePack(List.of(
            EvidenceItem.found(EvidenceType.RECEIPT, "receipt"),
            EvidenceItem.found(EvidenceType.DELIVERY_PROOF, "signed")));

    private DisputeCase dispute(long minorUnits, String currency, Instant respondBy) {
        return new DisputeCase("dp_1", "ch_1", DisputeReason.PRODUCT_NOT_RECEIVED,
                new Money(minorUnits, currency), respondBy);
    }

    private DisputeDraft draft(Recommendation recommendation, double confidence) {
        return new DisputeDraft(recommendation, confidence, "why", "letter");
    }

    @Test
    void routineCaseIsNotSentToReview() {
        var decision = policy.apply(dispute(24_000, "USD", NOW.plus(Duration.ofDays(10))),
                complete, draft(Recommendation.REPRESENT, 0.9), NOW);

        assertFalse(decision.requiresHumanReview());
        assertTrue(decision.reviewReasons().isEmpty());
        assertTrue(decision.canAutoApprove());
    }

    @Test
    void amountAboveLimitNeedsReviewEvenWhenTheDraftIsConfident() {
        var decision = policy.apply(dispute(420_000, "USD", NOW.plus(Duration.ofDays(10))),
                complete, draft(Recommendation.REPRESENT, 0.99), NOW);

        assertTrue(decision.requiresHumanReview());
        assertEquals(List.of(ReviewReason.AMOUNT_ABOVE_AUTO_LIMIT), decision.reviewReasons());
        assertFalse(decision.canAutoApprove());
    }

    @Test
    void limitIsComparedInMajorUnitsSoZeroDecimalCurrenciesAreNotInflated() {
        // 1000 JPY is 1000 yen, not 10.00: at the limit, not above it
        var atLimit = policy.apply(dispute(1_000, "JPY", NOW.plus(Duration.ofDays(10))),
                complete, draft(Recommendation.REPRESENT, 0.9), NOW);
        var above = policy.apply(dispute(1_001, "JPY", NOW.plus(Duration.ofDays(10))),
                complete, draft(Recommendation.REPRESENT, 0.9), NOW);

        assertFalse(atLimit.requiresHumanReview());
        assertTrue(above.requiresHumanReview());
    }

    @Test
    void representingWithMissingEvidenceNeedsReview() {
        var partial = new EvidencePack(List.of(
                EvidenceItem.found(EvidenceType.RECEIPT, "receipt"),
                EvidenceItem.missing(EvidenceType.DELIVERY_PROOF, "order system not connected")));

        var decision = policy.apply(dispute(12_000, "USD", NOW.plus(Duration.ofDays(10))),
                partial, draft(Recommendation.REPRESENT, 0.9), NOW);

        assertEquals(List.of(ReviewReason.MISSING_REQUIRED_EVIDENCE), decision.reviewReasons());
    }

    @Test
    void acceptingWithMissingEvidenceDoesNotNeedReviewForThatReason() {
        var partial = new EvidencePack(List.of(EvidenceItem.missing(EvidenceType.RECEIPT, "none")));

        var decision = policy.apply(dispute(12_000, "USD", NOW.plus(Duration.ofDays(10))),
                partial, draft(Recommendation.ACCEPT, 0.9), NOW);

        assertFalse(decision.requiresHumanReview());
        assertFalse(decision.canAutoApprove(), "accepting is never an automatic submission");
    }

    @Test
    void lowConfidenceNeedsReview() {
        var decision = policy.apply(dispute(12_000, "USD", NOW.plus(Duration.ofDays(10))),
                complete, draft(Recommendation.REPRESENT, 0.69), NOW);

        assertEquals(List.of(ReviewReason.LOW_CONFIDENCE), decision.reviewReasons());
    }

    @Test
    void passedAndApproachingDeadlinesAreDistinguished() {
        var passed = policy.apply(dispute(12_000, "USD", NOW.minusSeconds(1)),
                complete, draft(Recommendation.REPRESENT, 0.9), NOW);
        var soon = policy.apply(dispute(12_000, "USD", NOW.plus(Duration.ofDays(1))),
                complete, draft(Recommendation.REPRESENT, 0.9), NOW);

        assertEquals(List.of(ReviewReason.DEADLINE_PASSED), passed.reviewReasons());
        assertEquals(List.of(ReviewReason.DEADLINE_SOON), soon.reviewReasons());
    }

    @Test
    void severalProblemsAreAllReported() {
        var decision = policy.apply(dispute(420_000, "USD", NOW.plus(Duration.ofHours(5))),
                new EvidencePack(List.of(EvidenceItem.missing(EvidenceType.RECEIPT, "none"))),
                draft(Recommendation.REPRESENT, 0.2), NOW);

        assertEquals(List.of(ReviewReason.AMOUNT_ABOVE_AUTO_LIMIT, ReviewReason.MISSING_REQUIRED_EVIDENCE,
                ReviewReason.LOW_CONFIDENCE, ReviewReason.DEADLINE_SOON), decision.reviewReasons());
    }

    @Test
    void figuresReadFromFreeTextAlwaysNeedAPersonToCheckThem() {
        var fromText = new DisputeCase("dp_1", "ch_1", DisputeReason.PRODUCT_NOT_RECEIVED, new Money(24_000, "USD"),
                NOW.plus(Duration.ofDays(10)), CaseOrigin.EXTRACTED);

        var decision = policy.apply(fromText, complete, draft(Recommendation.REPRESENT, 0.99), NOW);

        assertEquals(List.of(ReviewReason.FIGURES_FROM_TEXT), decision.reviewReasons());
        assertFalse(decision.canAutoApprove());
    }

    @Test
    void decisionCarriesTheDraftAndEvidenceThrough() {
        var decision = policy.apply(dispute(24_000, "USD", NOW.plus(Duration.ofDays(10))),
                complete, draft(Recommendation.REPRESENT, 0.9), NOW);

        assertEquals("dp_1", decision.disputeId());
        assertEquals("letter", decision.responseLetter());
        assertEquals(complete.items(), decision.evidence());
    }
}
