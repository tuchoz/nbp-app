import pytest
from pydantic import ValidationError

from app.config import Settings


def test_https_is_required():
    with pytest.raises(ValidationError, match="HTTPS"):
        Settings(nbp_api_base_url="http://api.nbp.pl/api/")


def test_rejects_untrusted_host_by_default():
    with pytest.raises(ValidationError, match="Official NBP API"):
        Settings(nbp_api_base_url="https://example.com/api/")


def test_custom_https_host_requires_explicit_opt_out():
    s = Settings(nbp_api_base_url="https://mock.example.com/api/", nbp_enforce_official_host=False)
    assert s.nbp_usd_url == "https://mock.example.com/api/exchangerates/rates/a/usd/"


def test_rejects_credentials():
    with pytest.raises(ValidationError, match="credentials"):
        Settings(nbp_api_base_url="https://user:pass@api.nbp.pl/api/")


def test_default_url():
    assert Settings().nbp_usd_url == "https://api.nbp.pl/api/exchangerates/rates/a/usd/"
