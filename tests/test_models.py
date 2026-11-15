from copy import deepcopy
from decimal import Decimal

import pytest
from pydantic import ValidationError

from app.models.exchange_rate import NBPResponse


def test_model_parses_nbp_payload(sample_payload):
    rate = NBPResponse.model_validate(sample_payload).to_exchange_rate()
    assert rate.code == "USD"
    assert rate.mid == Decimal("3.7421")
    assert rate.publication_date.isoformat() == "2026-11-10"


@pytest.mark.parametrize(
    "change",
    [
        {"code": "EUR"},
        {"table": "C"},
        {"rates": []},
        {"rates": [{"no": "a", "effectiveDate": "broken", "mid": 3.7}]},
        {"rates": [{"no": "a", "effectiveDate": "2026-11-10", "mid": -1}]},
    ],
)
def test_bad_data_is_rejected(sample_payload, change):
    payload = deepcopy(sample_payload)
    payload.update(change)
    with pytest.raises(ValidationError):
        NBPResponse.model_validate(payload)
