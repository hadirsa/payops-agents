package io.github.hadirsa.payops.dispute.api.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Public request body. Either {@code dispute} (a structured message; skips extraction) or {@code text}
 * (free-form, the agent reads the dispute out of it), never both. Size limits bound prompt cost.
 */
public record CreateDisputeRequest(
        @Size(max = 1000) String text,
        @Valid DisputePayload dispute
) {

    public record DisputePayload(
            @NotBlank @Size(max = 100) String disputeId,
            @NotBlank @Size(max = 100) String paymentId,
            @NotBlank @Size(max = 50) String reason,
            @NotNull @Valid AmountPayload amount,
            @NotNull Instant respondBy
    ) {

        DisputeCase toCase() {
            return new DisputeCase(disputeId, paymentId, DisputeReason.parse(reason),
                    Money.ofMajor(amount.value(), amount.currency()), respondBy);
        }
    }

    /** Major units, e.g. {@code 240.00 USD}. */
    public record AmountPayload(
            @NotNull @DecimalMin("0") BigDecimal value,
            @NotBlank @Size(min = 3, max = 3) String currency
    ) {}

    @JsonIgnore
    @AssertTrue(message = "provide either 'text' or 'dispute', not both")
    public boolean isEitherTextOrDispute() {
        return hasText() != (dispute != null);
    }

    boolean hasText() {
        return text != null && !text.isBlank();
    }
}
