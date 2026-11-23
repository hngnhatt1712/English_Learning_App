"""Shared Streak response shape from contract section 6.9."""
from datetime import date

from app.core.schemas import CamelModel
from pydantic import Field


class StreakDto(CamelModel):
    current: int = Field(ge=0)
    last_learning_date: date | None
    learned_today: bool
