package io.github.hadirsa.payops.dispute.domain.model;

import io.github.hadirsa.payops.dispute.domain.exception.InvalidDisputeException;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * The message the agent is sent: one dispute, described without reference to any payment provider.
 * Adapters translate their provider's dispute into this (Stripe's is in {@code infra.stripe}).
 *
 * @param disputeId the provider's id for the dispute
 * @param paymentId the provider's id for the payment being disputed; evidence sources use it to look things up
 * @param respondBy the moment the response window closes
 * @param origin    {@code null} means {@link CaseOrigin#STRUCTURED}
 */
public record DisputeCase(
        String disputeId,
        String paymentId,
        DisputeReason reason,
        Money amount,
        Instant respondBy,
        CaseOrigin origin
) {

    private static final Pattern ID = Pattern.compile("[\\w-]{1,100}");

    public DisputeCase {
        requireId("disputeId", disputeId);
        requireId("paymentId", paymentId);
        if (reason == null) {
            throw new InvalidDisputeException("reason is required");
        }
        if (amount == null) {
            throw new InvalidDisputeException("amount is required");
        }
        if (respondBy == null) {
            throw new InvalidDisputeException("respondBy is required");
        }
        disputeId = disputeId.trim();
        paymentId = paymentId.trim();
        origin = origin == null ? CaseOrigin.STRUCTURED : origin;
    }

    /** A case sent as structured data. */
    public DisputeCase(String disputeId, String paymentId, DisputeReason reason, Money amount, Instant respondBy) {
        this(disputeId, paymentId, reason, amount, respondBy, CaseOrigin.STRUCTURED);
    }

    public static void requireId(String field, String value) {
        if (value == null || !ID.matcher(value.trim()).matches()) {
            throw new InvalidDisputeException(field + " must be 1-100 letters, digits, '_' or '-': " + value);
        }
    }
}
