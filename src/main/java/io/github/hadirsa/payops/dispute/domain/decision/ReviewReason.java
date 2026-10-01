package io.github.hadirsa.payops.dispute.domain.decision;

/** Why a decision needs a person before anything is submitted. */
public enum ReviewReason {
    /** The amount and deadline were read from free text by the model and must be checked. */
    FIGURES_FROM_TEXT,
    AMOUNT_ABOVE_AUTO_LIMIT,
    MISSING_REQUIRED_EVIDENCE,
    LOW_CONFIDENCE,
    DEADLINE_PASSED,
    DEADLINE_SOON
}
