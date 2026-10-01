/**
 * Adapters for the domain ports: {@code demo} (in-memory, no account needed) and {@code stripe}
 * (real API: the webhook that receives disputes, the sink that writes drafts back, charge evidence). Selected with {@code dispute.provider=demo|stripe}.
 *
 * <p>Depends on {@code domain} and Spring; the Stripe SDK is allowed only under {@code infra.stripe}.
 * Inbound adapters here (the webhook) call the domain's {@code DisputeIntake} port, never the api layer. Nothing else may import {@code infra}.
 */
package io.github.hadirsa.payops.dispute.infra;

import io.github.hadirsa.payops.dispute.domain.port.DisputeIntake;

