import asyncio
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

import pytest

from app.config import Settings
from app.services.nbp_client import NBPUnavailableError
from app.services.rate_service import RateService

WARSAW = ZoneInfo("Europe/Warsaw")


class FakeClock:
    def __init__(self, hour=10, minute=0):
        self.tick = 1000.0

        self.now_value = datetime(
            2026,
            11,
            10,
            hour,
            minute,
            tzinfo=WARSAW,
        )

    def monotonic(self):
        return self.tick

    def now(self):
        return self.now_value

    def advance(self, *, minutes=0, seconds=0):
        delta = timedelta(
            minutes=minutes,
            seconds=seconds,
        )

        self.now_value += delta
        self.tick += delta.total_seconds()


class FakeClient:
    def __init__(self, rate):
        self.rate = rate
        self.calls = 0
        self.error = False

    async def fetch_usd(self):
        self.calls += 1
        await asyncio.sleep(0)
        if self.error:
            raise NBPUnavailableError("unavailable")
        return self.rate


def service_with(fake_client, clock, **kwargs):
    return RateService(fake_client, Settings(**kwargs), clock.now, clock.monotonic)


@pytest.mark.asyncio
async def test_cache_hit_and_expiry(sample_rate):
    clock = FakeClock()
    client = FakeClient(sample_rate)
    service = service_with(client, clock)
    assert (await service.get_usd()).stale is False
    await service.get_usd()
    assert client.calls == 1
    clock.tick += 3601
    await service.get_usd()
    assert client.calls == 2


@pytest.mark.asyncio
async def test_single_flight(sample_rate):
    clock = FakeClock()
    client = FakeClient(sample_rate)
    service = service_with(client, clock)
    results = await asyncio.gather(*(service.get_usd() for _ in range(25)))
    assert len(results) == 25
    assert client.calls == 1


@pytest.mark.asyncio
async def test_stale_if_error_and_persistent_stale_flag(sample_rate):
    clock = FakeClock()
    client = FakeClient(sample_rate)
    service = service_with(client, clock)
    await service.get_usd()
    clock.tick += 3601
    client.error = True
    assert (await service.get_usd()).stale is True
    assert (await service.get_usd()).stale is True
    assert client.calls == 2
    clock.tick += 61
    client.error = False
    assert (await service.get_usd()).stale is False


@pytest.mark.asyncio
async def test_stale_age_is_bounded(sample_rate):
    clock = FakeClock()
    client = FakeClient(sample_rate)
    service = service_with(client, clock, nbp_max_stale_seconds=7200)
    await service.get_usd()
    client.error = True
    clock.tick += 7201
    with pytest.raises(NBPUnavailableError):
        await service.get_usd()


@pytest.mark.asyncio
async def test_no_cache_on_first_failure(sample_rate):
    clock = FakeClock()
    client = FakeClient(sample_rate)
    client.error = True
    with pytest.raises(NBPUnavailableError):
        await service_with(client, clock).get_usd()


@pytest.mark.parametrize(
    "hour,minute,expected",
    [
        (10, 0, 3600),
        (11, 45, 300),
        (12, 0, 300),
        (12, 30, 600),
    ],
)
def test_adaptive_ttl_without_today_rate(sample_rate, hour, minute, expected):
    clock = FakeClock(hour, minute)
    service = service_with(FakeClient(sample_rate), clock)
    assert service._ttl(False) == expected


def test_after_publication_ttl_when_today_published(sample_rate):
    clock = FakeClock(14)
    service = service_with(FakeClient(sample_rate), clock)
    assert service._ttl(True) == 3600


@pytest.mark.parametrize(
    "hour,minute,expected",
    [
        (10, 0, 3600),
        (11, 20, 1500),
        (11, 40, 300),
        (11, 44, 60),
        (11, 45, 300),
    ],
)
def test_ttl_never_crosses_publication_start(
    sample_rate,
    hour,
    minute,
    expected,
):
    clock = FakeClock(hour, minute)

    service = service_with(
        FakeClient(sample_rate),
        clock,
    )

    assert service._ttl(False) == expected


@pytest.mark.asyncio
async def test_prepublication_entry_revalidated_at_window_start(
    sample_rate,
):
    clock = FakeClock(11, 40)

    client = FakeClient(sample_rate)

    service = service_with(client, clock)

    # 11:40 - pierwsze pobranie kursu.
    await service.get_usd()

    assert client.calls == 1

    # 11:44:59 - cache nadal jest ważny.
    clock.advance(minutes=4, seconds=59)

    await service.get_usd()

    assert client.calls == 1

    # 11:45:00 - cache wygasa.
    clock.advance(seconds=1)

    await service.get_usd()

    assert client.calls == 2

    # 11:49:00 - korzystamy z cache.
    clock.advance(minutes=4)

    await service.get_usd()

    assert client.calls == 2

    # 11:50:00 - mija pięciominutowy TTL.
    clock.advance(minutes=1)

    await service.get_usd()

    assert client.calls == 3


@pytest.mark.asyncio
async def test_custom_prepublication_ttl_is_still_capped(
    sample_rate,
):
    clock = FakeClock(11, 44)

    client = FakeClient(sample_rate)

    service = service_with(
        client,
        clock,
        nbp_cache_prepublication_seconds=7200,
    )

    # Pomimo TTL = 2 godziny cache
    # powinien wygasnąć o 11:45.
    await service.get_usd()

    assert client.calls == 1

    clock.advance(minutes=1)

    await service.get_usd()

    assert client.calls == 2
