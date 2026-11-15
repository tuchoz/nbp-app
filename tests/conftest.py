from datetime import date
from decimal import Decimal

import pytest

from app.models.exchange_rate import ExchangeRate


@pytest.fixture
def sample_payload():
    return {
        "table": "A",
        "currency": "dolar amerykański",
        "code": "USD",
        "rates": [{"no": "220/A/NBP/2026", "effectiveDate": "2026-11-10", "mid": 3.7421}],
    }


@pytest.fixture
def sample_rate():
    return ExchangeRate(
        name="dolar amerykański",
        code="USD",
        mid=Decimal("3.7421"),
        publication_date=date(2026, 11, 10),
        table_number="220/A/NBP/2026",
    )
