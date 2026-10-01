package io.github.hadirsa.payops.dispute.domain.decision;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.hadirsa.payops.dispute.domain.policy.DisputePolicy;

/** The LLM's proposal. {@link DisputePolicy} decides what is done with it. */
@JsonClassDescription("A recommendation on how to handle a dispute, with a draft response")
public record DisputeDraft(
        @JsonPropertyDescription("REPRESENT to contest the dispute, ACCEPT to give up") Recommendation recommendation,
        @JsonPropertyDescription("Confidence from 0.0 to 1.0 that contesting would succeed on this evidence") double confidence,
        @JsonPropertyDescription("Two or three sentences explaining the recommendation") String rationale,
        @JsonPropertyDescription("The response letter to the card issuer; empty when recommending ACCEPT") String responseLetter
) {

    public DisputeDraft {
        if (recommendation == null) {
            throw new IllegalArgumentException("recommendation is required");
        }
        confidence = Double.isNaN(confidence) ? 0.0 : Math.clamp(confidence, 0.0, 1.0);
        rationale = rationale == null ? "" : rationale;
        responseLetter = responseLetter == null ? "" : responseLetter;
    }
}
