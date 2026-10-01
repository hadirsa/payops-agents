package io.github.hadirsa.payops.dispute.infra.demo;

import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceType;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.github.hadirsa.payops.dispute.domain.evidence.EvidenceType.*;

/**
 * Sample disputes and the evidence behind them, for trying the agent without any payment provider.
 * Deadlines are relative to the clock so the samples never go stale.
 */
final class DemoCases {

    record Sample(DisputeCase dispute, Map<EvidenceType, String> evidence) {}

    private DemoCases() {}

    static Map<String, Sample> all(Clock clock) {
        var now = clock.instant();
        var samples = new LinkedHashMap<String, Sample>();
        samples.put("dp_demo_fraud", new Sample(
                new DisputeCase("dp_demo_fraud", "ch_demo_1", DisputeReason.FRAUDULENT,
                        new Money(24_000, "USD"), now.plus(Duration.ofDays(12))),
                Map.of(RECEIPT, "Receipt emailed to the cardholder on the day of purchase",
                        AUTHORIZATION_CHECKS, "CVC check passed, postal code check passed, 3D Secure authenticated",
                        CUSTOMER_HISTORY, "3 earlier orders from the same customer and device, none disputed")));
        samples.put("dp_demo_not_received", new Sample(
                new DisputeCase("dp_demo_not_received", "ch_demo_2", DisputeReason.PRODUCT_NOT_RECEIVED,
                        new Money(8_990, "EUR"), now.plus(Duration.ofDays(9))),
                Map.of(RECEIPT, "Receipt emailed to the cardholder on the day of purchase",
                        DELIVERY_PROOF, "Carrier shows delivered 12 days ago, signed by R. Khan at the billing address",
                        CUSTOMER_COMMUNICATION, "Customer confirmed the delivery address by email before dispatch")));
        samples.put("dp_demo_duplicate", new Sample(
                new DisputeCase("dp_demo_duplicate", "ch_demo_3", DisputeReason.DUPLICATE,
                        new Money(4_900, "USD"), now.plus(Duration.ofDays(14))),
                Map.of(RECEIPT, "Two receipts issued: one per separate order",
                        CUSTOMER_HISTORY, "Orders A-1001 and A-1002 were placed 2 minutes apart for different items")));
        samples.put("dp_demo_high_value", new Sample(
                new DisputeCase("dp_demo_high_value", "ch_demo_4", DisputeReason.FRAUDULENT,
                        new Money(420_000, "USD"), now.plus(Duration.ofDays(10))),
                Map.of(RECEIPT, "Receipt emailed to the cardholder on the day of purchase",
                        AUTHORIZATION_CHECKS, "CVC check passed, postal code check failed",
                        CUSTOMER_HISTORY, "First order from this customer")));
        samples.put("dp_demo_missing_evidence", new Sample(
                new DisputeCase("dp_demo_missing_evidence", "ch_demo_5", DisputeReason.PRODUCT_NOT_RECEIVED,
                        new Money(12_000, "USD"), now.plus(Duration.ofDays(8))),
                Map.of(RECEIPT, "Receipt emailed to the cardholder on the day of purchase")));
        return samples;
    }
}
