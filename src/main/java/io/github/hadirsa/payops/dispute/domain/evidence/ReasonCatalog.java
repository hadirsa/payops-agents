package io.github.hadirsa.payops.dispute.domain.evidence;

import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import java.util.List;

import static io.github.hadirsa.payops.dispute.domain.evidence.EvidenceType.*;

/**
 * Which evidence each dispute reason needs. The LLM never decides this.
 *
 * <p>This table is a deliberately simplified starting point, not card network rules. Someone who
 * handles disputes should review and extend it before it is used with real money.
 */
public final class ReasonCatalog {

    private ReasonCatalog() {}

    public static List<EvidenceType> requiredEvidence(DisputeReason reason) {
        return switch (reason) {
            case FRAUDULENT, UNRECOGNIZED -> List.of(RECEIPT, AUTHORIZATION_CHECKS, CUSTOMER_HISTORY);
            case PRODUCT_NOT_RECEIVED -> List.of(RECEIPT, DELIVERY_PROOF, CUSTOMER_COMMUNICATION);
            case DUPLICATE -> List.of(RECEIPT, CUSTOMER_HISTORY);
            case PRODUCT_NOT_ACCEPTABLE, CREDIT_NOT_PROCESSED ->
                    List.of(RECEIPT, POLICY_DISCLOSURE, CUSTOMER_COMMUNICATION);
            case SUBSCRIPTION_CANCELED -> List.of(RECEIPT, POLICY_DISCLOSURE, CUSTOMER_COMMUNICATION);
            case BANK_CANNOT_PROCESS, CHECK_RETURNED, DEBIT_NOT_AUTHORIZED, INCORRECT_ACCOUNT_DETAILS,
                 INSUFFICIENT_FUNDS, CUSTOMER_INITIATED, GENERAL -> List.of(RECEIPT, CUSTOMER_COMMUNICATION);
        };
    }
}
