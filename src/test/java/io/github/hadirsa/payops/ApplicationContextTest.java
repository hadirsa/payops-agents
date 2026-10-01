package io.github.hadirsa.payops;

import com.embabel.agent.core.AgentPlatform;
import com.stripe.StripeClient;
import io.github.hadirsa.payops.dispute.domain.port.DecisionSink;
import io.github.hadirsa.payops.dispute.infra.demo.InMemoryDecisionSink;
import io.github.hadirsa.payops.dispute.infra.stripe.StripeWebhookController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boots the whole application in demo mode with a dummy model key (no LLM call is made), which checks
 * that Embabel finds the agent and that only the demo adapters are active.
 */
@SpringBootTest(properties = {
        "OPENAI_CUSTOM_API_KEY=dummy-key-for-context-test",
        "OPENAI_CUSTOM_BASE_URL=http://localhost:1/v1"})
class ApplicationContextTest {

    @Autowired
    AgentPlatform platform;
    @Autowired
    java.util.List<DecisionSink> sinks;
    @Autowired
    ApplicationContext context;

    @Test
    void embabelDeploysTheDisputeAgent() {
        var names = platform.agents().stream().map(a -> a.getName()).toList();
        assertTrue(names.contains("DisputeAgent"), names.toString());
    }

    @Test
    void demoModeUsesTheInMemorySinkAndNoStripeBeans() {
        assertEquals(1, sinks.size());
        assertInstanceOf(InMemoryDecisionSink.class, sinks.getFirst());
        assertEquals(0, context.getBeansOfType(StripeClient.class).size());
        assertEquals(0, context.getBeansOfType(StripeWebhookController.class).size());
    }
}
