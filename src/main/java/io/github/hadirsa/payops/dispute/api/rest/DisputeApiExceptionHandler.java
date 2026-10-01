package io.github.hadirsa.payops.dispute.api.rest;

import io.github.hadirsa.payops.dispute.api.service.DisputeTriageException;
import io.github.hadirsa.payops.dispute.api.service.UnreadableDisputeException;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeConflictException;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeGatewayException;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeNotFoundException;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * RFC 9457 problem details for the dispute API. Extending {@link ResponseEntityExceptionHandler} also
 * covers Spring MVC's own errors (malformed JSON, wrong content type) with the same format.
 */
@RestControllerAdvice(assignableTypes = DisputeController.class)
public class DisputeApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DisputeApiExceptionHandler.class);

    @ExceptionHandler(DisputeNotFoundException.class)
    ProblemDetail handleNotFound(DisputeNotFoundException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Dispute not found");
        return problem;
    }

    @ExceptionHandler(DisputeConflictException.class)
    ProblemDetail handleConflict(DisputeConflictException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Dispute cannot be changed");
        return problem;
    }

    @ExceptionHandler(UnreadableDisputeException.class)
    ProblemDetail handleUnreadable(UnreadableDisputeException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        problem.setTitle("Dispute could not be read from the text");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadInput(IllegalArgumentException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(DisputeGatewayException.class)
    ProblemDetail handleGatewayFailure(DisputeGatewayException e) {
        log.warn("Outside system call failed", e);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "A payment system failed to answer.");
        problem.setTitle("Payment system failed");
        return problem;
    }

    @ExceptionHandler(DisputeTriageException.class)
    ProblemDetail handleTriageFailure(DisputeTriageException e) {
        log.warn("Dispute triage failed", e);
        var status = e.isTimeout() ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
        var problem = ProblemDetail.forStatusAndDetail(status,
                e.isTimeout() ? "The model provider timed out." : "The model provider failed to triage the dispute.");
        problem.setTitle("Dispute triage failed");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = e.getBody();
        problem.setDetail("Request body is invalid.");
        Map<String, String> errors = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(), f -> String.valueOf(f.getDefaultMessage()), (a, b) -> a));
        problem.setProperty("errors", errors);
        return handleExceptionInternal(e, problem, headers, status, request);
    }
}
