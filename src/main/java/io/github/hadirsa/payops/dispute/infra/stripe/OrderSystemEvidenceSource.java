package io.github.hadirsa.payops.dispute.infra.stripe;

import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceCollector;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceItem;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceSource;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceType;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import io.github.hadirsa.payops.dispute.domain.policy.DisputePolicy;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Placeholder for the merchant's own systems: shipping, customer history, support email, terms.
 * Stripe knows none of this, so until a real source is wired in these are reported as missing and
 * {@code DisputePolicy} sends the case to a person.
 *
 * <p>To connect your systems, implement {@link EvidenceSource} for the types you can answer and
 * register it as a bean. Both can stay registered: {@code EvidenceCollector} uses the first source that
 * finds evidence, and this placeholder only contributes the "not connected" reason when none does.
 */
@Component
@ConditionalOnProperty(name = "dispute.provider", havingValue = "stripe")
public class OrderSystemEvidenceSource implements EvidenceSource {

    @Override
    public Set<EvidenceType> provides() {
        return Set.of(EvidenceType.DELIVERY_PROOF, EvidenceType.CUSTOMER_HISTORY,
                EvidenceType.CUSTOMER_COMMUNICATION, EvidenceType.POLICY_DISCLOSURE);
    }

    @Override
    public List<EvidenceItem> collect(DisputeCase dispute, Set<EvidenceType> wanted) {
        return wanted.stream()
                .map(type -> EvidenceItem.missing(type, "order system not connected"))
                .toList();
    }
}
