package pl.nbp.app.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import pl.nbp.app.client.NbpClient;
import pl.nbp.app.model.NbpResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateServiceTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    // Zegar umożliwiający symulowanie upływu czasu
    private static class MutableClock extends Clock {

        private Instant currentTime;

        MutableClock(Instant startTime) {
            this.currentTime = startTime;
        }

        void advance(Duration duration) {
            currentTime = currentTime.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return WARSAW;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(currentTime, zone);
        }

        @Override
        public Instant instant() {
            return currentTime;
        }
    }

    private MutableClock createClock() {
        return new MutableClock(
                Instant.parse("2026-11-23T09:00:00Z")
        );
    }

    private NbpResponse createResponse() {
        return new NbpResponse(
                "A",
                "dolar amerykański",
                "USD",
                List.of()
        );
    }

    @Test
    void shouldUseCachedResponse() {
        NbpClient client = mock(NbpClient.class);
        MutableClock clock = createClock();

        NbpResponse response = createResponse();

        when(client.fetchLatestRate()).thenReturn(response);

        RateService service = new RateService(client, clock);

        assertSame(response, service.getLatestRate());
        assertSame(response, service.getLatestRate());

        verify(client, times(1)).fetchLatestRate();
    }

    @Test
    void shouldFetchNewRateAfterCacheExpires() {
        NbpClient client = mock(NbpClient.class);
        MutableClock clock = createClock();

        NbpResponse first = createResponse();

        NbpResponse second = new NbpResponse(
                "A", "nowy kurs", "USD", List.of()
        );

        when(client.fetchLatestRate()).thenReturn(first, second);

        RateService service = new RateService(client, clock);

        assertSame(first, service.getLatestRate());

        // Po 16 minutach cache wygasa
        clock.advance(Duration.ofMinutes(16));

        assertSame(second, service.getLatestRate());

        verify(client, times(2)).fetchLatestRate();
    }

    @Test
    void shouldReturnCachedRateWhenNbpFails() {
        NbpClient client = mock(NbpClient.class);
        MutableClock clock = createClock();

        NbpResponse response = createResponse();

        when(client.fetchLatestRate())
                .thenReturn(response)
                .thenThrow(new ResourceAccessException("NBP unavailable"));

        RateService service = new RateService(client, clock);

        assertSame(response, service.getLatestRate());

        clock.advance(Duration.ofMinutes(16));

        // Przy awarii zwracamy ostatni zapisany kurs
        assertSame(response, service.getLatestRate());

        verify(client, times(2)).fetchLatestRate();
    }

    @Test
    void shouldThrowExceptionWhenNbpFailsWithoutCache() {
        NbpClient client = mock(NbpClient.class);
        MutableClock clock = createClock();

        when(client.fetchLatestRate())
                .thenThrow(new ResourceAccessException("NBP unavailable"));

        RateService service = new RateService(client, clock);

        assertThrows(
                ResourceAccessException.class,
                service::getLatestRate
        );
    }

    @Test
    void shouldRejectCachedRateOlderThan14Days() {
        NbpClient client = mock(NbpClient.class);
        MutableClock clock = createClock();

        NbpResponse response = createResponse();

        when(client.fetchLatestRate())
                .thenReturn(response)
                .thenThrow(new ResourceAccessException("NBP unavailable"));

        RateService service = new RateService(client, clock);

        service.getLatestRate();

        // Po 15 dniach zapasowy kurs jest zbyt stary
        clock.advance(Duration.ofDays(15));

        assertThrows(
                ResourceAccessException.class,
                service::getLatestRate
        );

        verify(client, times(2)).fetchLatestRate();
    }

    @Test
    void shouldNotFetchAgainBeforeCacheExpires() {
        NbpClient client = mock(NbpClient.class);
        MutableClock clock = createClock();

        NbpResponse response = createResponse();

        when(client.fetchLatestRate()).thenReturn(response);

        RateService service = new RateService(client, clock);

        for (int i = 0; i < 10; i++) {
            assertSame(response, service.getLatestRate());
        }

        verify(client, times(1)).fetchLatestRate();
    }

    @Test
    void shouldUseOneMinuteCacheDuringPublicationWindow() {
        NbpClient client = mock(NbpClient.class);

        // Poniedziałek, godzina 12:00 w Polsce
        MutableClock clock = new MutableClock(
                Instant.parse("2026-11-23T11:00:00Z")
        );

        when(client.fetchLatestRate())
                .thenReturn(createResponse());

        RateService service = new RateService(client, clock);

        service.getLatestRate();

        // Po 30 sekundach cache nadal jest ważny
        clock.advance(Duration.ofSeconds(30));
        service.getLatestRate();

        verify(client, times(1)).fetchLatestRate();

        // Po 61 sekundach cache wygasł
        clock.advance(Duration.ofSeconds(31));
        service.getLatestRate();

        verify(client, times(2)).fetchLatestRate();
    }
}