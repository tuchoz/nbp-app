package pl.nbp.app.client;

import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.nbp.app.model.NbpResponse;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class NbpClientTest {

    private WireMockServer wireMock;
    private NbpClient nbpClient;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(0);
        wireMock.start();

        String url = wireMock.baseUrl()
                + "/api/exchangerates/rates/a/usd/";

        nbpClient = new NbpClient(RestClient.builder(), url);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void shouldFetchValidUsdRate() {
        wireMock.stubFor(get(urlEqualTo(
                "/api/exchangerates/rates/a/usd/"
        )).willReturn(okJson("""
                {
                  "table": "A",
                  "currency": "dolar amerykański",
                  "code": "USD",
                  "rates": [{
                    "no": "220/A/NBP/2026",
                    "effectiveDate": "2026-11-16",
                    "mid": 3.65
                  }]
                }
                """)));

        NbpResponse response = nbpClient.fetchLatestRate();

        assertEquals("USD", response.code());
        assertEquals("A", response.table());
        assertEquals(new BigDecimal("3.65"), response.rates().get(0).mid());
        assertEquals(LocalDate.of(2026, 11, 16),
                response.rates().get(0).effectiveDate());

        wireMock.verify(1, getRequestedFor(urlEqualTo(
                "/api/exchangerates/rates/a/usd/"
        )));
    }
    @Test
    void shouldRejectWrongCurrency() {
        wireMock.stubFor(get(urlEqualTo(
                "/api/exchangerates/rates/a/usd/"
        )).willReturn(okJson("""
                {
                "table": "A",
                "currency": "euro",
                "code": "EUR",
                "rates": [{
                    "no": "220/A/NBP/2026",
                    "effectiveDate": "2026-11-16",
                    "mid": 4.25
                }]
                }
                """)));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> nbpClient.fetchLatestRate()
        );

        assertEquals("Niepoprawny kod waluty", exception.getMessage());
    }
    @Test
    void shouldRejectNegativeRate() {
        wireMock.stubFor(get(urlEqualTo(
                "/api/exchangerates/rates/a/usd/"
        )).willReturn(okJson("""
                {
                "table": "A",
                "currency": "dolar amerykański",
                "code": "USD",
                "rates": [{
                    "no": "220/A/NBP/2026",
                    "effectiveDate": "2026-11-16",
                    "mid": -3.65
                }]
                }
                """)));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> nbpClient.fetchLatestRate()
        );

        assertEquals("Niepoprawne dane kursu", exception.getMessage());
    }
    @Test
    void shouldRejectEmptyResponse() {
        wireMock.stubFor(get(urlEqualTo(
                "/api/exchangerates/rates/a/usd/"
        )).willReturn(ok("")));

        assertThrows(
                IllegalStateException.class,
                () -> nbpClient.fetchLatestRate()
        );
    }
    @Test
    void shouldHandleNbpUnavailable() {
        wireMock.stubFor(get(urlEqualTo(
                "/api/exchangerates/rates/a/usd/"
        )).willReturn(aResponse().withStatus(503)));

        assertThrows(
                org.springframework.web.client.HttpServerErrorException.class,
                () -> nbpClient.fetchLatestRate()
        );
    }
    @Test
    void shouldHandleNbpNotFound() {
        wireMock.stubFor(get(urlEqualTo(
                "/api/exchangerates/rates/a/usd/"
        )).willReturn(aResponse().withStatus(404)));

        assertThrows(
                org.springframework.web.client.HttpClientErrorException.NotFound.class,
                () -> nbpClient.fetchLatestRate()
        );
    }
    @Test
    void shouldRetryAfter503AndReturnValidRate() {
        String path = "/api/exchangerates/rates/a/usd/";

        // Pierwsza próba: NBP jest niedostępne
        wireMock.stubFor(get(urlEqualTo(path))
                .inScenario("NBP retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));

        // Druga próba: NBP odpowiada poprawnie
        wireMock.stubFor(get(urlEqualTo(path))
                .inScenario("NBP retry")
                .whenScenarioStateIs("recovered")
                .willReturn(okJson("""
                        {
                        "table": "A",
                        "currency": "dolar amerykański",
                        "code": "USD",
                        "rates": [{
                            "no": "220/A/NBP/2026",
                            "effectiveDate": "2026-11-16",
                            "mid": 3.65
                        }]
                        }
                        """)));

        NbpResponse response = nbpClient.fetchLatestRate();

        assertEquals("USD", response.code());
        assertEquals(new BigDecimal("3.65"), response.rates().get(0).mid());

        // Sprawdzamy, czy wykonano dokładnie 2 zapytania
        wireMock.verify(2, getRequestedFor(urlEqualTo(path)));
    }
    @Test
    void shouldNotRetryAfter404() {
        String path = "/api/exchangerates/rates/a/usd/";

        wireMock.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse().withStatus(404)));

        assertThrows(
                org.springframework.web.client.HttpClientErrorException.NotFound.class,
                () -> nbpClient.fetchLatestRate()
        );

        wireMock.verify(1, getRequestedFor(urlEqualTo(path)));
    }
    @Test
    void shouldRetryAfter429() {
        String path = "/api/exchangerates/rates/a/usd/";

        wireMock.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse().withStatus(429)));

        assertThrows(
                org.springframework.web.client.HttpClientErrorException.TooManyRequests.class,
                () -> nbpClient.fetchLatestRate()
        );

        wireMock.verify(3, getRequestedFor(urlEqualTo(path)));
    }
    @Test
    void shouldNotRetryInvalidRate() {
        String path = "/api/exchangerates/rates/a/usd/";

        wireMock.stubFor(get(urlEqualTo(path))
                .willReturn(okJson("""
                        {
                        "table": "A",
                        "currency": "dolar amerykański",
                        "code": "USD",
                        "rates": [{
                            "no": "220/A/NBP/2026",
                            "effectiveDate": "2026-11-16",
                            "mid": -3.65
                        }]
                        }
                        """)));

        assertThrows(IllegalStateException.class,
                () -> nbpClient.fetchLatestRate());

        wireMock.verify(1, getRequestedFor(urlEqualTo(path)));
    }

    @Test
    void shouldRejectWrongTable() {
        wireMock.stubFor(get(urlEqualTo("/api/exchangerates/rates/a/usd/"))
                .willReturn(okJson("""
                        {
                        "table": "B",
                        "currency": "dolar amerykański",
                        "code": "USD",
                        "rates": [{
                            "no": "220/A/NBP/2026",
                            "effectiveDate": "2026-11-16",
                            "mid": 3.65
                        }]
                        }
                        """)));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> nbpClient.fetchLatestRate());

        assertEquals("Niepoprawna tabela NBP", exception.getMessage());
    }

    @Test
    void shouldRejectMissingCurrencyName() {
        wireMock.stubFor(get(urlEqualTo("/api/exchangerates/rates/a/usd/"))
                .willReturn(okJson("""
                        {
                        "table": "A",
                        "code": "USD",
                        "rates": [{
                            "no": "220/A/NBP/2026",
                            "effectiveDate": "2026-11-16",
                            "mid": 3.65
                        }]
                        }
                        """)));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> nbpClient.fetchLatestRate());

        assertEquals("Brak nazwy waluty", exception.getMessage());
    }

    @Test
    void shouldRejectMissingRates() {
        wireMock.stubFor(get(urlEqualTo("/api/exchangerates/rates/a/usd/"))
                .willReturn(okJson("""
                        {
                        "table": "A",
                        "currency": "dolar amerykański",
                        "code": "USD"
                        }
                        """)));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> nbpClient.fetchLatestRate());

        assertEquals("Niepoprawna liczba kursów", exception.getMessage());
    }

    @Test
    void shouldRejectZeroRate() {
        wireMock.stubFor(get(urlEqualTo("/api/exchangerates/rates/a/usd/"))
                .willReturn(okJson("""
                        {
                        "table": "A",
                        "currency": "dolar amerykański",
                        "code": "USD",
                        "rates": [{
                            "no": "220/A/NBP/2026",
                            "effectiveDate": "2026-11-16",
                            "mid": 0
                        }]
                        }
                        """)));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> nbpClient.fetchLatestRate());

        assertEquals("Niepoprawne dane kursu", exception.getMessage());
    }

    @Test
    void shouldRejectMissingEffectiveDate() {
        wireMock.stubFor(get(urlEqualTo("/api/exchangerates/rates/a/usd/"))
                .willReturn(okJson("""
                        {
                        "table": "A",
                        "currency": "dolar amerykański",
                        "code": "USD",
                        "rates": [{
                            "no": "220/A/NBP/2026",
                            "mid": 3.65
                        }]
                        }
                        """)));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> nbpClient.fetchLatestRate());

        assertEquals("Niepoprawne dane kursu", exception.getMessage());
    }

    @Test
    void shouldRejectMissingTableNumber() {
        wireMock.stubFor(get(urlEqualTo("/api/exchangerates/rates/a/usd/"))
                .willReturn(okJson("""
                        {
                        "table": "A",
                        "currency": "dolar amerykański",
                        "code": "USD",
                        "rates": [{
                            "effectiveDate": "2026-11-16",
                            "mid": 3.65
                        }]
                        }
                        """)));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> nbpClient.fetchLatestRate());

        assertEquals("Niepoprawne dane kursu", exception.getMessage());
    }

    @Test
    void shouldRejectMultipleRates() {
        wireMock.stubFor(get(urlEqualTo("/api/exchangerates/rates/a/usd/"))
                .willReturn(okJson("""
                        {
                        "table": "A",
                        "currency": "dolar amerykański",
                        "code": "USD",
                        "rates": [
                            {
                            "no": "220/A/NBP/2026",
                            "effectiveDate": "2026-11-16",
                            "mid": 3.65
                            },
                            {
                            "no": "221/A/NBP/2026",
                            "effectiveDate": "2026-11-17",
                            "mid": 3.66
                            }
                        ]
                        }
                        """)));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> nbpClient.fetchLatestRate());

        assertEquals("Niepoprawna liczba kursów", exception.getMessage());
    }

    @Test
    void shouldRetryThreeTimesAfter500() {
        String path = "/api/exchangerates/rates/a/usd/";

        wireMock.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(
                org.springframework.web.client.HttpServerErrorException.class,
                () -> nbpClient.fetchLatestRate());

        wireMock.verify(3, getRequestedFor(urlEqualTo(path)));
    }

    @Test
    void shouldRetryThreeTimesAfter502() {
        String path = "/api/exchangerates/rates/a/usd/";

        wireMock.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse().withStatus(502)));

        assertThrows(
                org.springframework.web.client.HttpServerErrorException.class,
                () -> nbpClient.fetchLatestRate());

        wireMock.verify(3, getRequestedFor(urlEqualTo(path)));
    }

    @Test
    void shouldNotRetryAfter400() {
        String path = "/api/exchangerates/rates/a/usd/";

        wireMock.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse().withStatus(400)));

        assertThrows(
                org.springframework.web.client.HttpClientErrorException.class,
                () -> nbpClient.fetchLatestRate());

        wireMock.verify(1, getRequestedFor(urlEqualTo(path)));
    }
}