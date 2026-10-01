package io.github.hadirsa.payops.dispute.agent;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.core.ActionRetryPolicy;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.agent.test.unit.FakeOperationContext;
import io.github.hadirsa.payops.dispute.domain.decision.DisputeDraft;
import io.github.hadirsa.payops.dispute.domain.decision.Recommendation;
import io.github.hadirsa.payops.dispute.domain.decision.ReviewReason;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceItem;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceSource;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceType;
import io.github.hadirsa.payops.dispute.domain.evidence.ReasonCatalog;
import io.github.hadirsa.payops.dispute.domain.model.CaseOrigin;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.ExtractedCase;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import io.github.hadirsa.payops.dispute.domain.privacy.PanRedactor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks what each action sends to the LLM (prompt, model, temperature) using Embabel's fakes.
 * {@code LlmOptions.withModel} stores the name as a selection criterion, so assert on getModelName().
 */
class DisputeAgentTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    private final DisputeCase dispute = new DisputeCase("dp_1", "ch_1", DisputeReason.PRODUCT_NOT_RECEIVED,
            new Money(24_000, "USD"), NOW.plus(Duration.ofDays(10)));

    private final EvidenceSource fullSource = new EvidenceSource() {
        @Override
        public Set<EvidenceType> provides() {
            return Set.of(EvidenceType.values());
        }

        @Override
        public List<EvidenceItem> collect(DisputeCase d, Set<EvidenceType> wanted) {
            return wanted.stream().map(t -> EvidenceItem.found(t, "proof of " + t)).toList();
        }
    };

    private DisputeAgent agent(EvidenceSource... sources) {
        return new DisputeAgent(
                new DisputeAgentProperties("extract-model", "draft-model", new BigDecimal("1000"), 0.7, Duration.ofDays(2)),
                List.of(sources), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void extractCaseUsesExtractorColdMasksCardNumbersAndMarksTheCaseAsExtracted() {
        var context = FakeOperationContext.create();
        context.expectResponse(new ExtractedCase("dp_1", "ch_1", "product_not_received", new BigDecimal("240.00"),
                "USD", "2026-10-20"));

        var result = agent(fullSource).extractCase(
                new UserInput("dispute dp_1 on ch_1: card 4242 4242 4242 4242, $240, not received, reply by 2026-10-20"),
                context);

        assertEquals(new Money(24_000, "USD"), result.amount());
        assertEquals(CaseOrigin.EXTRACTED, result.origin());
        var invocation = context.getLlmInvocations().getFirst();
        assertTrue(invocation.getPrompt().contains("dp_1"));
        assertFalse(invocation.getPrompt().contains("4242 4242"), "card number must not reach the model");
        assertTrue(invocation.getPrompt().contains(PanRedactor.redact("4242 4242 4242 4242")));
        assertEquals("extract-model", invocation.getInteraction().getLlm().getModelName());
        assertEquals(0.0, invocation.getInteraction().getLlm().getTemperature(), 0.001);
    }

    @Test
    void gatherEvidenceNeedsNoLlm() {
        var context = FakeOperationContext.create();

        var pack = agent(fullSource).gatherEvidence(dispute);

        assertEquals(ReasonCatalog.requiredEvidence(DisputeReason.PRODUCT_NOT_RECEIVED).size(), pack.found().size());
        assertTrue(context.getLlmInvocations().isEmpty());
    }

    @Test
    void decideSendsEvidenceToTheDrafterAndAppliesPolicyToTheAnswer() {
        var agent = agent(fullSource);
        var context = FakeOperationContext.create();
        context.expectResponse(new DisputeDraft(Recommendation.REPRESENT, 0.9, "Delivered and signed.", "Dear issuer..."));

        var decision = agent.decide(dispute, agent.gatherEvidence(dispute), context);

        assertEquals(Recommendation.REPRESENT, decision.recommendation());
        assertFalse(decision.requiresHumanReview());
        assertEquals("Dear issuer...", decision.responseLetter());
        var invocation = context.getLlmInvocations().getFirst();
        assertTrue(invocation.getPrompt().contains("product not received"));
        assertTrue(invocation.getPrompt().contains("240.00 USD"));
        assertTrue(invocation.getPrompt().contains("proof of DELIVERY_PROOF"));
        assertEquals("draft-model", invocation.getInteraction().getLlm().getModelName());
        assertEquals(0.2, invocation.getInteraction().getLlm().getTemperature(), 0.001);
    }

    @Test
    void decideMarksMissingEvidenceInThePromptAndForcesReview() {
        var bare = agent();
        var context = FakeOperationContext.create();
        context.expectResponse(new DisputeDraft(Recommendation.REPRESENT, 0.95, "Confident.", "Letter"));

        var decision = bare.decide(dispute, bare.gatherEvidence(dispute), context);

        assertTrue(decision.requiresHumanReview());
        assertTrue(decision.reviewReasons().contains(ReviewReason.MISSING_REQUIRED_EVIDENCE));
        assertTrue(context.getLlmInvocations().getFirst().getPrompt().contains("DELIVERY_PROOF: MISSING"));
    }

    @Test
    void highValueDisputeNeedsReviewWhateverTheModelSays() {
        var big = new DisputeCase("dp_2", "ch_2", DisputeReason.FRAUDULENT, new Money(420_000, "USD"),
                NOW.plus(Duration.ofDays(10)));
        var agent = agent(fullSource);
        var context = FakeOperationContext.create();
        context.expectResponse(new DisputeDraft(Recommendation.REPRESENT, 1.0, "Sure.", "Letter"));

        var decision = agent.decide(big, agent.gatherEvidence(big), context);

        assertEquals(List.of(ReviewReason.AMOUNT_ABOVE_AUTO_LIMIT), decision.reviewReasons());
    }

    @Test
    void evidenceLookupIsNotRetriedButModelStepsAre() {
        // Regression: Embabel's default retries once held an HTTP call for minutes when a lookup kept failing
        assertEquals(ActionRetryPolicy.FIRE_ONCE, retryPolicy("gatherEvidence"));
        assertEquals(ActionRetryPolicy.DEFAULT, retryPolicy("extractCase"), "an LLM call, where a retry can help");
        assertEquals(ActionRetryPolicy.DEFAULT, retryPolicy("decide"), "an LLM call, where a retry can help");
    }

    private static ActionRetryPolicy retryPolicy(String method) {
        return Arrays.stream(DisputeAgent.class.getMethods())
                .filter(m -> m.getName().equals(method)).findFirst().orElseThrow()
                .getAnnotation(Action.class).actionRetryPolicy();
    }

    @Test
    void propertiesRejectNonsenseLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> new DisputeAgentProperties("m", "m", new BigDecimal("-1"), 0.7, Duration.ZERO).limits());
    }
}
