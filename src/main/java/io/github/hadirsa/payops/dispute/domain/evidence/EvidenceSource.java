package io.github.hadirsa.payops.dispute.domain.evidence;

import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import java.util.List;
import java.util.Set;

/**
 * Port for one system that can supply evidence (the payment provider, your order system, ...).
 * This is the main extension point: implement it against your own systems.
 */
public interface EvidenceSource {

    /** The evidence types this source can answer for. */
    Set<EvidenceType> provides();

    /** Looks up evidence of the requested types; only those in {@link #provides()} are asked for. */
    List<EvidenceItem> collect(DisputeCase dispute, Set<EvidenceType> wanted);
}
