"""In-process adaptive TTL cache, single-flight refresh and stale-if-error."""

import asyncio
import logging
from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime, time
from time import monotonic
from zoneinfo import ZoneInfo

from app.config import Settings
from app.models.exchange_rate import ExchangeRate
from app.services.nbp_client import NBPClient, NBPUnavailableError

logger = logging.getLogger(__name__)
WARSAW = ZoneInfo("Europe/Warsaw")


@dataclass(frozen=True)
class RateResult:
    rate: ExchangeRate
    stale: bool
    checked_at: datetime


class RateService:
    def __init__(
        self,
        client: NBPClient,
        settings: Settings,
        now: Callable[[], datetime] | None = None,
        clock: Callable[[], float] = monotonic,
    ):
        self.client = client
        self.settings = settings
        self._now = now or (lambda: datetime.now(WARSAW))
        self._clock = clock
        self._result: RateResult | None = None
        self._expires_at = 0.0
        self._last_verified_monotonic = 0.0
        self._lock = asyncio.Lock()

    def _ttl(self, published_today: bool) -> int:
        now = self._now().astimezone(WARSAW)

        if now.weekday() >= 5:
            return self.settings.nbp_cache_nonbusiness_seconds

        if now.time() < time(11, 45):
            next_window = datetime.combine(
                now.date(),
                time(11, 45),
                tzinfo=WARSAW,
            )

            seconds_to_window = (next_window - now).total_seconds()

            return min(
                self.settings.nbp_cache_prepublication_seconds,
                max(1, int(seconds_to_window)),
            )

        if now.time() < time(12, 30):
            return self.settings.nbp_cache_publication_seconds

        if published_today:
            return self.settings.nbp_cache_postpublication_seconds

        return self.settings.nbp_cache_missing_publication_seconds

    def _is_usable(self) -> bool:
        if self._result is None or self._clock() >= self._expires_at:
            return False

        return not (
            self._result.stale
            and (
                self._clock() - self._last_verified_monotonic > self.settings.nbp_max_stale_seconds
            )
        )

    async def get_usd(self) -> RateResult:
        if self._is_usable():
            return self._result  # type: ignore[return-value]
        # Re-check after acquiring the lock: concurrent requests share one refresh.
        async with self._lock:
            if self._is_usable():
                return self._result  # type: ignore[return-value]
            try:
                rate = await self.client.fetch_usd()
                checked_at = self._now().astimezone(WARSAW)
                published_today = rate.publication_date == checked_at.date()
                self._result = RateResult(rate=rate, stale=False, checked_at=checked_at)
                self._last_verified_monotonic = self._clock()
                self._expires_at = self._clock() + self._ttl(published_today)
                return self._result
            except NBPUnavailableError:
                if (
                    self._result is not None
                    and self._clock() - self._last_verified_monotonic
                    <= self.settings.nbp_max_stale_seconds
                ):
                    logger.warning("nbp_stale_cache_served")
                    self._expires_at = self._clock() + min(
                        self.settings.nbp_cache_publication_seconds, 60
                    )
                    self._result = RateResult(
                        rate=self._result.rate,
                        stale=True,
                        checked_at=self._result.checked_at,
                    )
                    return self._result
                raise
