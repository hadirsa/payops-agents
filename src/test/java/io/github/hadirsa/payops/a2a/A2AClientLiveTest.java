package io.github.hadirsa.payops.a2a;

import com.embabel.agent.a2a.client.api.A2AClient;
import io.a2a.spec.Message;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.a2a.spec.TaskState;
import io.a2a.spec.TextPart;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sends a real dispute through the A2A client to this application. It calls the configured model, so it only
 * runs when the model key and URL are in the environment and {@code PAYOPS_LIVE=true}:
 * {@code PAYOPS_LIVE=true mvn test -Dtest=A2AClientLiveTest}.
 */
@Import(A2AClientLiveTest.Recording.class)
@EnabledIfEnvironmentVariable(named = "PAYOPS_LIVE", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class A2AClientLiveTest {

    static final List<String> OBSERVED = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class Recording {
        @Bean
        ObservationRegistry observationRegistry() {
            var registry = ObservationRegistry.create();
            registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
                @Override
                public boolean supportsContext(Observation.Context context) {
                    return true;
                }

                @Override
                public void onStop(Observation.Context context) {
                    OBSERVED.add(context.getName());
                }
            });
            return registry;
        }
    }

    @LocalServerPort
    int port;
    @Autowired
    A2AClient client;

    @Test
    void aDisputeSentThroughTheClientIsTriagedAndKeepsItsContextId() {
        var contextId = "ctx-" + UUID.randomUUID();
        var message = new Message.Builder()
                .role(Message.Role.USER)
                .messageId(UUID.randomUUID().toString())
                .contextId(contextId)
                .parts(List.of(new TextPart(
                        "Dispute dp_demo_not_received on payment ch_demo_2: product not received, 89.90 EUR, reply by 2027-01-15")))
                .build();

        var task = client.sendMessage("http://localhost:" + port + "/a2a", message);

        assertEquals(contextId, task.getContextId());
        assertEquals(TaskState.COMPLETED, task.getStatus().state());
        var text = ((TextPart) task.getStatus().message().getParts().getFirst()).getText();
        assertTrue(text.contains("dp_demo_not_received"), text);
        assertTrue(text.contains("REPRESENT") || text.contains("ACCEPT"), text);
        // the client streams when the card advertises streaming, so the server records message.stream
        assertTrue(OBSERVED.contains("a2a.message.stream"), OBSERVED.toString());
    }
}
