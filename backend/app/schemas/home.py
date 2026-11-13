"""Draft Home response shapes, including the explicitly authorized extensions."""
from typing import Literal
from uuid import UUID

from app.core.schemas import CamelModel
from app.schemas.profile import LevelBriefDto
from app.schemas.streak import StreakDto
from app.services.content_constants import ROUNDS_PER_TOPIC
from pydantic import Field


class HomeRoundDto(CamelModel):
    id: UUID
    round_number: int = Field(ge=1, le=ROUNDS_PER_TOPIC)
    type: Literal["learn", "recognition", "matching", "application", "review"]


class HomeCurrentTopicDto(CamelModel):
    id: UUID
    name: str
    lesson_id: UUID
    round: HomeRoundDto | None
    is_new: bool


class HomeDto(CamelModel):
    username: str
    level: LevelBriefDto | None
    streak: StreakDto
    total_stars: int = Field(ge=0)
    max_stars: int = Field(ge=0)
    current_topic: HomeCurrentTopicDto | None
    review_count: int = Field(ge=0)
    flashcard_count: int = Field(ge=0)
