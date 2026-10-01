package io.github.hadirsa.payops.dispute.infra.stripe;

import com.stripe.StripeClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.hadirsa.payops.dispute.domain.decision.DisputeDecision;
import io.github.hadirsa.payops.dispute.domain.decision.Recommendation;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeConflictException;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeGatewayException;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeNotFoundException;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Talks to a throwaway local HTTP server that speaks Stripe's wire format, so the real SDK is
 * exercised (request encoding, response parsing, error mapping) with no network and no account.
 */
class StripeDecisionSinkTest {

    private record Reply(int status, String body) {}

    private record Call(String method, String path, String body) {}

    private HttpServer server;
    private StripeDecisionSink sink;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private final List<Call> calls = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        var client = StripeClient.builder()
                .setApiKey("sk_test_fake")
                .setApiBase("http://localhost:" + server.getAddress().getPort())
                .setMaxNetworkRetries(0)
                .build();
        sink = new StripeDecisionSink(client);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        synchronized (calls) {
            calls.add(new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    URLDecoder.decode(body, StandardCharsets.UTF_8)));
        }
        var reply = replies.getOrDefault(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath(),
                new Reply(500, "{\"error\":{\"type\":\"api_error\",\"message\":\"unexpected call\"}}"));
        var bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String dispute(String metadata) {
        return """
                {"id":"dp_1","object":"dispute","amount":24000,"currency":"usd","charge":"ch_1",
                 "reason":"product_not_received","status":"needs_response","metadata":%s,
                 "evidence_details":{"due_by":1792000000,"has_evidence":false,"submission_count":0}}
                """.formatted(metadata);
    }

    private DisputeDecision decision(Recommendation recommendation, String letter) {
        return new DisputeDecision("dp_1", DisputeReason.PRODUCT_NOT_RECEIVED, new Money(24_000, "USD"),
                Instant.ofEpochSecond(1_792_000_000L), List.of(), recommendation, 0.9, false, List.of(), "why", letter);
    }

    private List<Call> posts() {
        synchronized (calls) {
            return calls.stream().filter(c -> c.method().equals("POST")).toList();
        }
    }

    @Test
    void acceptingARepresentDecisionStoresTheLetterAsEvidenceWithoutSubmitting() {
        replies.put("POST /v1/disputes/dp_1", new Reply(200, dispute("{}")));

        sink.accept(decision(Recommendation.REPRESENT, "Dear issuer, the goods were delivered."));

        var body = posts().getFirst().body();
        assertTrue(body.contains("evidence[uncategorized_text]=Dear issuer, the goods were delivered."), body);
        assertTrue(body.contains("submit=false"), body);
        assertTrue(body.contains("metadata[triage_recommendation]=REPRESENT"), body);
        assertTrue(body.contains("metadata[triage_needs_review]=false"), body);
    }

    @Test
    void acceptingAnAcceptDecisionRecordsTheOutcomeButNoEvidence() {
        replies.put("POST /v1/disputes/dp_1", new Reply(200, dispute("{}")));

        sink.accept(decision(Recommendation.ACCEPT, ""));

        var body = posts().getFirst().body();
        assertFalse(body.contains("evidence["), body);
        assertTrue(body.contains("metadata[triage_recommendation]=ACCEPT"), body);
        assertTrue(body.contains("submit=false"), body);
    }

    @Test
    void stripeErrorsMapToOurOwnExceptions() {
        replies.put("POST /v1/disputes/dp_1", new Reply(500, "{\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}"));
        replies.put("POST /v1/disputes/dp_404", new Reply(404,
                "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"resource_missing\",\"message\":\"No such dispute\"}}"));

        assertThrows(DisputeGatewayException.class, () -> sink.accept(decision(Recommendation.REPRESENT, "letter")));
        assertThrows(DisputeNotFoundException.class, () -> sink.accept(
                new DisputeDecision("dp_404", DisputeReason.GENERAL, new Money(1, "USD"), Instant.EPOCH, List.of(),
                        Recommendation.REPRESENT, 0.9, false, List.of(), "r", "l")));
    }

    @Test
    void approvalRefusesAnUntriagedDispute() {
        replies.put("GET /v1/disputes/dp_1", new Reply(200, dispute("{}")));

        assertThrows(DisputeConflictException.class, () -> sink.approve("dp_1"));
        assertTrue(posts().isEmpty(), "nothing may be sent to Stripe");
    }

    @Test
    void approvalRefusesADisputeTriagedAsAccept() {
        replies.put("GET /v1/disputes/dp_1", new Reply(200, dispute("{\"triage_recommendation\":\"ACCEPT\"}")));

        assertThrows(DisputeConflictException.class, () -> sink.approve("dp_1"));
        assertTrue(posts().isEmpty());
    }

    @Test
    void approvalSubmitsATriagedRepresentDispute() {
        replies.put("GET /v1/disputes/dp_1", new Reply(200, dispute("{\"triage_recommendation\":\"REPRESENT\"}")));
        replies.put("POST /v1/disputes/dp_1", new Reply(200, dispute("{}")));

        sink.approve("dp_1");

        assertTrue(posts().getFirst().body().contains("submit=true"), posts().getFirst().body());
    }

    @Test
    void approvingAnUnknownDisputeIsNotFound() {
        replies.put("GET /v1/disputes/dp_404", new Reply(404,
                "{\"error\":{\"type\":\"invalid_request_error\",\"code\":\"resource_missing\",\"message\":\"No such dispute\"}}"));

        assertThrows(DisputeNotFoundException.class, () -> sink.approve("dp_404"));
    }
}
