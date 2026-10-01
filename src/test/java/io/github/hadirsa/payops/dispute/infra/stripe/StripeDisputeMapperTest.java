package io.github.hadirsa.payops.dispute.infra.stripe;

import com.stripe.model.Dispute;
import com.stripe.net.ApiResource;
import io.github.hadirsa.payops.dispute.domain.exception.InvalidDisputeException;
import io.github.hadirsa.payops.dispute.domain.model.CaseOrigin;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Parses JSON in Stripe's real shape, so a field rename in the SDK shows up here. */
class StripeDisputeMapperTest {

    private static Dispute dispute(String json) {
        return ApiResource.GSON.fromJson(json, Dispute.class);
    }

    @Test
    void mapsStripesFieldsToTheProviderNeutralCase() {
        var result = StripeDisputeMapper.toCase(dispute("""
                {"id":"dp_1","object":"dispute","amount":24000,"currency":"usd","charge":"ch_1",
                 "reason":"product_not_received","evidence_details":{"due_by":1792000000}}"""));

        assertEquals(new DisputeCase("dp_1", "ch_1", DisputeReason.PRODUCT_NOT_RECEIVED, new Money(24_000, "USD"),
                Instant.ofEpochSecond(1_792_000_000L)), result);
        assertEquals(CaseOrigin.STRUCTURED, result.origin());
    }

    @Test
    void reasonsStripeAddsLaterFallBackToGeneral() {
        var result = StripeDisputeMapper.toCase(dispute("""
                {"id":"dp_1","object":"dispute","amount":100,"currency":"usd","charge":"ch_1","reason":"brand_new_reason",
                 "evidence_details":{"due_by":1792000000}}"""));

        assertEquals(DisputeReason.GENERAL, result.reason());
    }

    @Test
    void fallsBackToThePaymentIntentWhenThereIsNoCharge() {
        var result = StripeDisputeMapper.toCase(dispute("""
                {"id":"dp_1","object":"dispute","amount":100,"currency":"usd","payment_intent":"pi_9","reason":"general",
                 "evidence_details":{"due_by":1792000000}}"""));

        assertEquals("pi_9", result.paymentId());
    }

    @Test
    void missingDeadlineBecomesAnAlreadyPassedOne() {
        var result = StripeDisputeMapper.toCase(dispute(
                "{\"id\":\"dp_1\",\"object\":\"dispute\",\"amount\":100,\"currency\":\"usd\",\"charge\":\"ch_1\",\"reason\":\"general\"}"));

        assertEquals(Instant.EPOCH, result.respondBy());
    }

    @Test
    void disputeWithoutAnAmountCannotBeTranslated() {
        assertThrows(InvalidDisputeException.class, () -> StripeDisputeMapper.toCase(dispute(
                "{\"id\":\"dp_1\",\"object\":\"dispute\",\"charge\":\"ch_1\",\"reason\":\"general\"}")));
    }
}
