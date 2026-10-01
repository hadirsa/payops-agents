/**
 * The {@code @Agent} class (orchestration only), all prompt text, and the configuration properties.
 *
 * <p>Depends on {@code domain} (types, rules and ports), Embabel and Spring. It must not know about
 * {@code api} or {@code infra}, or any payment provider: it is sent a case and answers with a decision.
 */
package io.github.hadirsa.payops.dispute.agent;

