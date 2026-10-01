package io.github.hadirsa.payops.dispute.api.service;

import io.github.hadirsa.payops.dispute.domain.decision.DisputeDecision;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import io.github.hadirsa.payops.dispute.domain.port.DecisionSink;
import io.github.hadirsa.payops.dispute.domain.port.DisputeIntake;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Triage, then hand the result to the {@link DecisionSink}s according to {@link DisputeWorkflowProperties}.
 * The agent only decides; every outward action happens here, behind the human-review rule.
 */
@Service
public class DisputeService implements DisputeIntake {

    private static final Logger log = LoggerFactory.getLogger(DisputeService.class);

    public record Outcome(DisputeDecision decision, boolean published, boolean approved) {}

    private final DisputeTriageGateway triage;
    private final List<DecisionSink> sinks;
    private final DisputeWorkflowProperties workflow;

    public DisputeService(DisputeTriageGateway triage, List<DecisionSink> sinks, DisputeWorkflowProperties workflow) {
        this.triage = triage;
        this.sinks = List.copyOf(sinks);
        this.workflow = workflow;
    }

    public Outcome triageText(String text) {
        return store(triage.fromText(text));
    }

    public Outcome triage(DisputeCase dispute) {
        return store(triage.fromCase(dispute));
    }

    @Override
    public void receive(DisputeCase dispute) {
        triage(dispute);
    }

    /** The human approval step: sends the response for real. Final. */
    public void approve(String disputeId) {
        sinks.forEach(sink -> sink.approve(disputeId));
        log.info("Approved dispute {}", disputeId);
    }

    private Outcome store(DisputeDecision decision) {
        boolean published = false;
        boolean approved = false;
        if (workflow.publishDecisions()) {
            sinks.forEach(sink -> sink.accept(decision));
            published = true;
            if (workflow.autoApprove() && decision.canAutoApprove()) {
                sinks.forEach(sink -> sink.approve(decision.disputeId()));
                approved = true;
            }
        }
        log.info("Dispute {}: {} (review needed: {}, published: {}, approved: {})", decision.disputeId(),
                decision.recommendation(), decision.requiresHumanReview(), published, approved);
        return new Outcome(decision, published, approved);
    }
}
