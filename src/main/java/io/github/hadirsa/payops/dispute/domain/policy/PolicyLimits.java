package io.github.hadirsa.payops.dispute.domain.policy;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Thresholds for {@link DisputePolicy}.
 *
 * @param autoDecisionLimit largest amount, in the dispute's own currency, that may skip human review.
 *                          No currency conversion happens; convert upstream if you mix currencies.
 * @param minConfidence     below this the draft always needs review
 * @param deadlineWarning   a response due sooner than this needs a person to look now
 */
public record PolicyLimits(BigDecimal autoDecisionLimit, double minConfidence, Duration deadlineWarning) {

    public PolicyLimits {
        if (autoDecisionLimit == null || autoDecisionLimit.signum() < 0) {
            throw new IllegalArgumentException("autoDecisionLimit must be zero or more");
        }
        if (minConfidence < 0.0 || minConfidence > 1.0) {
            throw new IllegalArgumentException("minConfidence must be between 0 and 1");
        }
        if (deadlineWarning == null || deadlineWarning.isNegative()) {
            throw new IllegalArgumentException("deadlineWarning must be zero or more");
        }
    }
}
