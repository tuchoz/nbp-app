package pl.nbp.app.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CachePolicyTest {

    private static final String ZONE = "Europe/Warsaw";

    @Test
    void shouldUseOneMinuteDuringPublicationWindow() {
        assertTtl("2026-11-23T12:00:00+01:00[Europe/Warsaw]", 1);
    }

    @Test
    void shouldUseFifteenMinutesBeforePublicationWindow() {
        assertTtl("2026-11-23T11:44:00+01:00[Europe/Warsaw]", 15);
    }

    @Test
    void shouldUseOneMinuteAtPublicationWindowStart() {
        assertTtl("2026-11-23T11:45:00+01:00[Europe/Warsaw]", 1);
    }

    @Test
    void shouldUseFifteenMinutesAtPublicationWindowEnd() {
        assertTtl("2026-11-23T12:30:00+01:00[Europe/Warsaw]", 15);
    }

    @Test
    void shouldUseFifteenMinutesOnSaturday() {
        assertTtl("2026-11-21T12:00:00+01:00[Europe/Warsaw]", 15);
    }

    @Test
    void shouldUseFifteenMinutesOnSunday() {
        assertTtl("2026-11-22T12:00:00+01:00[Europe/Warsaw]", 15);
    }

    @Test
    void shouldHandleSummerTime() {
        assertTtl("2026-07-06T12:00:00+02:00[Europe/Warsaw]", 1);
    }

    private void assertTtl(String dateTime, long expectedMinutes) {
        assertEquals(
                Duration.ofMinutes(expectedMinutes),
                CachePolicy.ttl(ZonedDateTime.parse(dateTime))
        );
    }
}