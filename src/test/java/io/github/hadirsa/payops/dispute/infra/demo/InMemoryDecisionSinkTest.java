package io.github.hadirsa.payops.dispute.infra.demo;

import io.github.hadirsa.payops.dispute.domain.decision.DisputeDecision;
import io.github.hadirsa.payops.dispute.domain.decision.Recommendation;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceCollector;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceType;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeConflictException;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryDecisionSinkTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);

    private final InMemoryDecisionSink sink = new InMemoryDecisionSink();

    private static DisputeDecision decision(String id, Recommendation recommendation) {
        return new DisputeDecision(id, DisputeReason.FRAUDULENT, new Money(100, "USD"), Instant.parse("2026-10-14T00:00:00Z"),
                List.of(), recommendation, 0.9, false, List.of(), "why", "letter");
    }

    @Test
    void anAcceptedDecisionCanBeReplacedUntilItIsApproved() {
        sink.accept(decision("dp_1", Recommendation.REPRESENT));
        sink.accept(decision("dp_1", Recommendation.REPRESENT));

        assertTrue(sink.decision("dp_1").isPresent());
        assertFalse(sink.isApproved("dp_1"));
    }

    @Test
    void approvalNeedsAPriorDecisionAndWorksOnlyOnce() {
        assertThrows(DisputeConflictException.class, () -> sink.approve("dp_1"));

        sink.accept(decision("dp_1", Recommendation.REPRESENT));
        sink.approve("dp_1");

        assertTrue(sink.isApproved("dp_1"));
        assertThrows(DisputeConflictException.class, () -> sink.approve("dp_1"));
        assertThrows(DisputeConflictException.class, () -> sink.accept(decision("dp_1", Recommendation.REPRESENT)));
    }

    @Test
    void aDisputeTriagedAsAcceptHasNothingToApprove() {
        sink.accept(decision("dp_1", Recommendation.ACCEPT));

        assertThrows(DisputeConflictException.class, () -> sink.approve("dp_1"));
        assertFalse(sink.isApproved("dp_1"));
    }

    @Test
    void everySampleHasTheEvidenceItsReasonNeedsExceptTheDeliberatelyIncompleteOne() {
        var collector = new EvidenceCollector(List.of(new DemoEvidenceSource(CLOCK)));
        var samples = DemoCases.all(CLOCK);

        for (var id : List.of("dp_demo_fraud", "dp_demo_not_received", "dp_demo_duplicate", "dp_demo_high_value")) {
            assertTrue(collector.collect(samples.get(id).dispute()).missingTypes().isEmpty(), id);
        }
        assertEquals(Set.of(EvidenceType.DELIVERY_PROOF, EvidenceType.CUSTOMER_COMMUNICATION),
                Set.copyOf(collector.collect(samples.get("dp_demo_missing_evidence").dispute()).missingTypes()));
    }

    @Test
    void sampleDeadlinesAreRelativeToTheClock() {
        var fraud = DemoCases.all(CLOCK).get("dp_demo_fraud").dispute();

        assertEquals(CLOCK.instant().plusSeconds(12 * 86_400L), fraud.respondBy());
    }
}
