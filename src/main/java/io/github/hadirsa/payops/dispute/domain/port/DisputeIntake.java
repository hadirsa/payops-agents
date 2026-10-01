package io.github.hadirsa.payops.dispute.domain.port;

import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;

/**
 * Inbound port: something that accepts a dispute for triage. Adapters that receive disputes from a
 * payment provider (a webhook, a queue consumer) call this with a {@link DisputeCase} and need to
 * know nothing about how triage works.
 */
public interface DisputeIntake {

    /** Triages the dispute and hands the result on; failures propagate to the caller. */
    void receive(DisputeCase dispute);
}
