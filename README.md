# NBP USD/PLN — aplikacja

Aplikacja FastAPI prezentująca ostatni opublikowany średni kurs USD/PLN z oficjalnego API Narodowego Banku Polskiego.

**Demo:** https://136-69-119-54.sslip.io/

## Technologie

Python 3.13, FastAPI, Jinja2, Pydantic, httpx, pytest, Docker, Google Cloud Build.

## Funkcjonalności

- Pobieranie i walidacja kursu USD z tabeli A NBP.
- Cache w pamięci aplikacji i obsługa niedostępności API.
- Wyświetlanie ostatnich poprawnych danych w przypadku błędu NBP.
- Endpointy `/health/live`, `/health/ready` i `/metrics`.
- Logowanie w formacie JSON.
- Uruchamianie kontenera bez uprawnień root.

## Cache

NBP wskazuje godziny **11:45–12:15** jako przedział publikacji tabeli A. Uwzględniono to w mechanizmie cache: między 11:45 a 12:30 kurs sprawdzany jest częściej, a poza tym okresem rzadziej.

Cache jest lokalny dla każdego Poda. W większej aplikacji można zastosować **Redis**, zapewniający współdzielony cache dla wszystkich replik. Przy tak prostym zastosowaniu dodatkowa usługa byłaby zbędna.

## Uruchomienie lokalne

```bash
uv sync --locked
uv run --locked uvicorn app.main:app --reload --port 8080
```

Aplikacja: http://localhost:8080/

## Testy

```bash
uv run --locked pytest
uv run --locked ruff check .
```

Testy wykorzystują mocki API NBP, dzięki czemu nie wymagają dostępu do zewnętrznego serwisu.

## CI/CD

Pull Request uruchamia testy i kontrolę jakości kodu. Merge do `main` buduje obraz Docker i publikuje go w Google Artifact Registry.

Obraz nie jest wdrażany automatycznie. Wersję produkcyjną wybiera się przez digest SHA256 w repozytorium `nbp-deployment`.

## Możliwe usprawnienia

**SonarQube** zwiększyłby poziom kontroli jakości i bezpieczeństwa kodu poprzez analizę statyczną, wykrywanie potencjalnych podatności, błędów i problemów z utrzymaniem kodu. Quality Gate umożliwiłby blokowanie merge niespełniającego ustalonych standardów.

Ze względu na ograniczony czas integracja SonarQube nie została zrealizowana. Stanowi ona możliwy kierunek dalszego rozwoju.