package io.github.hadirsa.payops.dispute.domain.evidence;

import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceCollectorTest {

    private final DisputeCase notReceived = new DisputeCase("dp_1", "ch_1",
            DisputeReason.PRODUCT_NOT_RECEIVED, new Money(10_000, "USD"), Instant.parse("2026-10-20T00:00:00Z"));

    private static EvidenceSource source(Set<EvidenceType> provides, EvidenceItem... items) {
        return new EvidenceSource() {
            @Override
            public Set<EvidenceType> provides() {
                return provides;
            }

            @Override
            public List<EvidenceItem> collect(DisputeCase dispute, Set<EvidenceType> wanted) {
                return List.of(items).stream().filter(i -> wanted.contains(i.type())).toList();
            }
        };
    }

    @Test
    void everyRequiredTypeAppearsInThePackInCatalogOrder() {
        var pack = new EvidenceCollector(List.of()).collect(notReceived);

        assertEquals(ReasonCatalog.requiredEvidence(DisputeReason.PRODUCT_NOT_RECEIVED),
                pack.items().stream().map(EvidenceItem::type).toList());
        assertTrue(pack.found().isEmpty());
    }

    @Test
    void typeWithNoSourceIsReportedMissingAndSaysWhy() {
        var pack = new EvidenceCollector(List.of()).collect(notReceived);

        assertTrue(pack.items().getFirst().summary().contains("no evidence source configured"));
    }

    @Test
    void findsEvidenceFromTheSourcesThatProvideIt() {
        var stripe = source(Set.of(EvidenceType.RECEIPT), EvidenceItem.found(EvidenceType.RECEIPT, "receipt url"));
        var orders = source(Set.of(EvidenceType.DELIVERY_PROOF, EvidenceType.CUSTOMER_COMMUNICATION),
                EvidenceItem.found(EvidenceType.DELIVERY_PROOF, "signed by R. Khan"));

        var pack = new EvidenceCollector(List.of(stripe, orders)).collect(notReceived);

        assertEquals(List.of(EvidenceType.RECEIPT, EvidenceType.DELIVERY_PROOF),
                pack.found().stream().map(EvidenceItem::type).toList());
        assertEquals(List.of(EvidenceType.CUSTOMER_COMMUNICATION), pack.missingTypes());
    }

    @Test
    void laterSourceCanFillWhatAnEarlierOneCouldNot() {
        var first = source(Set.of(EvidenceType.RECEIPT), EvidenceItem.missing(EvidenceType.RECEIPT, "charge has no receipt"));
        var second = source(Set.of(EvidenceType.RECEIPT), EvidenceItem.found(EvidenceType.RECEIPT, "from order system"));

        var pack = new EvidenceCollector(List.of(first, second)).collect(notReceived);

        assertEquals("from order system", pack.items().getFirst().summary());
    }

    @Test
    void missingItemKeepsEverySourcesReason() {
        var first = source(Set.of(EvidenceType.RECEIPT), EvidenceItem.missing(EvidenceType.RECEIPT, "charge has no receipt"));
        var second = source(Set.of(EvidenceType.RECEIPT), EvidenceItem.missing(EvidenceType.RECEIPT, "order system not connected"));

        var pack = new EvidenceCollector(List.of(first, second)).collect(notReceived);

        assertEquals("charge has no receipt; order system not connected", pack.items().getFirst().summary());
    }

    @Test
    void reportsTypesNoSourceCovers() {
        var stripe = source(Set.of(EvidenceType.RECEIPT, EvidenceType.AUTHORIZATION_CHECKS));

        var uncovered = new EvidenceCollector(List.of(stripe)).uncovered();

        assertFalse(uncovered.contains(EvidenceType.RECEIPT));
        assertTrue(uncovered.contains(EvidenceType.DELIVERY_PROOF));
    }
}
