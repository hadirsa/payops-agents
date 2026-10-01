package io.github.hadirsa.payops.dispute.domain.model;

/** Where a case's figures came from. Figures an LLM read from free text need a person to check them. */
public enum CaseOrigin {
    /** Sent as structured data by a caller or translated from a provider event. */
    STRUCTURED,
    /** Read out of free text by the model. */
    EXTRACTED
}
