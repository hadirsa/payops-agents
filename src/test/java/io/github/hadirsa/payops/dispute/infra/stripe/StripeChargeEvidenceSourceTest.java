package io.github.hadirsa.payops.dispute.infra.stripe;

import com.stripe.model.Charge;
import com.stripe.net.ApiResource;
import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Parses JSON in Stripe's real shape, so a field rename in the SDK shows up here. */
class StripeChargeEvidenceSourceTest {

    private static Charge charge(String json) {
        return ApiResource.GSON.fromJson(json, Charge.class);
    }

    @Test
    void receiptIsTheChargesReceiptUrl() {
        var item = StripeChargeEvidenceSource.receipt(
                charge("{\"id\":\"ch_1\",\"object\":\"charge\",\"receipt_url\":\"https://pay.stripe.com/receipts/abc\"}"));

        assertTrue(item.found());
        assertTrue(item.summary().contains("https://pay.stripe.com/receipts/abc"));
    }

    @Test
    void chargeWithoutReceiptIsReportedMissing() {
        var item = StripeChargeEvidenceSource.receipt(charge("{\"id\":\"ch_1\",\"object\":\"charge\"}"));

        assertFalse(item.found());
        assertEquals(EvidenceType.RECEIPT, item.type());
    }

    @Test
    void cardChecksAndThreeDSecureAreSummarised() {
        var item = StripeChargeEvidenceSource.authorizationChecks(charge("""
                {"id":"ch_1","object":"charge","payment_method_details":{"type":"card","card":{
                  "checks":{"cvc_check":"pass","address_postal_code_check":"fail","address_line1_check":null},
                  "three_d_secure":{"result":"authenticated"}}}}
                """));

        assertTrue(item.found());
        assertEquals("CVC check: pass, postal code check: fail, address line check: unknown, 3D Secure: authenticated",
                item.summary());
    }

    @Test
    void chargeWithoutCardDetailsHasNoChecks() {
        assertFalse(StripeChargeEvidenceSource.authorizationChecks(charge("{\"id\":\"ch_1\",\"object\":\"charge\"}")).found());
        assertFalse(StripeChargeEvidenceSource.authorizationChecks(charge(
                "{\"id\":\"ch_1\",\"object\":\"charge\",\"payment_method_details\":{\"type\":\"card\",\"card\":{}}}")).found());
    }
}
