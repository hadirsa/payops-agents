package io.github.hadirsa.payops.dispute.domain.model;

import io.github.hadirsa.payops.dispute.domain.exception.InvalidDisputeException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The dispute message: what a caller or the model may send, and what is refused. */
class CaseMessageTest {

    private static final Instant DUE = Instant.parse("2026-10-20T00:00:00Z");
    private static final Money MONEY = new Money(24_000, "USD");

    @Test
    void structuredCaseKeepsItsFieldsAndDefaultsToStructuredOrigin() {
        var dispute = new DisputeCase(" dp_1 ", "ch-1", DisputeReason.FRAUDULENT, MONEY, DUE, null);

        assertEquals("dp_1", dispute.disputeId());
        assertEquals(CaseOrigin.STRUCTURED, dispute.origin());
        assertEquals(CaseOrigin.STRUCTURED,
                new DisputeCase("dp_1", "ch_1", DisputeReason.FRAUDULENT, MONEY, DUE).origin());
    }

    @Test
    void caseRefusesMissingOrMalformedFields() {
        assertThrows(InvalidDisputeException.class, () -> new DisputeCase(null, "ch_1", DisputeReason.GENERAL, MONEY, DUE));
        assertThrows(InvalidDisputeException.class, () -> new DisputeCase("dp 1", "ch_1", DisputeReason.GENERAL, MONEY, DUE));
        assertThrows(InvalidDisputeException.class, () -> new DisputeCase("dp_1", " ", DisputeReason.GENERAL, MONEY, DUE));
        assertThrows(InvalidDisputeException.class, () -> new DisputeCase("x".repeat(101), "ch_1", DisputeReason.GENERAL, MONEY, DUE));
        assertThrows(InvalidDisputeException.class, () -> new DisputeCase("dp_1", "ch_1", null, MONEY, DUE));
        assertThrows(InvalidDisputeException.class, () -> new DisputeCase("dp_1", "ch_1", DisputeReason.GENERAL, null, DUE));
        assertThrows(InvalidDisputeException.class, () -> new DisputeCase("dp_1", "ch_1", DisputeReason.GENERAL, MONEY, null));
    }

    @Test
    void moneyFromMajorUnitsUsesTheCurrencysDecimals() {
        assertEquals(new Money(24_000, "USD"), Money.ofMajor(new BigDecimal("240"), "usd"));
        assertEquals(new Money(24_050, "USD"), Money.ofMajor(new BigDecimal("240.50"), "USD"));
        assertEquals(new Money(1_000, "JPY"), Money.ofMajor(new BigDecimal("1000"), "JPY"));
    }

    @Test
    void moneyFromMajorUnitsRefusesFiguresThatWouldBeRounded() {
        assertThrows(InvalidDisputeException.class, () -> Money.ofMajor(new BigDecimal("240.505"), "USD"));
        assertThrows(InvalidDisputeException.class, () -> Money.ofMajor(new BigDecimal("10.5"), "JPY"));
        assertThrows(InvalidDisputeException.class, () -> Money.ofMajor(new BigDecimal("-1"), "USD"));
        assertThrows(InvalidDisputeException.class, () -> Money.ofMajor(BigDecimal.ONE, "DOLLARS"));
        assertThrows(InvalidDisputeException.class, () -> Money.ofMajor(null, "USD"));
    }

    @Test
    void reasonParsingIsStrictForCallersAndLenientForProviders() {
        assertEquals(DisputeReason.PRODUCT_NOT_RECEIVED, DisputeReason.parse("Product-Not-Received"));
        assertEquals(DisputeReason.PRODUCT_NOT_RECEIVED, DisputeReason.parse(" product not received "));
        var e = assertThrows(InvalidDisputeException.class, () -> DisputeReason.parse("stolen"));
        assertTrue(e.getMessage().contains("fraudulent"), "says what is allowed");
        assertThrows(InvalidDisputeException.class, () -> DisputeReason.parse(null));
        assertEquals(DisputeReason.GENERAL, DisputeReason.parseLenient("a_reason_added_next_year"));
    }

    @Test
    void extractedCaseConvertsTextFiguresAndIsMarkedAsExtracted() {
        var dispute = new ExtractedCase("dp_1", "ch_1", "product_not_received", new BigDecimal("240.00"), "usd",
                "2026-10-20").toCase();

        assertEquals(new Money(24_000, "USD"), dispute.amount());
        assertEquals(DisputeReason.PRODUCT_NOT_RECEIVED, dispute.reason());
        assertEquals(CaseOrigin.EXTRACTED, dispute.origin());
        // a bare date means the end of that day, so the deadline is the start of the next one
        assertEquals(Instant.parse("2026-10-21T00:00:00Z"), dispute.respondBy());
    }

    @Test
    void extractedCaseAcceptsAFullTimestampDeadline() {
        var dispute = new ExtractedCase("dp_1", "ch_1", "general", BigDecimal.TEN, "USD", "2026-10-20T15:30:00Z").toCase();

        assertEquals(Instant.parse("2026-10-20T15:30:00Z"), dispute.respondBy());
    }

    @Test
    void textThatLacksFiguresFailsInsteadOfProducingAGuessedCase() {
        assertThrows(InvalidDisputeException.class,
                () -> new ExtractedCase("dp_1", "ch_1", "general", null, "USD", "2026-10-20"));
        assertThrows(InvalidDisputeException.class,
                () -> new ExtractedCase("dp_1", "ch_1", "general", BigDecimal.TEN, null, "2026-10-20"));
        assertThrows(InvalidDisputeException.class,
                () -> new ExtractedCase("dp_1", "ch_1", "general", BigDecimal.TEN, "USD", null));
        assertThrows(InvalidDisputeException.class,
                () -> new ExtractedCase("dp_1", "ch_1", "general", BigDecimal.TEN, "USD", "next Friday"));
        assertThrows(InvalidDisputeException.class,
                () -> new ExtractedCase(null, "ch_1", "general", BigDecimal.TEN, "USD", "2026-10-20"));
    }
}
