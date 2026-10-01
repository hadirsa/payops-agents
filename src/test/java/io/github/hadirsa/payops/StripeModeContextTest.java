package io.github.hadirsa.payops;

import io.github.hadirsa.payops.dispute.domain.evidence.EvidenceSource;
import io.github.hadirsa.payops.dispute.domain.port.DecisionSink;
import io.github.hadirsa.payops.dispute.infra.demo.DemoEvidenceSource;
import io.github.hadirsa.payops.dispute.infra.stripe.StripeDecisionSink;
import io.github.hadirsa.payops.dispute.infra.stripe.StripeWebhookController;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/** The same application with {@code dispute.provider=stripe}. No call to Stripe or a model is made. */
class StripeModeContextTest {


    @Nested
    @SpringBootTest(properties = {"OPENAI_CUSTOM_API_KEY=dummy", "OPENAI_CUSTOM_BASE_URL=http://localhost:1/v1", "dispute.provider=stripe", "stripe.api-key=sk_test_not_a_real_key", "stripe.webhook-secret=whsec_not_real"})
    class WithWebhook {

        @Autowired
        ApplicationContext context;
        @Autowired
        java.util.List<DecisionSink> sinks;

        @Test
        void usesTheStripeAdaptersAndRegistersTheWebhook() {
            assertEquals(1, sinks.size());
            assertInstanceOf(StripeDecisionSink.class, sinks.getFirst());
            assertEquals(1, context.getBeansOfType(StripeWebhookController.class).size());
            assertEquals(0, context.getBeansOfType(DemoEvidenceSource.class).size());
            // charge evidence (Stripe) plus the order-system placeholder
            assertEquals(2, context.getBeansOfType(EvidenceSource.class).size());
        }
    }

    @Nested
    @SpringBootTest(properties = {"OPENAI_CUSTOM_API_KEY=dummy", "OPENAI_CUSTOM_BASE_URL=http://localhost:1/v1", "dispute.provider=stripe", "stripe.api-key=sk_test_not_a_real_key"})
    class WithoutWebhookSecret {

        @Autowired
        ApplicationContext context;

        @Test
        void webhookEndpointIsNotRegistered() {
            assertEquals(0, context.getBeansOfType(StripeWebhookController.class).size());
        }
    }

    @Test
    void startupFailsWithALiveKey() {
        var app = new SpringApplication(PayOpsAgentsApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);

        var e = assertThrows(Exception.class, () -> app.run(args("--stripe.api-key=sk_live_do_not_use")).close());

        assertTrue(rootMessage(e).contains("live Stripe key"), rootMessage(e));
    }

    @Test
    void startupFailsWithoutAnyKeyAndSaysHowToFixIt() {
        var app = new SpringApplication(PayOpsAgentsApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);

        var e = assertThrows(Exception.class, () -> app.run(args("--stripe.api-key=")).close());

        assertTrue(rootMessage(e).contains("STRIPE_API_KEY"), rootMessage(e));
    }

    /** Command-line arguments outrank application.yml, which a default property would not. */
    private static String[] args(String... extra) {
        var all = new java.util.ArrayList<>(java.util.List.of(
                "--OPENAI_CUSTOM_API_KEY=dummy", "--OPENAI_CUSTOM_BASE_URL=http://localhost:1/v1",
                "--dispute.provider=stripe"));
        all.addAll(java.util.List.of(extra));
        return all.toArray(String[]::new);
    }

    private static String rootMessage(Throwable t) {
        var root = t;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }
}
