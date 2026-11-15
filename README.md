# NBP USD — application repository (#1)

A small, production-minded, server-rendered USD/PLN page backed by the **official NBP Table A** API. This repository intentionally owns **only the application, tests, Docker image and Cloud Build CI/BUILD**. The agreed Git repositories are separate:

| Repository | Owner | Responsibility |
| --- | --- | --- |
| **`nbp-app`** (this repository) | Developers | FastAPI, tests, Docker, build and artifact publishing |
| `nbp-deployment` (#2) | Operators | Helm chart, environment values, SLSA verification, operator-controlled deployment |
| `nbp-infrastructure` (#3) | Platform | Terraform, GKE Autopilot, Artifact Registry, IAM, Cloud Build triggers, Binary Authorization, networking |

**A merge to `nbp-app/main` builds an image but never deploys it.** An operator chooses an existing `repository@sha256:...` and updates the environment values via PR in repository #2. That merge (and optional PROD approval) triggers DEPLOY. Do not put Terraform or Helm charts in this repository.

## Features

- Official `https://api.nbp.pl/api/exchangerates/rates/a/usd/` endpoint; latest published rate, **not** `/today` (works on weekends/holidays).
- HTML table: currency name, ISO code, average rate in PLN and NBP publication date; also table number and last successful verification.
- Validated Pydantic response; `Decimal` rate and strict USD / Table A checks.
- HTTPS-only URL validation; official host by default; certificate verification enabled; no HTTP redirects or HTTP fallback.
- `httpx` asynchronous client with timeout, bounded retry for 429/5xx/network timeouts and no retry for invalid payload, 4xx other than 429, or TLS errors.
- Per-process in-memory adaptive TTL, single-flight refresh, stale-if-error up to a configurable maximum age. Stale data are conspicuously marked; no cache data returns HTTP 503.
- Kubernetes `/health/live`, `/health/ready` (neither depends on NBP), `/metrics` (HTTP request counts and latency, **no cache metrics**), structured JSON logs on stdout.
- Non-root Docker runtime; works with read-only root filesystem; Docker multi-stage builds; prod dependencies separated from dev dependencies.
- Tests use mocked HTTP requests (RESPX), no external NBP dependency.

## Layout

```text
nbp-app/
├── app/
│   ├── api/routes.py
│   ├── services/nbp_client.py
│   ├── services/rate_service.py
│   ├── models/exchange_rate.py
│   ├── templates/index.html
│   ├── static/style.css
│   ├── config.py
│   ├── logging_config.py
│   └── main.py
├── tests/
├── docker/Dockerfile
├── cloudbuild/ci.yaml
├── cloudbuild/build.yaml
├── docs/adr/
├── pyproject.toml
├── uv.lock                # generate once: uv lock; then commit
├── .env.example
├── .dockerignore
├── .gitignore
├── sonar-project.properties
├── Makefile
└── README.md
```

## Local development

Requires Python 3.13 and `uv`.

```bash
uv lock                # initial bootstrap, requires PyPI access; commit generated uv.lock
uv sync --locked
uv run --locked pytest --cov=app --cov-branch --cov-report=term-missing
uv run --locked ruff check .
uv run --locked ruff format --check .
uv run --locked uvicorn app.main:app --reload --host 127.0.0.1 --port 8080
```

Open http://127.0.0.1:8080/ (local browser-to-application HTTP is fine; **upstream NBP traffic always uses HTTPS**). The NBP endpoint does not need an API key. Configuration via environment variables (`.env.example` lists defaults). To load manually: `set -a; source .env.example; set +a`.

## Docker

Generate and commit `uv.lock` before building:

```bash
docker build -f docker/Dockerfile -t nbp-usd:local .
docker run --rm --read-only --tmpfs /tmp:rw,noexec,nosuid,size=16m \
  --cap-drop=ALL --security-opt=no-new-privileges \
  --publish 8080:8080 nbp-usd:local
```

The image uses `USER 10001:10001` and listens on unprivileged port 8080. Kubernetes security context and probes belong to the Helm chart in repo #2. For fully controlled image builds, pin base images and tool images by tested immutable digests in CI; this sample currently uses versioned but mutable base-image tags.

## Cache policy

| Local Warsaw time / state | Freshness period |
| --- | ---: |
| Weekday before 11:45 | 60 min |
| Weekday 11:45–12:30 | 5 min |
| Weekday after 12:30, today's table obtained | 60 min |
| Weekday after 12:30, old table | 10 min |
| Saturday/Sunday | 6 h |

Polling is **on demand**, when a request arrives after the freshness period. Revalidation still happens after publication, to detect potential corrections. On refresh failure the last verified value is returned with a prominent stale indicator for at most 14 days after the last successful verification; refresh is retried at most every 60 s while stale. With no usable cached data the page returns `503` with a human-readable message. **No cache metrics** are collected. Cache is local to one process; different GKE replicas can temporarily show different values. If strict cross-replica consistency becomes a requirement, revisit shared caching rather than adding Redis prematurely.

## Cloud Build integration (repo #1)

1. PR trigger runs `cloudbuild/ci.yaml`: locked dependencies, Ruff, pytest/RESPX, coverage XML and SonarQube quality gate. Configure `_SONAR_PROJECT_KEY`, `_SONAR_HOST_URL`, and a Secret Manager secret `sonar-token`. GitHub branch protection must require CI success and test the mergeable revision (merge queue or up-to-date checks).
2. Merge-to-main trigger runs `cloudbuild/build.yaml`: Docker build → Trivy image scan → CycloneDX SBOM → successful publication to Artifact Registry. The `images:` field associates the image with Cloud Build results. Configure `_SBOM_BUCKET` and grant the build service account scoped IAM access to Artifact Registry, the SBOM bucket, and Cloud Logging.
3. On successful completion, Cloud Build can produce provenance/automatic build attestation in the supported setup (`requestedVerifyOption: VERIFIED`). **This build does not start a deployment**. The operator selects an image by digest in the separate Helm repository.

`cloudbuild/*.yaml` files are integration templates: GCP project setup, repository triggers, SonarQube subscription, secrets, IAM, registry/bucket and tested builder image digests must be provisioned separately. Cloud Build *does not* automatically enforce GitHub branch protection or SonarQube PR decoration; configure these in your GitHub/Sonar integration. Keep release attestations out of scope as agreed; the deployment repository will verify build provenance and GKE Binary Authorization will require the Cloud Build-built image condition. That condition by itself does not prove approval by our specific pipeline.

## Kubernetes / monitoring contract for repo #2

- `containerPort: 8080`, `GET /health/live` (liveness), `GET /health/ready` (readiness); readiness does **not** depend on upstream NBP.
- `/metrics` exposes Prometheus HTTP metrics only. **Never expose `/metrics` publicly via Gateway/Ingress**; use an internal scraping route or network control. There are no cache metrics.
- stdout JSON logs are compatible with GKE Cloud Logging; label cardinality for request paths is bounded.
- Use non-root, `allowPrivilegeEscalation: false`, `readOnlyRootFilesystem: true`, drop all capabilities, `seccompProfile: RuntimeDefault`, resource requests/limits, HPA and PDB in Helm.
- Secret Manager integration, if required later, belongs to platform/deployment repo; NBP API itself needs no secret.
- Operator changes `values-dev.yaml`/`values-prod.yaml` containing image repository, human-readable tag and **exact digest**; digest is the source of truth. DEPLOY verifies provenance before Helm, while Binary Authorization enforces the configured admission policy at GKE.

## Tests / failure scenarios

The test suite covers: valid and invalid responses, incorrect code/table, bad JSON, 404/503/timeout/retry, HTTPS-only settings, redirect refusal, cache hit/expiry, single-flight refresh, stale fallback, stale max-age, health probes and HTML/503 responses. CI tests are designed to use RESPX rather than live calls to NBP. Runtime API failure is treated as a recoverable dependency failure; liveness does not restart Pods for upstream outages.

## Engineering decisions and known simplifications

- No React/TypeScript: server-rendered HTML is sufficient.
- No Redis/database: one rate with bounded eventual consistency does not justify extra services.
- No background scheduler: refresh is lazy; actual data freshness depends on users accessing the page.
- No Polish holiday calendar: the last-published-rate endpoint avoids `/today` failures and normal polling will eventually pick up a new table.
- Stale cache is never persisted across Pod restarts; a cold Pod and unavailable NBP return 503.
- No deployment or Terraform from this repository; separation of duties is intentional.
- Security gates are not a proof of zero vulnerabilities. External pipeline accounts and policies must be configured and scoped appropriately.

## Official API

https://api.nbp.pl/ — Table A, latest single-currency USD rate. NBP requires HTTPS since 2025-08-01.
