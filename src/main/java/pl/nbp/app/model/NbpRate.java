package pl.nbp.app.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NbpRate(
        String no,
        LocalDate effectiveDate,
        BigDecimal mid
) {}