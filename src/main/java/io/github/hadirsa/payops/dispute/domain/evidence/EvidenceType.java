package io.github.hadirsa.payops.dispute.domain.evidence;

/** Kinds of evidence a dispute response can rest on. */
public enum EvidenceType {
    RECEIPT,
    AUTHORIZATION_CHECKS,
    DELIVERY_PROOF,
    CUSTOMER_HISTORY,
    CUSTOMER_COMMUNICATION,
    POLICY_DISCLOSURE
}
