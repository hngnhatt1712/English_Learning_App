"""Level schemas (contract 6.3)."""
from uuid import UUID

from pydantic import Field

from app.core.schemas import CamelModel
from app.schemas.enums import ContentStatus


class LevelDto(CamelModel):
    id: UUID
    name: str
    description: str | None
    order_index: int = Field(ge=1)
    status: ContentStatus
    stars: int = Field(ge=0)
    max_stars: int = Field(ge=0)
    topics_completed: int = Field(ge=0)
    topics_total: int = Field(ge=0)
