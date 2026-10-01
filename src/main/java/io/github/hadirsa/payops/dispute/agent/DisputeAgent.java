package io.github.hadirsa.payops.dispute.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.ActionRetryPolicy;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.common.ai.model.LlmOptions;
import io.github.hadirsa.payops.dispute.domain.decision.DisputeDecision;
import io.github.hadirsa.payops.dispute.domain.decision.DisputeDraft;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceCollector;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidencePack;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceSource;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import io.github.hadirsa.payops.dispute.domain.model.ExtractedCase;
import io.github.hadirsa.payops.dispute.domain.policy.DisputePolicy;
import java.time.Clock;
import java.util.List;

/**
 * Plans UserInput -> DisputeCase -> EvidencePack -> DisputeDecision. Callers that already hold a
 * DisputeCase (the JSON endpoint, a provider webhook, a queue consumer) skip the first step: the
 * planner starts from whatever types it has.
 *
 * <p>Only two steps call an LLM. Gathering evidence is plain code, and the final decision is the
 * LLM's draft run through {@link DisputePolicy}. The agent knows no payment provider: it is sent a
 * case and answers with a decision.
 */
@Agent(description = "Triages a payment dispute: gathers evidence, drafts a response and flags cases that need a human")
public class DisputeAgent {

    private final EvidenceCollector collector;
    private final DisputePolicy policy;
    private final Clock clock;
    private final LlmOptions extractor;
    private final LlmOptions drafter;

    public DisputeAgent(DisputeAgentProperties properties, List<EvidenceSource> evidenceSources, Clock clock) {
        this.collector = new EvidenceCollector(evidenceSources);
        this.policy = new DisputePolicy(properties.limits());
        this.clock = clock;
        this.extractor = LlmOptions.withModel(properties.extractLlm());
        this.drafter = LlmOptions.withModel(properties.draftLlm());
    }

    @Action
    public DisputeCase extractCase(UserInput userInput, OperationContext context) {
        return context.ai()
                .withLlm(extractor.withTemperature(0.0))
                .createObject(DisputePrompts.extractCase(userInput.getContent()), ExtractedCase.class)
                .toCase();
    }

    // FIRE_ONCE: evidence lookups talk to outside systems that have their own retries, and Embabel's default
    // of several attempts with backoff would hold the caller for minutes when one of them is down
    @Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)
    public EvidencePack gatherEvidence(DisputeCase dispute) {
        return collector.collect(dispute);
    }

    @Action
    @AchievesGoal(description = "Triages a payment dispute: evidence, recommendation, draft response and review flag.",
            // MCP tool names are limited to [a-zA-Z0-9_-] by common clients, so no spaces
            export = @Export(remote = true, name = "disputeTriage",
                    startingInputTypes = {UserInput.class, DisputeCase.class})
    )
    public DisputeDecision decide(DisputeCase dispute, EvidencePack evidence, OperationContext context) {
        var draft = context.ai()
                .withLlm(drafter.withTemperature(0.2))
                .createObject(DisputePrompts.draftResponse(dispute, evidence), DisputeDraft.class);
        return policy.apply(dispute, evidence, draft, clock.instant());
    }
}
