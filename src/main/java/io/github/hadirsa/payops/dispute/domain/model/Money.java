package io.github.hadirsa.payops.dispute.domain.model;

import io.github.hadirsa.payops.dispute.domain.exception.InvalidDisputeException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

/**
 * An amount in the currency's smallest unit, as payment providers report it (cents for USD, yen for JPY).
 * Never a floating point number.
 */
public record Money(long minorUnits, String currency) {

    public Money {
        if (minorUnits < 0) {
            throw new InvalidDisputeException("amount must not be negative: " + minorUnits);
        }
        if (currency == null || currency.isBlank()) {
            throw new InvalidDisputeException("currency is required");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            throw new InvalidDisputeException("not an ISO 4217 currency: " + currency);
        }
    }

    /** Builds an amount from major units (240.00 USD). Refuses figures with more decimals than the currency has. */
    public static Money ofMajor(BigDecimal major, String currency) {
        if (major == null || currency == null || currency.isBlank()) {
            throw new InvalidDisputeException("amount and currency are required");
        }
        Currency parsed;
        try {
            parsed = Currency.getInstance(currency.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidDisputeException("not an ISO 4217 currency: " + currency);
        }
        int digits = Math.max(parsed.getDefaultFractionDigits(), 0);
        try {
            return new Money(major.setScale(digits, RoundingMode.UNNECESSARY).movePointRight(digits).longValueExact(),
                    parsed.getCurrencyCode());
        } catch (ArithmeticException e) {
            throw new InvalidDisputeException("amount " + major.toPlainString() + " does not fit " + parsed.getCurrencyCode()
                    + " (" + digits + " decimals)");
        }
    }

    /** The amount in major units, using the currency's own number of decimals (2 for USD, 0 for JPY). */
    public BigDecimal major() {
        int digits = Math.max(Currency.getInstance(currency).getDefaultFractionDigits(), 0);
        return BigDecimal.valueOf(minorUnits, digits);
    }

    public String display() {
        return major().toPlainString() + " " + currency;
    }
}
