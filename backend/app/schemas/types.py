"""Shared schema primitives."""
from datetime import datetime, timezone
from typing import Annotated

from pydantic import AfterValidator, PlainSerializer

USERNAME_PATTERN = r"^[A-Za-z0-9_.]+$"
USERNAME_MIN = 3
USERNAME_MAX = 30


def _to_utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc)


def _format_utc(value: datetime) -> str:
    return value.strftime("%Y-%m-%dT%H:%M:%SZ")


UtcDatetime = Annotated[
    datetime,
    AfterValidator(_to_utc),
    PlainSerializer(_format_utc, return_type=str, when_used="json"),
]
