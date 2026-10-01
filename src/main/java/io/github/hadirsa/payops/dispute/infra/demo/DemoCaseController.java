package io.github.hadirsa.payops.dispute.infra.demo;

import io.github.hadirsa.payops.dispute.domain.exception.DisputeNotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hands out sample disputes already shaped as a triage request, with a deadline relative to now:
 * {@code curl .../api/v1/demo/cases/dp_demo_fraud | curl ... /api/v1/disputes -d @-}.
 * Only exists in demo mode.
 */
@RestController
@RequestMapping(path = "/api/v1/demo/cases", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(name = "dispute.provider", havingValue = "demo", matchIfMissing = true)
class DemoCaseController {

    private final Clock clock;

    DemoCaseController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping
    List<String> list() {
        return List.copyOf(DemoCases.all(clock).keySet());
    }

    @GetMapping("/{disputeId}")
    Map<String, Object> one(@PathVariable String disputeId) {
        var sample = DemoCases.all(clock).get(disputeId);
        if (sample == null) {
            throw new DisputeNotFoundException(disputeId);
        }
        var d = sample.dispute();
        return Map.of("dispute", Map.of(
                "disputeId", d.disputeId(),
                "paymentId", d.paymentId(),
                "reason", d.reason().name(),
                "amount", Map.of("value", d.amount().major(), "currency", d.amount().currency()),
                "respondBy", d.respondBy().toString()));
    }

    @ExceptionHandler(DisputeNotFoundException.class)
    ProblemDetail notFound(DisputeNotFoundException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Demo case not found");
        return problem;
    }
}
