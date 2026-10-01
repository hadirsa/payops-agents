package io.github.hadirsa.payops.dispute.infra.demo;

import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceItem;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceSource;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceType;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Serves the evidence behind the sample disputes; stands in for the payment provider and the order system. */
@Component
@ConditionalOnProperty(name = "dispute.provider", havingValue = "demo", matchIfMissing = true)
public class DemoEvidenceSource implements EvidenceSource {

    private final Map<String, DemoCases.Sample> samples;

    public DemoEvidenceSource(Clock clock) {
        this.samples = DemoCases.all(clock);
    }

    @Override
    public Set<EvidenceType> provides() {
        return Set.of(EvidenceType.values());
    }

    @Override
    public List<EvidenceItem> collect(DisputeCase dispute, Set<EvidenceType> wanted) {
        var sample = samples.get(dispute.disputeId());
        return wanted.stream()
                .map(type -> sample != null && sample.evidence().containsKey(type)
                        ? EvidenceItem.found(type, sample.evidence().get(type))
                        : EvidenceItem.missing(type, "not present in the demo data"))
                .toList();
    }
}
