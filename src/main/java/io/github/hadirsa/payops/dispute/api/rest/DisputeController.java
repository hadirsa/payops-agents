package io.github.hadirsa.payops.dispute.api.rest;

import io.github.hadirsa.payops.dispute.api.service.DisputeService;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns 200 rather than 201 from triage: the decision is computed on demand and the dispute
 * already exists at the payment provider, so there is no new resource to point a Location header at.
 */
@RestController
@RequestMapping(path = "/api/v1/disputes", produces = MediaType.APPLICATION_JSON_VALUE)
public class DisputeController {

    private final DisputeService service;

    public DisputeController(DisputeService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public DisputeResponse triage(@Valid @RequestBody CreateDisputeRequest request) {
        var outcome = request.hasText()
                ? service.triageText(request.text())
                : service.triage(request.dispute().toCase());
        return DisputeResponse.from(outcome);
    }

    /** Approval step. It sends the response for real and is final, so it is separate from triage. */
    @PostMapping("/{disputeId}/approve")
    public ApproveResponse approve(@PathVariable String disputeId) {
        DisputeCase.requireId("disputeId", disputeId);
        service.approve(disputeId);
        return new ApproveResponse(disputeId, true);
    }

    public record ApproveResponse(String disputeId, boolean approved) {}
}
