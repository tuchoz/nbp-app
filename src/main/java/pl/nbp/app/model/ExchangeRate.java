package pl.nbp.app.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRate(
        String currency,
        String code,
        BigDecimal rate,
        LocalDate effectiveDate,
        String tableNumber
) {}