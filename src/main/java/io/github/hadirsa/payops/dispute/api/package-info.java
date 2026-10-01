/**
 * REST entry points, the service that hands decisions to the sinks, and error mapping, in two
 * sub-packages: {@code rest} (HTTP) depends on {@code service} (application logic), never the reverse.
 *
 * <p>Depends on {@code domain} (types and ports), {@code AgentPlatform} and Spring. It must not import
 * {@code agent} or {@code infra}: goals are invoked by type so the planner chooses the path. Public
 * request and response records live here because domain records are the LLM schema. It implements
 * the inbound port {@code DisputeIntake} for adapters that receive disputes from a provider.
 */
package io.github.hadirsa.payops.dispute.api;

import io.github.hadirsa.payops.dispute.domain.port.DisputeIntake;

