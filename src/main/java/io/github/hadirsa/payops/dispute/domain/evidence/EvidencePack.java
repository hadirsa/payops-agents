package io.github.hadirsa.payops.dispute.domain.evidence;

import java.util.List;

/** Everything gathered for a dispute: one item per required evidence type. */
public record EvidencePack(List<EvidenceItem> items) {

    public EvidencePack {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public List<EvidenceItem> found() {
        return items.stream().filter(EvidenceItem::found).toList();
    }

    public List<EvidenceType> missingTypes() {
        return items.stream().filter(i -> !i.found()).map(EvidenceItem::type).toList();
    }
}
