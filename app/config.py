"""Validated runtime settings, supplied via environment variables."""

from pathlib import Path

from pydantic import Field, HttpUrl, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

APP_DIR = Path(__file__).resolve().parent


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="", extra="ignore")

    nbp_api_base_url: HttpUrl = HttpUrl("https://api.nbp.pl/api/")
    nbp_enforce_official_host: bool = True
    nbp_timeout_seconds: float = Field(default=5.0, gt=0, le=60)
    nbp_retry_attempts: int = Field(default=2, ge=0, le=5)
    nbp_retry_base_delay_seconds: float = Field(default=0.25, ge=0, le=5)
    nbp_cache_prepublication_seconds: int = Field(default=3600, ge=1)
    nbp_cache_publication_seconds: int = Field(default=300, ge=1)
    nbp_cache_postpublication_seconds: int = Field(default=3600, ge=1)
    nbp_cache_missing_publication_seconds: int = Field(default=600, ge=1)
    nbp_cache_nonbusiness_seconds: int = Field(default=21600, ge=1)
    nbp_max_stale_seconds: int = Field(default=14 * 86400, ge=0)
    log_level: str = "INFO"

    @field_validator("nbp_api_base_url")
    @classmethod
    def require_https(cls, value: HttpUrl) -> HttpUrl:
        if value.scheme != "https":
            raise ValueError("NBP_API_BASE_URL must use HTTPS")
        if value.username or value.password or value.query or value.fragment:
            raise ValueError("NBP_API_BASE_URL must not contain credentials, query or fragment")
        return value

    @model_validator(mode="after")
    def validate_official_host(self) -> "Settings":
        if self.nbp_enforce_official_host and (
            self.nbp_api_base_url.host != "api.nbp.pl"
            or self.nbp_api_base_url.port not in (None, 443)
        ):
            raise ValueError("Official NBP API host is required")
        return self

    @property
    def nbp_usd_url(self) -> str:
        return f"{str(self.nbp_api_base_url).rstrip('/')}/exchangerates/rates/a/usd/"
