package io.github.hadirsa.payops.dispute.domain.model;

import io.github.hadirsa.payops.dispute.domain.exception.InvalidDisputeException;
import java.util.Locale;

/** Dispute reasons, named after Stripe's. Anything a provider adds later falls back to {@link #GENERAL}. */
public enum DisputeReason {
    BANK_CANNOT_PROCESS,
    CHECK_RETURNED,
    CREDIT_NOT_PROCESSED,
    CUSTOMER_INITIATED,
    DEBIT_NOT_AUTHORIZED,
    DUPLICATE,
    FRAUDULENT,
    GENERAL,
    INCORRECT_ACCOUNT_DETAILS,
    INSUFFICIENT_FUNDS,
    PRODUCT_NOT_ACCEPTABLE,
    PRODUCT_NOT_RECEIVED,
    SUBSCRIPTION_CANCELED,
    UNRECOGNIZED;

    /** Lenient parse of a provider's snake_case value, e.g. {@code product_not_received}; unknown means GENERAL. */
    public static DisputeReason parseLenient(String value) {
        if (value == null || value.isBlank()) {
            return GENERAL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return GENERAL;
        }
    }

    /** Strict parse for caller input: "product_not_received", "PRODUCT-NOT-RECEIVED" and so on. */
    public static DisputeReason parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidDisputeException("reason is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            throw new InvalidDisputeException("unknown reason '" + value + "'; expected one of "
                    + java.util.Arrays.stream(values()).map(r -> r.name().toLowerCase(Locale.ROOT)).toList());
        }
    }

    /** Human-readable form for prompts and responses, e.g. "product not received". */
    public String label() {
        return name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
