package io.github.hadirsa.payops.dispute.api.rest;

import io.github.hadirsa.payops.dispute.api.service.DisputeService;
import io.github.hadirsa.payops.dispute.domain.decision.DisputeDecision;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Public response. Never return the domain records directly: they are the LLM schema, so a
 * prompt-driven rename would silently change this API.
 */
public record DisputeResponse(
        String disputeId,
        String reason,
        Amount amount,
        Instant respondBy,
        String recommendation,
        double confidence,
        boolean requiresHumanReview,
        List<String> reviewReasons,
        String rationale,
        String responseLetter,
        List<Evidence> evidence,
        boolean published,
        boolean approved
) {

    public record Amount(BigDecimal value, String currency) {}

    public record Evidence(String type, boolean found, String summary) {

        static Evidence from(EvidenceItem item) {
            return new Evidence(item.type().name(), item.found(), item.summary());
        }
    }

    static DisputeResponse from(DisputeService.Outcome outcome) {
        DisputeDecision d = outcome.decision();
        return new DisputeResponse(
                d.disputeId(),
                d.reason().name(),
                new Amount(d.amount().major(), d.amount().currency()),
                d.respondBy(),
                d.recommendation().name(),
                d.confidence(),
                d.requiresHumanReview(),
                d.reviewReasons().stream().map(Enum::name).toList(),
                d.rationale(),
                d.responseLetter(),
                d.evidence().stream().map(Evidence::from).toList(),
                outcome.published(),
                outcome.approved());
    }
}
