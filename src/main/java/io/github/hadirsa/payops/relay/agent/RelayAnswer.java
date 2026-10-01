package io.github.hadirsa.payops.relay.agent;

import com.embabel.agent.domain.library.HasContent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jspecify.annotations.NonNull;

/** What the remote agent answered, plus the ids that show how the call was correlated. */
public record RelayAnswer(String processId, String remoteTaskId, String remoteContextId, String remoteText)
        implements HasContent {

    @JsonIgnore
    @Override
    public @NonNull String getContent() {
        return "relayed by process " + processId + " | remote task " + remoteTaskId
                + " | remote contextId " + remoteContextId + "\n" + remoteText;
    }
}
