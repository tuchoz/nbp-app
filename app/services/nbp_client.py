"""HTTPS NBP client with bounded retry and response validation."""

import asyncio
import logging
import ssl
from datetime import datetime
from zoneinfo import ZoneInfo

import httpx
from pydantic import ValidationError

from app.config import Settings
from app.models.exchange_rate import ExchangeRate, NBPResponse

logger = logging.getLogger(__name__)
WARSAW = ZoneInfo("Europe/Warsaw")


class NBPUnavailableError(Exception):
    """NBP cannot currently provide a valid USD exchange rate."""


def _is_tls_failure(exc: BaseException) -> bool:
    current: BaseException | None = exc
    while current is not None:
        if isinstance(current, ssl.SSLError):
            return True
        current = current.__cause__ or current.__context__
    return False


class NBPClient:
    def __init__(self, settings: Settings, client: httpx.AsyncClient):
        self.settings = settings
        self.client = client

    async def fetch_usd(self) -> ExchangeRate:
        retries = self.settings.nbp_retry_attempts
        for attempt in range(retries + 1):
            retryable = False
            try:
                response = await self.client.get(
                    self.settings.nbp_usd_url,
                    headers={"Accept": "application/json"},
                )
                response.raise_for_status()
                data = NBPResponse.model_validate(response.json())
                result = data.to_exchange_rate()
                if result.publication_date > datetime.now(WARSAW).date():
                    raise ValueError("NBP publication date cannot be in the future")
                return result
            except httpx.HTTPStatusError as exc:
                status = exc.response.status_code
                retryable = status == 429 or status >= 500
                logger.warning(
                    "nbp_http_error", extra={"nbp_status": status, "attempt": attempt + 1}
                )
            except (httpx.TimeoutException, httpx.NetworkError) as exc:
                retryable = not _is_tls_failure(exc)
                logger.warning(
                    "nbp_network_error",
                    extra={"error_type": type(exc).__name__, "attempt": attempt + 1},
                )
            except (ValidationError, ValueError, UnicodeError) as exc:
                logger.warning("nbp_invalid_response", extra={"error_type": type(exc).__name__})
            if not retryable or attempt == retries:
                raise NBPUnavailableError("NBP API is unavailable or returned invalid data")
            await asyncio.sleep(self.settings.nbp_retry_base_delay_seconds * (2**attempt))
        raise NBPUnavailableError("NBP API unavailable")  # pragma: no cover
