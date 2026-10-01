package io.github.hadirsa.payops.dispute.infra.demo;

import io.github.hadirsa.payops.dispute.api.rest.DisputeApiExceptionHandler;
import io.github.hadirsa.payops.dispute.api.rest.DisputeController;
import io.github.hadirsa.payops.dispute.api.service.DisputeService;
import io.github.hadirsa.payops.dispute.domain.model.DisputeCase;
import io.github.hadirsa.payops.dispute.domain.model.DisputeReason;
import io.github.hadirsa.payops.dispute.domain.model.Money;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DemoCaseControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);

    private final DisputeService service = mock(DisputeService.class);
    private final org.springframework.test.web.servlet.MockMvc demo =
            MockMvcBuilders.standaloneSetup(new DemoCaseController(CLOCK)).build();
    private final org.springframework.test.web.servlet.MockMvc api =
            MockMvcBuilders.standaloneSetup(new DisputeController(service))
                    .setControllerAdvice(new DisputeApiExceptionHandler()).build();

    @Test
    void listsTheSampleIds() throws Exception {
        demo.perform(get("/api/v1/demo/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("dp_demo_fraud"))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void unknownSampleIs404() throws Exception {
        demo.perform(get("/api/v1/demo/cases/nope")).andExpect(status().isNotFound());
    }

    @Test
    void aSampleCanBePostedStraightToTheTriageEndpoint() throws Exception {
        // what the README's pipe does: demo endpoint output becomes the triage request body
        var sample = demo.perform(get("/api/v1/demo/cases/dp_demo_fraud"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        when(service.triage(any())).thenThrow(new RuntimeException("stop here, only the request binding matters"));

        try {
            api.perform(post("/api/v1/disputes").contentType(MediaType.APPLICATION_JSON).content(sample));
        } catch (Exception expected) {
            // the mocked service throws on purpose
        }

        var sent = ArgumentCaptor.forClass(DisputeCase.class);
        verify(service).triage(sent.capture());
        assertEquals(new DisputeCase("dp_demo_fraud", "ch_demo_1", DisputeReason.FRAUDULENT, new Money(24_000, "USD"),
                CLOCK.instant().plusSeconds(12 * 86_400L)), sent.getValue());
    }
}
