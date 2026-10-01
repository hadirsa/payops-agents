package io.github.hadirsa.payops.dispute.domain;

import io.github.hadirsa.payops.dispute.domain.decision.DisputeDraft;
import io.github.hadirsa.payops.dispute.domain.decision.Recommendation;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceType;
import io.github.hadirsa.payops.dispute.domain.evidence.ReasonCatalog;
import io.github.hadirsa.payops.dispute.domain.exception.InvalidDisputeException;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import io.github.hadirsa.payops.dispute.domain.policy.PolicyLimits;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValueTypesTest {

    @Test
    void moneyUsesTheCurrencysOwnDecimals() {
        assertEquals(new BigDecimal("240.00"), new Money(24_000, "usd").major());
        assertEquals(new BigDecimal("1000"), new Money(1_000, "JPY").major());
        assertEquals("240.00 USD", new Money(24_000, "usd").display());
    }

    @Test
    void moneyRejectsNegativeAmountsAndUnknownCurrencies() {
        assertThrows(InvalidDisputeException.class, () -> new Money(-1, "USD"));
        assertThrows(InvalidDisputeException.class, () -> new Money(1, "  "));
        assertThrows(InvalidDisputeException.class, () -> new Money(1, "XXZ"));
    }

    @Test
    void unknownStripeReasonsFallBackToGeneral() {
        assertEquals(DisputeReason.PRODUCT_NOT_RECEIVED, DisputeReason.parseLenient("product_not_received"));
        assertEquals(DisputeReason.GENERAL, DisputeReason.parseLenient("something_new"));
        assertEquals(DisputeReason.GENERAL, DisputeReason.parseLenient(null));
        assertEquals("product not received", DisputeReason.PRODUCT_NOT_RECEIVED.label());
    }

    @Test
    void draftClampsConfidenceAndNormalisesNulls() {
        var high = new DisputeDraft(Recommendation.REPRESENT, 7, null, null);
        var nan = new DisputeDraft(Recommendation.ACCEPT, Double.NaN, "r", "l");

        assertEquals(1.0, high.confidence());
        assertEquals("", high.rationale());
        assertEquals("", high.responseLetter());
        assertEquals(0.0, nan.confidence());
        assertThrows(IllegalArgumentException.class, () -> new DisputeDraft(null, 0.5, "r", "l"));
    }

    @Test
    void policyLimitsRejectNonsense() {
        assertThrows(IllegalArgumentException.class, () -> new PolicyLimits(new BigDecimal("-1"), 0.5, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new PolicyLimits(BigDecimal.ONE, 1.5, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new PolicyLimits(BigDecimal.ONE, 0.5, Duration.ofDays(-1)));
    }

    @Test
    void everyReasonHasRequiredEvidenceIncludingAReceipt() {
        for (var reason : DisputeReason.values()) {
            assertTrue(ReasonCatalog.requiredEvidence(reason).contains(EvidenceType.RECEIPT), reason.name());
        }
    }
}
