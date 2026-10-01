package io.github.hadirsa.payops.dispute.domain.evidence;

/**
 * One piece of evidence, or the record that it could not be found.
 *
 * @param summary what was found, or why nothing was (e.g. "order system not connected")
 */
public record EvidenceItem(EvidenceType type, boolean found, String summary) {

    public static EvidenceItem found(EvidenceType type, String summary) {
        return new EvidenceItem(type, true, summary);
    }

    public static EvidenceItem missing(EvidenceType type, String why) {
        return new EvidenceItem(type, false, why);
    }
}
