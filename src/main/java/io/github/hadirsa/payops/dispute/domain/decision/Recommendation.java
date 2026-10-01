package io.github.hadirsa.payops.dispute.domain.decision;

public enum Recommendation {
    /** Contest the dispute with the drafted response. */
    REPRESENT,
    /** Do not contest; the evidence does not support the merchant. */
    ACCEPT
}
