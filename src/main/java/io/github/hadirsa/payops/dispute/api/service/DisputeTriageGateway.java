package io.github.hadirsa.payops.dispute.api.service;

import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.domain.io.UserInput;
import io.github.hadirsa.payops.dispute.domain.decision.DisputeDecision;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeGatewayException;
import io.github.hadirsa.payops.dispute.domain.exception.InvalidDisputeException;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

/**
 * Runs the dispute goal on the {@link AgentPlatform}. Invokes by goal type, never by agent class, so
 * the planner chooses the path. Failures of outside systems and "no complete dispute in the text" keep
 * their own type; anything else becomes a {@link DisputeTriageException}.
 */
@Component
public class DisputeTriageGateway {

    private final AgentPlatform agentPlatform;

    public DisputeTriageGateway(AgentPlatform agentPlatform) {
        this.agentPlatform = agentPlatform;
    }

    public DisputeDecision fromText(String text) {
        return run(new UserInput(text));
    }

    public DisputeDecision fromCase(DisputeCase dispute) {
        return run(dispute);
    }

    private DisputeDecision run(Object input) {
        DisputeDecision result;
        try {
            result = AgentInvocation.builder(agentPlatform).build(DisputeDecision.class).invoke(input);
        } catch (Exception e) {
            // Not just RuntimeException: invoke() is Kotlin and throws checked ExecutionException undeclared
            throw translate(e);
        }
        if (result == null) {
            throw new DisputeTriageException("Dispute triage returned no result", null, false);
        }
        return result;
    }

    /**
     * The real cause is wrapped several layers deep (ExecutionException, Embabel's own wrappers), so
     * look through the chain for failures that are not the model provider's fault.
     */
    static RuntimeException translate(Exception e) {
        var outside = cause(e, DisputeGatewayException.class);
        if (outside != null) {
            return outside;
        }
        var invalid = cause(e, InvalidDisputeException.class);
        if (invalid != null) {
            return new UnreadableDisputeException(invalid);
        }
        return new DisputeTriageException("Dispute triage failed", e, isTimeout(e));
    }

    private static <T extends Throwable> T cause(Throwable e, Class<T> type) {
        for (var t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    private static boolean isTimeout(Throwable e) {
        for (var t = e; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException || t instanceof InterruptedIOException || t instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
