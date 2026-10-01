/**
 * Types the planner chains together, the rules around them, and the ports to outside systems.
 *
 * <p>Sub-packages, bottom-up: {@code exception}, {@code model} (the case message), {@code evidence},
 * {@code decision}, {@code policy}, {@code port}, {@code privacy}. ArchUnit keeps them free of cycles.
 *
 * <p>Record shapes and their {@code @Json*Description} annotations become the JSON schema sent to the
 * LLM ({@link io.github.hadirsa.payops.dispute.domain.model.ExtractedCase},
 * {@link io.github.hadirsa.payops.dispute.domain.decision.DisputeDraft}), so renaming a field changes the
 * prompt. Keep derived accessors {@code @JsonIgnore}d. No Spring, no payment-provider SDK and no Embabel runtime
 * here; only {@code HasContent} from Embabel's domain library is allowed.
 */
package io.github.hadirsa.payops.dispute.domain;

import io.github.hadirsa.payops.dispute.domain.decision.DisputeDraft;
import io.github.hadirsa.payops.dispute.domain.model.ExtractedCase;

