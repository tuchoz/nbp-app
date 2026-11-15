from contextlib import asynccontextmanager
from datetime import datetime
from zoneinfo import ZoneInfo

import pytest
from httpx import ASGITransport, AsyncClient

from app.main import create_app
from app.services.nbp_client import NBPUnavailableError
from app.services.rate_service import RateResult


class ServiceStub:
    def __init__(self, result=None, fails=False):
        self.result = result
        self.fails = fails

    async def get_usd(self):
        if self.fails:
            raise NBPUnavailableError("no data")
        return self.result


@asynccontextmanager
async def client_for(stub):
    app = create_app()
    async with app.router.lifespan_context(app):
        app.state.rate_service = stub
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            yield client


@pytest.mark.asyncio
async def test_html_contains_required_fields(sample_rate):
    result = RateResult(
        sample_rate, False, datetime(2026, 11, 10, 14, 0, tzinfo=ZoneInfo("Europe/Warsaw"))
    )
    async with client_for(ServiceStub(result)) as client:
        response = await client.get("/")
        assert response.status_code == 200
        for value in ("dolar amerykański", "USD", "3.7421", "10.11.2026", "220/A/NBP/2026"):
            assert value in response.text


@pytest.mark.asyncio
async def test_stale_html_contains_warning(sample_rate):
    result = RateResult(sample_rate, True, datetime(2026, 11, 10, tzinfo=ZoneInfo("Europe/Warsaw")))
    async with client_for(ServiceStub(result)) as client:
        response = await client.get("/")
        assert response.status_code == 200
        assert "może być nieaktualna" in response.text


@pytest.mark.asyncio
async def test_missing_api_returns_503():
    async with client_for(ServiceStub(fails=True)) as client:
        response = await client.get("/")
        assert response.status_code == 503
        assert "Spróbuj ponownie" in response.text


@pytest.mark.asyncio
async def test_probes_do_not_query_nbp():
    async with client_for(ServiceStub(fails=True)) as client:
        for path in ("/health/live", "/health/ready"):
            response = await client.get(path)
            assert response.status_code == 200


@pytest.mark.asyncio
async def test_metrics_have_no_cache_metrics():
    async with client_for(ServiceStub(fails=True)) as client:
        response = await client.get("/metrics")
        assert response.status_code == 200
        assert "nbp_http_requests_total" in response.text
        assert "nbp_cache_hits_total" not in response.text
