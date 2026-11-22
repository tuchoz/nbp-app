package pl.nbp.app.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import pl.nbp.app.model.NbpRate;
import pl.nbp.app.model.NbpResponse;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;

@Component
public class NbpClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_DELAY_MS = 200;

    private final RestClient restClient;
    private final String nbpUrl;

    public NbpClient(
            RestClient.Builder builder,
            @Value("${nbp.api-url:https://api.nbp.pl/api/exchangerates/rates/a/usd/}")
            String nbpUrl) {

        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);

        this.restClient = builder
                .requestFactory(factory)
                .build();

        this.nbpUrl = nbpUrl;
    }

    public NbpResponse fetchLatestRate() {

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {

            try {
                NbpResponse response = restClient.get()
                        .uri(nbpUrl)
                        .retrieve()
                        .body(NbpResponse.class);

                validate(response);

                return response;

            } catch (HttpStatusCodeException exception) {

                int status = exception.getStatusCode().value();

                boolean retryable = status == 429 || status >= 500;

                if (!retryable || attempt == MAX_ATTEMPTS) {
                    throw exception;
                }

            } catch (ResourceAccessException exception) {

                if (!isRetryableNetworkError(exception)
                        || attempt == MAX_ATTEMPTS) {
                    throw exception;
                }
            }

            waitBeforeRetry(attempt);
        }

        throw new IllegalStateException("Nie udało się pobrać kursu NBP");
    }

    private boolean isRetryableNetworkError(Throwable exception) {

        Throwable cause = exception;

        while (cause != null) {

            if (cause instanceof javax.net.ssl.SSLException) {
                return false;
            }

            if (cause instanceof SocketTimeoutException
                    || cause instanceof ConnectException
                    || cause instanceof NoRouteToHostException) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

    private void waitBeforeRetry(int attempt) {

        long delay = INITIAL_DELAY_MS * attempt;

        try {
            Thread.sleep(delay);

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Przerwano ponawianie zapytania NBP",
                    exception
            );
        }
    }

    private void validate(NbpResponse response) {

        if (response == null) {
            throw new IllegalStateException("Pusta odpowiedź NBP");
        }

        if (!"A".equals(response.table())) {
            throw new IllegalStateException("Niepoprawna tabela NBP");
        }

        if (!"USD".equals(response.code())) {
            throw new IllegalStateException("Niepoprawny kod waluty");
        }

        if (response.currency() == null || response.currency().isBlank()) {
            throw new IllegalStateException("Brak nazwy waluty");
        }

        if (response.rates() == null || response.rates().size() != 1) {
            throw new IllegalStateException("Niepoprawna liczba kursów");
        }

        NbpRate rate = response.rates().get(0);

        if (rate == null
                || rate.mid() == null
                || rate.mid().compareTo(BigDecimal.ZERO) <= 0
                || rate.effectiveDate() == null
                || rate.no() == null
                || rate.no().isBlank()) {

            throw new IllegalStateException("Niepoprawne dane kursu");
        }
    }
}