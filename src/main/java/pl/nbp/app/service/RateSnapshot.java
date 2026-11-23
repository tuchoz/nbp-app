package pl.nbp.app.service;

import pl.nbp.app.model.NbpResponse;

import java.time.Instant;

public record RateSnapshot(
        NbpResponse response,
        Instant checkedAt,
        boolean stale
) {
}