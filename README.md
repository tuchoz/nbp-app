# NBP USD/PLN — aplikacja

Aplikacja Java 21 + Spring Boot 4 prezentująca ostatni opublikowany średni kurs USD/PLN z oficjalnego API NBP.

Demo: https://136-69-119-54.sslip.io/

## Technologie

Java 21, Spring Boot 4, Maven, Thymeleaf, JUnit, WireMock, Docker, Google Cloud Build.

## Funkcjonalności

- Pobieranie i walidacja kursu USD z tabeli A NBP.
- Cache zależny od godzin publikacji NBP.
- Retry, timeouty i ostatnie poprawne dane przy awarii API.
- Liveness, readiness i metryki Prometheus na porcie 9090.
- Kontener uruchamiany bez uprawnień root.

## Uruchomienie lokalne

```powershell
.\mvnw.cmd spring-boot:run
```

Aplikacja: http://localhost:8080/

## Testy

```powershell
.\mvnw.cmd clean verify
```

38 testów z wykorzystaniem mocków i WireMock.

## CI/CD

Pull Request uruchamia testy Maven. Merge do `main` buduje obraz Docker, wykonuje skan Trivy, generuje SBOM CycloneDX i publikuje obraz w Artifact Registry.

Wdrożenie nie jest automatyczne. Operator wybiera digest SHA256 w repozytorium `nbp-deployment`.