package pl.nbp.app.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.ResourceAccessException;
import pl.nbp.app.model.NbpRate;
import pl.nbp.app.model.NbpResponse;
import pl.nbp.app.service.RateService;
import pl.nbp.app.service.RateSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateControllerTest {

    private NbpResponse response() {
        return new NbpResponse(
                "A",
                "dolar amerykański",
                "USD",
                List.of(new NbpRate(
                        "226/A/NBP/2026",
                        LocalDate.of(2026, 11, 20),
                        new BigDecimal("4.0613")
                ))
        );
    }

    @Test
    void shouldRenderCurrentRate() {
        RateService service = mock(RateService.class);

        when(service.getRateSnapshot()).thenReturn(
                new RateSnapshot(
                        response(),
                        Instant.parse("2026-11-22T12:00:00Z"),
                        false
                )
        );

        RateController controller = new RateController(service);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse http = new MockHttpServletResponse();

        assertEquals("index", controller.index(model, http));
        assertEquals(200, http.getStatus());

        assertEquals("USD", model.get("code"));
        assertEquals(false, model.get("stale"));
        assertEquals(false, model.get("error"));
        assertEquals(
                new BigDecimal("4.0613"),
                model.get("formattedRate")
        );
    }

    @Test
    void shouldMarkCachedRateAsStale() {
        RateService service = mock(RateService.class);

        when(service.getRateSnapshot()).thenReturn(
                new RateSnapshot(
                        response(),
                        Instant.parse("2026-11-20T12:00:00Z"),
                        true
                )
        );

        RateController controller = new RateController(service);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse http = new MockHttpServletResponse();

        assertEquals("index", controller.index(model, http));
        assertEquals(200, http.getStatus());
        assertEquals(true, model.get("stale"));
        assertEquals(false, model.get("error"));
    }

    @Test
    void shouldReturn503WhenNbpUnavailableWithoutCache() {
        RateService service = mock(RateService.class);

        when(service.getRateSnapshot()).thenThrow(
                new ResourceAccessException("NBP unavailable")
        );

        RateController controller = new RateController(service);
        ExtendedModelMap model = new ExtendedModelMap();
        MockHttpServletResponse http = new MockHttpServletResponse();

        assertEquals("index", controller.index(model, http));
        assertEquals(503, http.getStatus());
        assertEquals(true, model.get("error"));
    }
}