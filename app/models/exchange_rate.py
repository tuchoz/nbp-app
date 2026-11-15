"""Strict schema and normalized domain value for the NBP table A USD rate."""

from datetime import date
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


class NBPRate(BaseModel):
    model_config = ConfigDict(extra="ignore", strict=True)

    no: str = Field(min_length=1)
    effectiveDate: date  # noqa: N815 - field name comes from external API
    mid: Decimal = Field(gt=0, max_digits=12, decimal_places=6, strict=False)

    @field_validator("effectiveDate", mode="before")
    @classmethod
    def parse_date(cls, value: object) -> date:
        if not isinstance(value, str):
            raise ValueError("effectiveDate must be an ISO date string")
        return date.fromisoformat(value)


class NBPResponse(BaseModel):
    model_config = ConfigDict(extra="ignore", strict=True)

    table: Literal["A"]
    currency: str = Field(min_length=1)
    code: Literal["USD"]
    rates: list[NBPRate] = Field(min_length=1, max_length=1)

    def to_exchange_rate(self) -> "ExchangeRate":
        rate = self.rates[0]
        return ExchangeRate(
            name=self.currency,
            code=self.code,
            mid=rate.mid,
            publication_date=rate.effectiveDate,
            table_number=rate.no,
        )


class ExchangeRate(BaseModel):
    name: str
    code: Literal["USD"]
    mid: Decimal
    publication_date: date
    table_number: str
