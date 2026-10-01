package io.github.hadirsa.payops.dispute.domain.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.hadirsa.payops.dispute.domain.exception.InvalidDisputeException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * What the LLM reads out of a free-text dispute description. Incomplete or inconsistent text fails
 * here (as {@link InvalidDisputeException}) rather than producing a case with guessed figures.
 */
@JsonClassDescription("A payment dispute described in free text")
public record ExtractedCase(
        @JsonPropertyDescription("The dispute id exactly as written, e.g. dp_1Abc23 or DSP-2041")
        String disputeId,
        @JsonPropertyDescription("The id of the payment or charge being disputed, exactly as written")
        String paymentId,
        @JsonPropertyDescription("Why the customer disputed: one of fraudulent, product_not_received, duplicate, "
                + "credit_not_processed, subscription_canceled, product_not_acceptable, unrecognized, general")
        String reason,
        @JsonPropertyDescription("The disputed amount as a plain decimal number in major units, e.g. 240.00")
        BigDecimal amount,
        @JsonPropertyDescription("The ISO 4217 currency code of the amount, e.g. USD")
        String currency,
        @JsonPropertyDescription("The last day to respond, as an ISO-8601 date (YYYY-MM-DD)")
        String respondBy
) {

    public ExtractedCase {
        build(disputeId, paymentId, reason, amount, currency, respondBy); // fail early, never keep a half-read case
    }

    public DisputeCase toCase() {
        return build(disputeId, paymentId, reason, amount, currency, respondBy);
    }

    private static DisputeCase build(String disputeId, String paymentId, String reason, BigDecimal amount,
                                     String currency, String respondBy) {
        if (amount == null || currency == null) {
            throw new InvalidDisputeException("amount and currency are required");
        }
        return new DisputeCase(disputeId, paymentId, DisputeReason.parse(reason),
                Money.ofMajor(amount, currency), deadline(respondBy), CaseOrigin.EXTRACTED);
    }

    private static Instant deadline(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidDisputeException("respondBy is required");
        }
        try {
            return Instant.parse(text.trim());
        } catch (DateTimeParseException notAnInstant) {
            try {
                // A bare date means "by the end of that day"
                return LocalDate.parse(text.trim()).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException e) {
                throw new InvalidDisputeException("respondBy is not an ISO-8601 date: " + text);
            }
        }
    }
}
