package io.github.hadirsa.payops.dispute.infra.stripe;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import io.github.hadirsa.payops.dispute.domain.port.DisputeIntake;
import jakarta.annotation.PreDestroy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives {@code charge.dispute.created} from Stripe. Only registered when {@code dispute.provider=stripe}
 * and {@code stripe.webhook-secret} is set.
 *
 * <p>The signature is verified against the raw body before anything else. The handler answers 200
 * straight away and triages on a separate thread, because Stripe retries any delivery that takes too
 * long. Event ids already seen are skipped (in memory: a restart forgets them, and triage is safe to
 * repeat because saving a draft overwrites the previous one).
 */
@RestController
@RequestMapping("/webhooks/stripe")
@ConditionalOnProperty(name = "dispute.provider", havingValue = "stripe")
@ConditionalOnProperty("stripe.webhook-secret")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);
    private static final String DISPUTE_CREATED = "charge.dispute.created";
    private static final int REMEMBERED_EVENTS = 10_000;

    private final DisputeIntake intake;
    private final String secret;
    private final ExecutorService executor;
    private final Map<String, Boolean> seenEvents = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > REMEMBERED_EVENTS;
        }
    });

    @Autowired
    public StripeWebhookController(DisputeIntake intake, @Value("${stripe.webhook-secret}") String secret) {
        this(intake, secret, Executors.newVirtualThreadPerTaskExecutor());
    }

    StripeWebhookController(DisputeIntake intake, String secret, ExecutorService executor) {
        this.intake = intake;
        this.secret = secret;
        this.executor = executor;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody String payload,
                                        @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature == null ? "" : signature, secret);
        } catch (SignatureVerificationException e) {
            log.warn("Rejected Stripe webhook with an invalid signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (!DISPUTE_CREATED.equals(event.getType())) {
            return ResponseEntity.ok().build();
        }
        if (seenEvents.put(event.getId(), Boolean.TRUE) != null) {
            log.info("Ignoring repeated Stripe event {}", event.getId());
            return ResponseEntity.ok().build();
        }
        var dispute = dispute(event);
        if (dispute == null) {
            log.warn("Stripe event {} carried no readable dispute", event.getId());
            return ResponseEntity.ok().build();
        }
        executor.execute(() -> triage(dispute));
        return ResponseEntity.ok().build();
    }

    private void triage(DisputeCase dispute) {
        try {
            intake.receive(dispute);
        } catch (Exception e) {
            // Nobody is waiting on this thread: log it so the dispute can be retried by hand
            log.error("Automatic triage of dispute {} failed", dispute.disputeId(), e);
        }
    }

    /** The event already carries the whole dispute, so nothing is fetched from Stripe. */
    private static DisputeCase dispute(Event event) {
        var deserializer = event.getDataObjectDeserializer();
        StripeObject object = null;
        try {
            // Empty when the event's API version differs from the SDK's; throws if the event has no version at all
            object = deserializer.getObject().orElse(null);
        } catch (RuntimeException ignored) {
            // fall through to the lenient path
        }
        if (object == null) {
            try {
                object = deserializer.deserializeUnsafe();
            } catch (Exception e) {
                return null;
            }
        }
        if (!(object instanceof Dispute dispute)) {
            return null;
        }
        try {
            return StripeDisputeMapper.toCase(dispute);
        } catch (IllegalArgumentException e) {
            log.warn("Could not translate Stripe dispute {}: {}", dispute.getId(), e.getMessage());
            return null;
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
