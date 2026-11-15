import httpx
import pytest
import respx

from app.config import Settings
from app.services.nbp_client import NBPClient, NBPUnavailableError

URL = "https://api.nbp.pl/api/exchangerates/rates/a/usd/"


@pytest.mark.asyncio
async def test_fetch_success(sample_payload):
    with respx.mock(assert_all_called=True) as mock:
        route = mock.get(URL).mock(return_value=httpx.Response(200, json=sample_payload))
        async with httpx.AsyncClient() as client:
            result = await NBPClient(Settings(nbp_retry_attempts=0), client).fetch_usd()
        assert result.code == "USD"
        assert route.call_count == 1


@pytest.mark.asyncio
async def test_503_is_retried(sample_payload):
    with respx.mock as mock:
        route = mock.get(URL).mock(
            side_effect=[httpx.Response(503), httpx.Response(200, json=sample_payload)]
        )
        async with httpx.AsyncClient() as client:
            result = await NBPClient(
                Settings(nbp_retry_attempts=1, nbp_retry_base_delay_seconds=0), client
            ).fetch_usd()
        assert result.mid > 0
        assert route.call_count == 2


@pytest.mark.asyncio
async def test_404_is_not_retried():
    with respx.mock as mock:
        route = mock.get(URL).mock(return_value=httpx.Response(404))
        async with httpx.AsyncClient() as client:
            with pytest.raises(NBPUnavailableError):
                await NBPClient(Settings(nbp_retry_attempts=3), client).fetch_usd()
        assert route.call_count == 1


@pytest.mark.asyncio
async def test_timeout_is_retried():
    with respx.mock as mock:
        route = mock.get(URL).mock(side_effect=httpx.ConnectTimeout("timeout"))
        async with httpx.AsyncClient() as client:
            with pytest.raises(NBPUnavailableError):
                await NBPClient(
                    Settings(nbp_retry_attempts=2, nbp_retry_base_delay_seconds=0), client
                ).fetch_usd()
        assert route.call_count == 3


@pytest.mark.asyncio
async def test_wrong_currency_rejected(sample_payload):
    payload = dict(sample_payload, code="EUR")
    with respx.mock as mock:
        mock.get(URL).mock(return_value=httpx.Response(200, json=payload))
        async with httpx.AsyncClient() as client:
            with pytest.raises(NBPUnavailableError):
                await NBPClient(Settings(nbp_retry_attempts=2), client).fetch_usd()


@pytest.mark.asyncio
async def test_bad_json_rejected():
    with respx.mock as mock:
        mock.get(URL).mock(return_value=httpx.Response(200, text="not-json"))
        async with httpx.AsyncClient() as client:
            with pytest.raises(NBPUnavailableError):
                await NBPClient(Settings(nbp_retry_attempts=0), client).fetch_usd()


@pytest.mark.asyncio
async def test_redirect_to_http_is_not_followed():
    with respx.mock as mock:
        route = mock.get(URL).mock(
            return_value=httpx.Response(302, headers={"location": "http://example.com"})
        )
        async with httpx.AsyncClient(follow_redirects=False) as client:
            with pytest.raises(NBPUnavailableError):
                await NBPClient(Settings(nbp_retry_attempts=0), client).fetch_usd()
        assert route.call_count == 1
