package io.github.hadirsa.payops.dispute.api.rest;

import io.github.hadirsa.payops.dispute.api.service.DisputeService;
import io.github.hadirsa.payops.dispute.api.service.DisputeTriageException;
import io.github.hadirsa.payops.dispute.api.service.UnreadableDisputeException;
import io.github.hadirsa.payops.dispute.domain.decision.Recommendation;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeConflictException;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeGatewayException;
import io.github.hadirsa.payops.dispute.domain.exception.DisputeNotFoundException;
import io.github.hadirsa.payops.dispute.domain.exception.InvalidDisputeException;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static io.github.hadirsa.payops.dispute.api.DisputeTestData.decision;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc: no Spring context and no LLM. */
class DisputeControllerTest {

    private static final String CASE = """
            {"dispute":{"disputeId":"dp_1","paymentId":"ch_1","reason":"product_not_received",
             "amount":{"value":240.00,"currency":"USD"},"respondBy":"2026-10-14T00:00:00Z"}}
            """;

    private final DisputeService service = mock(DisputeService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DisputeController(service))
                .setControllerAdvice(new DisputeApiExceptionHandler())
                .build();
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void structuredMessageIsTranslatedToADomainCaseAndAnswersWithAFlatPublicResponse() throws Exception {
        when(service.triage(any())).thenReturn(
                new DisputeService.Outcome(decision(false, Recommendation.REPRESENT), true, false));

        postJson("/api/v1/disputes", CASE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disputeId").value("dp_1"))
                .andExpect(jsonPath("$.reason").value("PRODUCT_NOT_RECEIVED"))
                .andExpect(jsonPath("$.amount.value").value(240.00))
                .andExpect(jsonPath("$.amount.currency").value("USD"))
                .andExpect(jsonPath("$.recommendation").value("REPRESENT"))
                .andExpect(jsonPath("$.requiresHumanReview").value(false))
                .andExpect(jsonPath("$.evidence[0].type").value("DELIVERY_PROOF"))
                .andExpect(jsonPath("$.evidence[1].found").value(false))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.approved").value(false));

        var sent = ArgumentCaptor.forClass(DisputeCase.class);
        verify(service).triage(sent.capture());
        assertEquals(new DisputeCase("dp_1", "ch_1", DisputeReason.PRODUCT_NOT_RECEIVED, new Money(24_000, "USD"),
                Instant.parse("2026-10-14T00:00:00Z")), sent.getValue());
        verify(service, never()).triageText(any());
    }

    @Test
    void freeTextGoesThroughExtraction() throws Exception {
        when(service.triageText("dispute dp_1, $240")).thenReturn(
                new DisputeService.Outcome(decision(true, Recommendation.REPRESENT), true, false));

        postJson("/api/v1/disputes", "{\"text\":\"dispute dp_1, $240\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresHumanReview").value(true))
                .andExpect(jsonPath("$.reviewReasons[0]").value("MISSING_REQUIRED_EVIDENCE"));
    }

    @Test
    void bodyWithBothOrNeitherIsRejected() throws Exception {
        postJson("/api/v1/disputes", CASE.replace("{\"dispute\"", "{\"text\":\"x\",\"dispute\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
        postJson("/api/v1/disputes", "{}").andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void incompleteOrMalformedMessagesAreRejectedBeforeAnythingRuns() throws Exception {
        postJson("/api/v1/disputes", CASE.replace("\"respondBy\":\"2026-10-14T00:00:00Z\"", "\"respondBy\":null"))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/disputes", CASE.replace("\"currency\":\"USD\"", "\"currency\":\"DOLLARS\""))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/disputes", CASE.replace("\"value\":240.00", "\"value\":-5"))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/disputes", CASE.replace("\"paymentId\":\"ch_1\",", ""))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/disputes", "{\"text\":\"" + "a".repeat(1001) + "\"}").andExpect(status().isBadRequest());
        postJson("/api/v1/disputes", "{not json").andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void valuesThatPassFormatChecksButAreWrongGetAProblemDetailNotAStackTrace() throws Exception {
        postJson("/api/v1/disputes", CASE.replace("product_not_received", "stolen"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("unknown reason")));
        postJson("/api/v1/disputes", CASE.replace("240.00", "240.005"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("decimals")));
        verifyNoInteractions(service);
    }

    @Test
    void textWithoutACompleteDisputeIs422NotAModelFailure() throws Exception {
        when(service.triageText(any())).thenThrow(new UnreadableDisputeException(new InvalidDisputeException("respondBy is required")));

        postJson("/api/v1/disputes", "{\"text\":\"what is the weather\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Dispute could not be read from the text"));
    }

    @Test
    void outsideSystemFailureIs502WithoutLeakingInternals() throws Exception {
        when(service.triage(any())).thenThrow(new DisputeGatewayException("Stripe call failed: secret detail", null));

        postJson("/api/v1/disputes", CASE)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("A payment system failed to answer."));
    }

    @Test
    void modelFailureIs502AndModelTimeoutIs504() throws Exception {
        when(service.triage(any()))
                .thenThrow(new DisputeTriageException("boom", null, false))
                .thenThrow(new DisputeTriageException("slow", null, true));

        postJson("/api/v1/disputes", CASE).andExpect(status().isBadGateway());
        postJson("/api/v1/disputes", CASE).andExpect(status().isGatewayTimeout());
    }

    @Test
    void approveSendsTheResponseForReal() throws Exception {
        mvc.perform(post("/api/v1/disputes/dp_1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disputeId").value("dp_1"))
                .andExpect(jsonPath("$.approved").value(true));
        verify(service).approve("dp_1");
    }

    @Test
    void approveMapsConflictNotFoundAndBadIds() throws Exception {
        doThrow(new DisputeConflictException("not triaged")).when(service).approve("dp_1");
        doThrow(new DisputeNotFoundException("dp_2")).when(service).approve("dp_2");

        mvc.perform(post("/api/v1/disputes/dp_1/approve")).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/disputes/dp_2/approve")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/disputes/bad id!/approve")).andExpect(status().isBadRequest());
    }
}
