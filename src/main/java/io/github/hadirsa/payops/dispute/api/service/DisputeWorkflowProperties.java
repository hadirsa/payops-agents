package io.github.hadirsa.payops.dispute.api.service;

import io.github.hadirsa.payops.dispute.domain.port.DecisionSink;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * What happens to a decision after the agent has produced it.
 *
 * @param publishDecisions hand every decision to the configured {@code DecisionSink}s (for Stripe that saves
 *                         a draft; it never sends anything to the card issuer)
 * @param autoApprove      also approve, but only when the decision needs no human review and is REPRESENT.
 *                         Approval is final, so this stays off unless you turn it on.
 */
@ConfigurationProperties("dispute.workflow")
public record DisputeWorkflowProperties(
        @DefaultValue("true") boolean publishDecisions,
        @DefaultValue("false") boolean autoApprove
) {}
