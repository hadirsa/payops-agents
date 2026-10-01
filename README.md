# payops-agents

Agentic payment operations on [Embabel](https://github.com/embabel/embabel-agent), Spring Boot 4 and Java 25.
The first agent triages payment disputes (chargebacks): it is sent a dispute, gathers evidence, drafts a response,
and hands the risky cases to a person. Refunds, decline analysis and reconciliation are natural next agents;
each lives in its own package under `io.github.hadirsa.payops`.

The agent knows **no payment provider**. It is sent a provider-neutral dispute message and answers with a decision.
Stripe is an optional adapter that translates in both directions; the default demo mode needs no account at all.
The only thing you always need is access to an LLM.

> **Sample code, not legal or financial advice.** Dispute rules differ by card network and change over time.
> The evidence table is a simplified starting point that someone who handles disputes should review.
> Run against Stripe **test mode** unless you know exactly what you are doing.

## What it does

```
UserInput ─extractCase─▶ DisputeCase ─gatherEvidence─▶ EvidencePack ─decide─▶ DisputeDecision
 (free text)   LLM        (or start     plain code                   LLM drafts,
               cold       here)                                      policy decides
```

Embabel plans this chain from the types it has. Send a structured dispute and the first step is skipped.

| Step | Who | Notes |
|---|---|---|
| `extractCase` | LLM, temperature 0 | Reads id, payment, reason, amount, currency and deadline out of free text, refusing to guess missing ones. Card numbers are masked before the prompt is built. |
| `gatherEvidence` | code | Asks every `EvidenceSource` for what the dispute reason requires (`ReasonCatalog`). Missing evidence is recorded, never skipped. |
| `decide` | LLM, then code | The LLM proposes `REPRESENT` or `ACCEPT` with a draft letter. `DisputePolicy` then decides whether a person must review. **The model can only make the outcome more cautious, never less.** |

A decision needs human review when any of these hold: the figures were read from free text, the amount is above
`auto-decision-limit`, required evidence is missing while recommending to contest, confidence is below
`min-confidence`, or the response deadline is near or passed.

Decisions go out through `DecisionSink`s (for Stripe: a saved draft). Approval, which sends the response for real and
cannot be undone, is a separate call (or an explicit opt-in).

## Quick start (demo mode, no Stripe)

Requirements: JDK 25, Maven, and an OpenAI-compatible LLM endpoint.

```bash
export OPENAI_CUSTOM_API_KEY=...                          # your provider's key
export OPENAI_CUSTOM_BASE_URL=https://api.openai.com/v1   # must end in /v1
mvn spring-boot:run                                       # http://localhost:9091
```

The models are set in `application.yml` (`gpt-4o-mini` by default). Each model named there must also be in the
provider's `models:` list under `embabel.agent.platform.models.openai.custom`, or calls fail at runtime.

Demo mode serves five sample disputes, with deadlines relative to now so they never go stale:
`dp_demo_fraud`, `dp_demo_not_received`, `dp_demo_duplicate`, `dp_demo_high_value` (needs review: amount),
`dp_demo_missing_evidence` (needs review: no delivery proof).

```bash
# A sample, already shaped as a request, piped straight into triage
curl -s localhost:9091/api/v1/demo/cases/dp_demo_fraud \
  | curl -s localhost:9091/api/v1/disputes -H 'Content-Type: application/json' -d @-

# Your own structured message (skips extraction). Amount is in major units.
curl localhost:9091/api/v1/disputes -H 'Content-Type: application/json' -d '{
  "dispute": {
    "disputeId": "dp_1", "paymentId": "ch_1", "reason": "product_not_received",
    "amount": {"value": 240.00, "currency": "USD"}, "respondBy": "2026-12-01T00:00:00Z"
  }
}'

# Free text: the agent reads the dispute out of it. Send text OR dispute, never both (400)
curl localhost:9091/api/v1/disputes -H 'Content-Type: application/json' \
  -d '{"text":"Dispute dp_demo_not_received on payment ch_demo_2: not received, 89.90 EUR, reply by 2027-01-15"}'

# Approval: sends the response for real. Final.
curl -X POST localhost:9091/api/v1/disputes/dp_demo_fraud/approve
```

Reasons: `fraudulent`, `product_not_received`, `duplicate`, `credit_not_processed`, `subscription_canceled`,
`product_not_acceptable`, `unrecognized`, `general` and a few more (see `DisputeReason`).

Response (trimmed):

```json
{
  "disputeId": "dp_demo_fraud",
  "reason": "FRAUDULENT",
  "amount": { "value": 240.00, "currency": "USD" },
  "respondBy": "2026-10-12T19:17:30Z",
  "recommendation": "REPRESENT",
  "confidence": 0.9,
  "requiresHumanReview": false,
  "reviewReasons": [],
  "rationale": "…",
  "responseLetter": "Dear Card Issuer, …",
  "evidence": [
    { "type": "AUTHORIZATION_CHECKS", "found": true, "summary": "CVC check passed, postal code check passed, 3D Secure authenticated" }
  ],
  "published": true,
  "approved": false
}
```

Errors are RFC 9457 problem details: `400` invalid request, `404` unknown dispute, `409` already approved or never
triaged, `422` no complete dispute could be read from the text, `502` model or outside-system failure, `504` model timeout.

## Using Stripe

```bash
export DISPUTE_PROVIDER=stripe
export STRIPE_API_KEY=sk_test_...          # a test-mode key
export STRIPE_WEBHOOK_SECRET=whsec_...     # optional: enables POST /webhooks/stripe
mvn spring-boot:run
```

Everything Stripe-specific lives in one package, `infra.stripe`:

- **In:** the webhook receives `charge.dispute.created`, verifies the signature, translates the Stripe dispute
  into a `DisputeCase` (the event already carries everything, so nothing is fetched) and passes it to the domain's
  `DisputeIntake` port in the background. It answers immediately, because Stripe retries slow deliveries, and
  ignores repeated deliveries.
  For local testing: `stripe listen --forward-to localhost:9091/webhooks/stripe` then `stripe trigger charge.dispute.created`.
- **Out:** `StripeDecisionSink` writes the drafted letter to the dispute's evidence with `submit=false`. The outcome is
  kept in the dispute's metadata (`triage_recommendation`, `triage_needs_review`), so approval refuses a dispute that was
  never triaged or was triaged as `ACCEPT`, then submits.
- Live keys (`sk_live_`, `rk_live_`) are **refused at startup** unless `stripe.allow-live=true`.
- Stripe knows the receipt and card checks (`StripeChargeEvidenceSource`). It does not know your delivery, customer
  history, support emails or policies. Those come from `OrderSystemEvidenceSource`, a placeholder that reports them as
  missing, so cases go to a person until you connect your own systems (see below).

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `dispute.provider` (`DISPUTE_PROVIDER`) | `demo` | `demo` or `stripe` |
| `dispute.agent.extract-llm` / `draft-llm` | `gpt-4o-mini` | Models for each LLM step |
| `dispute.agent.auto-decision-limit` | `1000` | Larger amounts (in the dispute's own currency) always need a person. No currency conversion. |
| `dispute.agent.min-confidence` | `0.7` | Lower confidence always needs a person |
| `dispute.agent.deadline-warning` | `2d` | Disputes due sooner always need a person |
| `dispute.workflow.publish-decisions` | `true` | Hand every decision to the sinks (Stripe: saves a draft) |
| `dispute.workflow.auto-approve` | `false` | Also approve, only when no review is needed and the decision is `REPRESENT`. Final. |
| `stripe.api-key` (`STRIPE_API_KEY`) | none | Required when `dispute.provider=stripe` |
| `stripe.webhook-secret` (`STRIPE_WEBHOOK_SECRET`) | none | Enables the webhook endpoint |
| `stripe.allow-live` | `false` | Allow live keys |

There are no default secrets anywhere: startup fails if the model key or (in Stripe mode) the Stripe key is missing.

## Making it yours

- **Send it disputes from anywhere.** The agent takes a `DisputeCase`. Translate your provider's dispute, your
  switch's complaint record or a queue message into one and pass it to the `DisputeIntake` port, or `POST /api/v1/disputes`.
- **Decide what happens to decisions.** Implement `DecisionSink` (`accept` = a decision was made, `approve` = a
  person approved) and register it as a bean: write to a database, open a ticket, publish to a topic.
- **Connect your systems.** Implement `EvidenceSource` for the evidence types you can answer (delivery proof,
  customer history, support emails, terms) and register it as a bean. The first source that finds evidence
  wins; sources that do not are listed in the "not found" reason.
- **Change what is required.** `ReasonCatalog` maps each dispute reason to required evidence. It is plain Java with tests.
- **Change when a person must look.** `DisputePolicy` and its `PolicyLimits`. Also plain Java with tests.
- **Change the wording.** All prompt text is in `DisputePrompts`.
- **Another payment provider.** Add a package next to `infra.stripe` with its inbound translator and its sink. An
  architecture test keeps provider SDKs inside their own adapter package.
- **Another agent.** Add a sibling package, e.g. `payops/refund/{domain,agent,api,infra}`. Features must not depend
  on each other.

## How the code is organised

```
io.github.hadirsa.payops.dispute
├── domain
│   ├── model      the dispute message (DisputeCase), reasons, money
│   ├── evidence   what each reason requires, EvidenceSource port, collector
│   ├── decision   the LLM's draft, the policy-checked decision, review reasons
│   ├── policy     DisputePolicy and its limits (plain Java)
│   ├── port       DisputeIntake (in) and DecisionSink (out)
│   ├── exception  domain failures
│   └── privacy    card-number masking
├── agent          the @Agent class (orchestration only), prompts, properties
├── api
│   ├── rest       controller, public request/response records, error mapping
│   └── service    triage through the platform, hands decisions to the sinks
└── infra
    ├── demo       sample cases, in-memory sink
    └── stripe     webhook in, decision sink out, charge evidence
```

`ArchitectureTest` enforces the dependencies, and that the sub-packages of `domain` and `api` form no cycles: domain has no Spring or provider SDK; the agent knows no provider and
reaches the outside only through ports; the API starts goals by type (never by agent class); infra adapters call the
domain's ports, never the api or agent layers; only `infra.stripe` uses the Stripe SDK. The same agent is also exposed
over MCP (`disputeTriage`, SSE at `/sse`) and A2A (`/a2a`).

Public request and response records live in `api`. The domain records are the JSON schema sent to the LLM, so
returning them directly would let a prompt tweak silently change the API.

## Tests

```bash
mvn test
```

No network, no Stripe account and no LLM calls are needed. Domain rules and the policy are plain JUnit; agent steps
use Embabel's `FakeOperationContext` to check each prompt, model and temperature; the Stripe sink is tested against a
local fake Stripe HTTP server, so the real SDK's request and response handling is exercised; webhook signatures are
checked with real HMACs; two context tests boot the whole application in demo and Stripe mode.

Built and tested against **Embabel 1.5.2**.

## Not included (deliberately)

This is a sample. Before using it with real disputes you would add, at least:

- **Authentication** on the API (OAuth2/JWT or mTLS between services) and roles for who may approve.
- **Persistence and an audit trail**: which model, prompt version and policy rule produced each decision. Nothing
  is stored here except what a sink writes (for Stripe, the draft on the dispute).
- **A durable queue** for incoming disputes, with retries and a dead-letter path (webhook de-duplication here is in memory).
- **A real approval workflow** instead of a single endpoint.
- **Review of your data handling.** Decide what customer data may go to your model provider; card numbers are masked
  before prompts, but evidence summaries from your systems are sent as they are.
- Metrics, tracing, and a measure of how often people agree with the agent before you ever turn on auto-approve.

## License

Apache License 2.0, see [LICENSE](LICENSE).
