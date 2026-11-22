package pl.nbp.app.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestClientException;
import pl.nbp.app.model.NbpRate;
import pl.nbp.app.model.NbpResponse;
import pl.nbp.app.service.RateService;
import pl.nbp.app.service.RateSnapshot;

import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Controller
public class RateController {

    private static final ZoneId WARSAW =
            ZoneId.of("Europe/Warsaw");

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final RateService rateService;

    public RateController(RateService rateService) {
        this.rateService = rateService;
    }

    @GetMapping("/")
    public String index(
            Model model,
            HttpServletResponse httpResponse
    ) {
        try {
            RateSnapshot snapshot = rateService.getRateSnapshot();

            NbpResponse response = snapshot.response();
            NbpRate rate = response.rates().get(0);

            model.addAttribute(
                    "currency",
                    response.currency()
            );

            model.addAttribute(
                    "code",
                    response.code()
            );

            model.addAttribute(
                    "formattedRate",
                    rate.mid().setScale(4, RoundingMode.HALF_UP)
            );

            model.addAttribute(
                    "publicationDate",
                    rate.effectiveDate().format(DATE_FORMAT)
            );

            model.addAttribute(
                    "tableNumber",
                    rate.no()
            );

            model.addAttribute(
                    "checkedAt",
                    snapshot.checkedAt()
                            .atZone(WARSAW)
                            .format(DATE_TIME_FORMAT)
            );

            model.addAttribute("stale", snapshot.stale());
            model.addAttribute("error", false);

        } catch (RestClientException | IllegalStateException exception) {

            httpResponse.setStatus(
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE
            );

            model.addAttribute("error", true);
        }

        return "index";
    }
}