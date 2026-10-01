package io.github.hadirsa.payops.dispute.infra.stripe;

import com.stripe.model.Dispute;
import io.github.hadirsa.payops.dispute.domain.exception.InvalidDisputeException;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import java.time.Instant;

/** Translates Stripe's dispute into the provider-neutral message the agent is sent. */
final class StripeDisputeMapper {

    private StripeDisputeMapper() {}

    static DisputeCase toCase(Dispute dispute) {
        if (dispute.getAmount() == null || dispute.getCurrency() == null) {
            throw new InvalidDisputeException("Stripe dispute " + dispute.getId() + " has no amount or currency");
        }
        var dueBy = dispute.getEvidenceDetails() == null ? null : dispute.getEvidenceDetails().getDueBy();
        return new DisputeCase(
                dispute.getId(),
                dispute.getCharge() != null ? dispute.getCharge() : dispute.getPaymentIntent(),
                DisputeReason.parseLenient(dispute.getReason()),
                new Money(dispute.getAmount(), dispute.getCurrency()),
                // A dispute with no deadline is already closed for evidence; the policy flags it for review
                dueBy == null ? Instant.EPOCH : Instant.ofEpochSecond(dueBy));
    }
}
