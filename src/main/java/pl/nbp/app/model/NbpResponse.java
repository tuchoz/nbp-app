package pl.nbp.app.model;

import java.util.List;

public record NbpResponse(
        String table,
        String currency,
        String code,
        List<NbpRate> rates
) {}