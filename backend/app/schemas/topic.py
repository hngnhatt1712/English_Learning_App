"""Topic response shapes from contract section 6.4."""
from uuid import UUID

from app.core.schemas import CamelModel
from app.schemas.enums import ContentStatus
from app.schemas.lesson import LessonSummaryDto
from app.services.content_constants import MAX_STARS_PER_TOPIC
from pydantic import Field


class TopicDto(CamelModel):
    id: UUID
    level_id: UUID
    name: str
    description: str | None
    order_index: int = Field(ge=1)
    status: ContentStatus
    stars: int = Field(ge=0, le=MAX_STARS_PER_TOPIC)
    max_stars: int = Field(ge=0)
    rounds_completed: int = Field(ge=0)
    rounds_total: int = Field(ge=0)
    progress_percent: float = Field(ge=0, le=100)
    theme_key: str = Field(min_length=1)


class TopicDetailDto(TopicDto):
    lessons: list[LessonSummaryDto]
