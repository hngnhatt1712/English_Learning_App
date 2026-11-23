"""Lesson and Round response shapes from contract sections 6.5 and 6.6."""
from typing import Literal
from uuid import UUID

from app.core.schemas import CamelModel
from app.schemas.enums import ContentStatus
from app.services.content_constants import MAX_STARS_PER_ROUND, ROUNDS_PER_TOPIC
from pydantic import Field


class LessonSummaryDto(CamelModel):
    id: UUID
    topic_id: UUID
    title: str
    description: str | None
    order_index: int = Field(ge=1)


class RoundDto(CamelModel):
    id: UUID
    lesson_id: UUID
    round_number: int = Field(ge=1, le=ROUNDS_PER_TOPIC)
    type: Literal["learn", "recognition", "matching", "application", "review"]
    title: str
    status: ContentStatus
    stars: int = Field(ge=0, le=MAX_STARS_PER_ROUND)
    best_score_percent: float | None = Field(ge=0, le=100)
    attempts: int = Field(ge=0)
    total_questions: int = Field(ge=0)


class LessonDetailDto(LessonSummaryDto):
    rounds: list[RoundDto]
