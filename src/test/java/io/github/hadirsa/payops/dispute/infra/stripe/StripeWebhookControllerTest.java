package io.github.hadirsa.payops.dispute.infra.stripe;

import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import io.github.hadirsa.payops.dispute.domain.port.DisputeIntake;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StripeWebhookControllerTest {

    private static final String SECRET = "whsec_test_secret";
    private static final DisputeCase EXPECTED = new DisputeCase("dp_1", "ch_1", DisputeReason.PRODUCT_NOT_RECEIVED,
            new Money(24_000, "USD"), Instant.ofEpochSecond(1_792_000_000L));

    private final DisputeIntake service = mock(DisputeIntake.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private StripeWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new StripeWebhookController(service, SECRET, executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static String event(String id, String type) {
        return event(id, type, "\"api_version\":\"2020-08-27\",");
    }

    private static String event(String id, String type, String apiVersionField) {
        return """
                {"id":"%s","object":"event",%s"type":"%s","livemode":false,
                 "data":{"object":{"id":"dp_1","object":"dispute","amount":24000,"currency":"usd","charge":"ch_1",
                   "reason":"product_not_received","evidence_details":{"due_by":1792000000}}}}
                """.formatted(id, apiVersionField, type);
    }

    /** Stripe's scheme: t=timestamp,v1=HMAC-SHA256(secret, "timestamp.payload"). */
    private static String sign(String payload, String secret) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var hex = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        return "t=" + timestamp + ",v1=" + hex;
    }

    @Test
    void signedDisputeCreatedEventTriggersTriage() throws Exception {
        var payload = event("evt_1", "charge.dispute.created");

        var response = controller.receive(payload, sign(payload, SECRET));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service, timeout(2000)).receive(EXPECTED);
    }

    @Test
    void eventWithoutAnApiVersionIsStillUnderstood() throws Exception {
        var payload = event("evt_7", "charge.dispute.created", "");

        assertEquals(HttpStatus.OK, controller.receive(payload, sign(payload, SECRET)).getStatusCode());
        verify(service, timeout(2000)).receive(EXPECTED);
    }

    @Test
    void theEventsDisputeIsTranslatedWithoutAnyCallBackToStripe() throws Exception {
        // the controller has no Stripe client at all: everything the agent needs is in the event
        var payload = event("evt_8", "charge.dispute.created");

        controller.receive(payload, sign(payload, SECRET));

        verify(service, timeout(2000)).receive(EXPECTED);
    }

    @Test
    void eventWithAnUnreadableDisputeIsAcknowledgedAndDropped() throws Exception {
        var payload = """
                {"id":"evt_9","object":"event","api_version":"2020-08-27","type":"charge.dispute.created",
                 "data":{"object":{"id":"dp_2","object":"dispute","reason":"general"}}}
                """;

        assertEquals(HttpStatus.OK, controller.receive(payload, sign(payload, SECRET)).getStatusCode());
        Thread.sleep(200);
        verifyNoInteractions(service);
    }

    @Test
    void wrongSignatureIsRejectedAndNothingRuns() throws Exception {
        var payload = event("evt_2", "charge.dispute.created");

        var response = controller.receive(payload, sign(payload, "whsec_someone_else"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Thread.sleep(200);
        verifyNoInteractions(service);
    }

    @Test
    void missingSignatureHeaderIsRejected() {
        assertEquals(HttpStatus.BAD_REQUEST,
                controller.receive(event("evt_3", "charge.dispute.created"), null).getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void otherEventTypesAreAcknowledgedAndIgnored() throws Exception {
        var payload = event("evt_4", "charge.succeeded");

        assertEquals(HttpStatus.OK, controller.receive(payload, sign(payload, SECRET)).getStatusCode());
        Thread.sleep(200);
        verifyNoInteractions(service);
    }

    @Test
    void redeliveredEventIsTriagedOnce() throws Exception {
        var payload = event("evt_5", "charge.dispute.created");

        controller.receive(payload, sign(payload, SECRET));
        controller.receive(payload, sign(payload, SECRET));

        verify(service, timeout(2000)).receive(any(DisputeCase.class));
        Thread.sleep(200);
        verify(service, times(1)).receive(any(DisputeCase.class));
    }

    @Test
    void failedTriageDoesNotFailTheWebhook() throws Exception {
        doThrow(new IllegalStateException("model down")).when(service).receive(any());
        var payload = event("evt_6", "charge.dispute.created");

        assertEquals(HttpStatus.OK, controller.receive(payload, sign(payload, SECRET)).getStatusCode());
        verify(service, timeout(2000)).receive(any());
    }
}
