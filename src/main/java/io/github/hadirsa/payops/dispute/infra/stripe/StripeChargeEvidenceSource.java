package io.github.hadirsa.payops.dispute.infra.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceItem;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceSource;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceType;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeGatewayException;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Evidence that lives on the Stripe charge itself: the receipt and the card checks. The case's paymentId is the charge id. */
@Component
@ConditionalOnProperty(name = "dispute.provider", havingValue = "stripe")
public class StripeChargeEvidenceSource implements EvidenceSource {

    private final StripeClient stripe;

    public StripeChargeEvidenceSource(StripeClient stripe) {
        this.stripe = stripe;
    }

    @Override
    public Set<EvidenceType> provides() {
        return Set.of(EvidenceType.RECEIPT, EvidenceType.AUTHORIZATION_CHECKS);
    }

    @Override
    public List<EvidenceItem> collect(DisputeCase dispute, Set<EvidenceType> wanted) {
        Charge charge;
        try {
            charge = stripe.v1().charges().retrieve(dispute.paymentId());
        } catch (StripeException e) {
            throw new DisputeGatewayException("Could not read payment " + dispute.paymentId(), e);
        }
        var items = new ArrayList<EvidenceItem>();
        if (wanted.contains(EvidenceType.RECEIPT)) {
            items.add(receipt(charge));
        }
        if (wanted.contains(EvidenceType.AUTHORIZATION_CHECKS)) {
            items.add(authorizationChecks(charge));
        }
        return items;
    }

    static EvidenceItem receipt(Charge charge) {
        return charge.getReceiptUrl() == null
                ? EvidenceItem.missing(EvidenceType.RECEIPT, "the charge has no receipt URL")
                : EvidenceItem.found(EvidenceType.RECEIPT, "Stripe receipt: " + charge.getReceiptUrl());
    }

    static EvidenceItem authorizationChecks(Charge charge) {
        var card = charge.getPaymentMethodDetails() == null ? null : charge.getPaymentMethodDetails().getCard();
        if (card == null || card.getChecks() == null) {
            return EvidenceItem.missing(EvidenceType.AUTHORIZATION_CHECKS, "the charge has no card check results");
        }
        var checks = card.getChecks();
        var text = new StringBuilder("CVC check: ").append(orUnknown(checks.getCvcCheck()))
                .append(", postal code check: ").append(orUnknown(checks.getAddressPostalCodeCheck()))
                .append(", address line check: ").append(orUnknown(checks.getAddressLine1Check()));
        if (card.getThreeDSecure() != null && card.getThreeDSecure().getResult() != null) {
            text.append(", 3D Secure: ").append(card.getThreeDSecure().getResult());
        }
        return EvidenceItem.found(EvidenceType.AUTHORIZATION_CHECKS, text.toString());
    }

    private static String orUnknown(String check) {
        return check == null ? "unknown" : check;
    }
}
