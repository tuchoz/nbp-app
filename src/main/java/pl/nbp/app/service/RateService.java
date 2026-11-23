package pl.nbp.app.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import pl.nbp.app.client.NbpClient;
import pl.nbp.app.model.NbpResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;

@Service
public class RateService {

    private static final Duration MAX_STALE_AGE =
            Duration.ofDays(14);

    private final NbpClient nbpClient;
    private final Clock clock;

    private NbpResponse cachedResponse;
    private Instant cachedAt;
    private Duration cachedTtl;

    @Autowired
    public RateService(NbpClient nbpClient) {
        this(nbpClient, Clock.systemUTC());
    }

    RateService(NbpClient nbpClient, Clock clock) {
        this.nbpClient = nbpClient;
        this.clock = clock;
    }

    public synchronized NbpResponse getLatestRate() {
        return getRateSnapshot().response();
    }

    public synchronized RateSnapshot getRateSnapshot() {
        Instant now = Instant.now(clock);

        if (cachedResponse != null
                && cachedAt != null
                && cachedTtl != null
                && now.isBefore(cachedAt.plus(cachedTtl))) {

            return new RateSnapshot(
                    cachedResponse,
                    cachedAt,
                    false
            );
        }

        try {
            NbpResponse response = nbpClient.fetchLatestRate();

            cachedResponse = response;
            cachedAt = Instant.now(clock);
            cachedTtl = CachePolicy.ttl(
                    ZonedDateTime.now(clock)
            );

            return new RateSnapshot(
                    cachedResponse,
                    cachedAt,
                    false
            );

        } catch (RestClientException exception) {

            if (cachedResponse != null
                    && cachedAt != null
                    && Instant.now(clock).isBefore(
                            cachedAt.plus(MAX_STALE_AGE))) {

                return new RateSnapshot(
                        cachedResponse,
                        cachedAt,
                        true
                );
            }

            throw exception;
        }
    }
}