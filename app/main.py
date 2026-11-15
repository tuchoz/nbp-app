"""ASGI app factory: lifecycle-managed HTTP client and Kubernetes-friendly endpoints."""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from time import perf_counter

import httpx
from fastapi import FastAPI, Request
from fastapi.responses import Response
from fastapi.staticfiles import StaticFiles
from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest

from app.api.routes import router
from app.config import APP_DIR, Settings
from app.logging_config import configure_logging
from app.services.nbp_client import NBPClient
from app.services.rate_service import RateService

logger = logging.getLogger(__name__)
HTTP_REQUESTS = Counter("nbp_http_requests_total", "HTTP requests", ["method", "path", "status"])
HTTP_LATENCY = Histogram("nbp_http_request_duration_seconds", "HTTP latency", ["method", "path"])


def create_app(settings: Settings | None = None) -> FastAPI:
    config = settings or Settings()
    configure_logging(config.log_level)

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        # Never disable TLS verification or follow redirects to HTTP.
        async with httpx.AsyncClient(
            timeout=httpx.Timeout(config.nbp_timeout_seconds),
            verify=True,
            follow_redirects=False,
            trust_env=False,
        ) as http_client:
            app.state.rate_service = RateService(NBPClient(config, http_client), config)
            yield

    app = FastAPI(title="NBP USD Exchange Rate", lifespan=lifespan)
    app.mount("/static", StaticFiles(directory=str(APP_DIR / "static")), name="static")
    app.include_router(router)

    @app.middleware("http")
    async def request_logging(request: Request, call_next):
        start = perf_counter()
        path = request.url.path
        # No label cardinality from arbitrary user-supplied paths.
        label_path = path if path in {"/", "/health/live", "/health/ready", "/metrics"} else "other"
        status = 500
        try:
            response = await call_next(request)
            status = response.status_code
            return response
        finally:
            duration = perf_counter() - start
            if label_path != "/metrics":
                HTTP_REQUESTS.labels(request.method, label_path, str(status)).inc()
                HTTP_LATENCY.labels(request.method, label_path).observe(duration)
            logger.info(
                "http_request",
                extra={
                    "method": request.method,
                    "path": label_path,
                    "status": status,
                    "duration_ms": round(duration * 1000, 2),
                },
            )

    @app.get("/metrics", include_in_schema=False)
    async def metrics():
        # Expose only inside trusted cluster network via Gateway/Ingress route configuration.
        return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)

    return app


app = create_app()
