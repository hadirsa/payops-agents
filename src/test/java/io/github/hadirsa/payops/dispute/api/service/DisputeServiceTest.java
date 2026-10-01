package io.github.hadirsa.payops.dispute.api.service;

import io.github.hadirsa.payops.dispute.domain.decision.Recommendation;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeGatewayException;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import io.github.hadirsa.payops.dispute.domain.port.DecisionSink;
import java.util.List;
import org.junit.jupiter.api.Test;

import static io.github.hadirsa.payops.dispute.api.DisputeTestData.decision;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisputeServiceTest {

    private static final DisputeCase CASE = new DisputeCase("dp_1", "ch_1", DisputeReason.GENERAL,
            new Money(100, "USD"), java.time.Instant.parse("2026-10-14T00:00:00Z"));

    private final DisputeTriageGateway triage = mock(DisputeTriageGateway.class);
    private final DecisionSink first = mock(DecisionSink.class);
    private final DecisionSink second = mock(DecisionSink.class);

    private DisputeService service(boolean publish, boolean autoApprove) {
        return new DisputeService(triage, List.of(first, second), new DisputeWorkflowProperties(publish, autoApprove));
    }

    @Test
    void defaultsPublishToEverySinkAndNeverApprove() {
        var decision = decision(false, Recommendation.REPRESENT);
        when(triage.fromCase(CASE)).thenReturn(decision);

        var outcome = service(true, false).triage(CASE);

        assertTrue(outcome.published());
        assertFalse(outcome.approved());
        verify(first).accept(decision);
        verify(second).accept(decision);
        verify(first, never()).approve(any());
    }

    @Test
    void autoApproveApprovesACleanRepresentDecision() {
        when(triage.fromCase(any())).thenReturn(decision(false, Recommendation.REPRESENT));

        var outcome = service(true, true).triage(CASE);

        assertTrue(outcome.approved());
        verify(first).approve("dp_1");
        verify(second).approve("dp_1");
    }

    @Test
    void autoApproveNeverApprovesADecisionThatNeedsAPerson() {
        when(triage.fromCase(any())).thenReturn(decision(true, Recommendation.REPRESENT));

        var outcome = service(true, true).triage(CASE);

        assertTrue(outcome.published());
        assertFalse(outcome.approved());
        verify(first, never()).approve(any());
    }

    @Test
    void autoApproveNeverApprovesAnAcceptRecommendation() {
        when(triage.fromCase(any())).thenReturn(decision(false, Recommendation.ACCEPT));

        assertFalse(service(true, true).triage(CASE).approved());
        verify(first, never()).approve(any());
    }

    @Test
    void publishingCanBeTurnedOffAndThenNothingLeavesTheService() {
        when(triage.fromCase(any())).thenReturn(decision(false, Recommendation.REPRESENT));

        var outcome = service(false, true).triage(CASE);

        assertFalse(outcome.published());
        assertFalse(outcome.approved());
        verifyNoInteractions(first, second);
    }

    @Test
    void aFailingSinkFailsTheCallSoNobodyThinksTheDecisionWasDelivered() {
        when(triage.fromCase(any())).thenReturn(decision(false, Recommendation.REPRESENT));
        doThrow(new DisputeGatewayException("down", null)).when(first).accept(any());

        assertThrows(DisputeGatewayException.class, () -> service(true, false).triage(CASE));
        verify(second, never()).accept(any());
    }

    @Test
    void freeTextUsesTheTextPath() {
        when(triage.fromText("dispute dp_1")).thenReturn(decision(false, Recommendation.REPRESENT));

        service(true, false).triageText("dispute dp_1");

        verify(triage).fromText("dispute dp_1");
    }

    @Test
    void approvalGoesToEverySink() {
        service(true, false).approve("dp_1");

        verify(first).approve("dp_1");
        verify(second).approve("dp_1");
    }
}
