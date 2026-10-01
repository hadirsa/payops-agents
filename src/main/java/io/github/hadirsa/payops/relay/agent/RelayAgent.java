package io.github.hadirsa.payops.relay.agent;

import com.embabel.agent.a2a.client.api.A2AClient;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.ActionRetryPolicy;
import com.embabel.agent.domain.io.UserInput;
import io.a2a.spec.Message;
import io.a2a.spec.TextPart;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Forwards the text it is sent to another agent over A2A, using the client from embabel-agent-a2a. Only exists
 * when {@code relay.downstream-url} is set. It deliberately sends the message WITHOUT a contextId, so what the
 * downstream agent sees shows whether the client picked the caller's contextId up from OTel baggage.
 */
@Agent(description = "Relays a payment dispute message to a remote dispute triage agent over A2A and returns its answer")
@ConditionalOnProperty("relay.downstream-url")
public class RelayAgent {

    private static final Logger log = LoggerFactory.getLogger(RelayAgent.class);

    private final A2AClient client;
    private final String downstreamUrl;

    public RelayAgent(A2AClient client, @Value("${relay.downstream-url}") String downstreamUrl) {
        this.client = client;
        this.downstreamUrl = downstreamUrl;
    }

    @AchievesGoal(description = "Relays a dispute message to the remote triage agent and returns its answer",
            export = @Export(remote = true, name = "relayDispute", startingInputTypes = {UserInput.class}))
    @Action(actionRetryPolicy = ActionRetryPolicy.FIRE_ONCE)
    public RelayAnswer relay(UserInput input, OperationContext context) {
        var processId = context.getProcessContext().getAgentProcess().getId();
        var message = new Message.Builder()
                .role(Message.Role.USER)
                .messageId(UUID.randomUUID().toString())
                .parts(List.of(new TextPart(input.getContent())))
                .build();
        log.info("RELAY out: process={} -> {} (message has no contextId)", processId, downstreamUrl);
        var task = client.sendMessage(downstreamUrl, message);
        var text = task.getStatus().message() == null ? "" : ((TextPart) task.getStatus().message().getParts().getFirst()).getText();
        log.info("RELAY in: remote task={} contextId={}", task.getId(), task.getContextId());
        return new RelayAnswer(processId, task.getId(), task.getContextId(), text);
    }
}
