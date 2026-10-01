package io.github.hadirsa.payops.dispute.domain.evidence;

import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Collects the evidence a dispute reason requires from the registered {@link EvidenceSource}s.
 * Every required type ends up in the pack, found or not, so nothing is silently dropped.
 */
public final class EvidenceCollector {

    private final List<EvidenceSource> sources;

    public EvidenceCollector(List<EvidenceSource> sources) {
        this.sources = List.copyOf(sources);
    }

    public EvidencePack collect(DisputeCase dispute) {
        var required = ReasonCatalog.requiredEvidence(dispute.reason());
        var items = new ArrayList<EvidenceItem>();
        for (var type : required) {
            items.add(collectOne(dispute, type));
        }
        return new EvidencePack(items);
    }

    private EvidenceItem collectOne(DisputeCase dispute, EvidenceType type) {
        var reasons = new ArrayList<String>();
        for (var source : sources) {
            if (!source.provides().contains(type)) {
                continue;
            }
            var results = source.collect(dispute, EnumSet.of(type)).stream()
                    .filter(i -> i.type() == type)
                    .toList();
            for (var item : results) {
                if (item.found()) {
                    return item;
                }
                reasons.add(item.summary());
            }
        }
        return EvidenceItem.missing(type, reasons.isEmpty()
                ? "no evidence source configured for " + type
                : String.join("; ", reasons));
    }

    /** Types no registered source can provide, useful for a startup warning. */
    public Set<EvidenceType> uncovered() {
        var all = EnumSet.allOf(EvidenceType.class);
        sources.forEach(s -> all.removeAll(s.provides()));
        return all;
    }
}
