package io.github.hadirsa.payops.a2a;

import com.embabel.agent.a2a.client.api.A2AClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Starts the application on a real port and talks to its own A2A endpoint with the client that ships in
 * embabel-agent-a2a (1.5.3-SNAPSHOT and later). No model call is made, so no key is needed.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "OPENAI_CUSTOM_API_KEY=dummy-key-for-context-test",
                "OPENAI_CUSTOM_BASE_URL=http://localhost:1/v1"})
class A2AClientAgainstRunningAgentTest {

    @LocalServerPort
    int port;
    @Autowired
    A2AClient client;

    @Test
    void theClientIsAutoConfigured() {
        assertNotNull(client);
    }

    @Test
    void theClientReadsTheAgentCardOfTheRunningAgent() {
        var card = client.agentCard("http://localhost:" + port + "/a2a");

        assertTrue(card.capabilities().streaming());
        var skills = card.skills().stream().map(s -> s.id()).toList();
        assertTrue(skills.stream().anyMatch(id -> id.contains("DisputeAgent")), skills.toString());
    }
}
