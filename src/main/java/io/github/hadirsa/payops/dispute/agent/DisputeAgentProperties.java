package io.github.hadirsa.payops.dispute.agent;

import io.github.hadirsa.payops.dispute.domain.policy.PolicyLimits;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Models must also be listed under the provider's {@code models:} or the call fails at runtime.
 *
 * @param extractLlm        model that pulls the dispute id out of free text (run cold)
 * @param draftLlm          model that writes the recommendation and response letter
 * @param autoDecisionLimit largest amount, in the dispute's own currency, that may skip human review
 * @param minConfidence     drafts below this confidence always need review
 * @param deadlineWarning   disputes due sooner than this always need review
 */
@ConfigurationProperties("dispute.agent")
public record DisputeAgentProperties(
        @DefaultValue("gpt-4o-mini") String extractLlm,
        @DefaultValue("gpt-4o-mini") String draftLlm,
        @DefaultValue("1000") BigDecimal autoDecisionLimit,
        @DefaultValue("0.7") double minConfidence,
        @DefaultValue("2d") Duration deadlineWarning
) {

    public PolicyLimits limits() {
        return new PolicyLimits(autoDecisionLimit, minConfidence, deadlineWarning);
    }
}
