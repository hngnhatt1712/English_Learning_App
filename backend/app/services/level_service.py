"""Level DTO orchestration and compatibility wrappers for the shared access rules."""
import uuid
from collections.abc import Sequence
from collections.abc import Set as AbstractSet

from app.models.level import Level
from app.models.user import User
from app.repositories.level_repository import LevelRepository
from app.schemas.enums import ContentStatus
from app.schemas.level import LevelDto
from app.services.content_constants import (
    MAX_STARS_PER_ROUND,
    MAX_STARS_PER_TOPIC,
    ROUNDS_PER_TOPIC,
)
from app.services.level_progress_provider import (
    OrderedLevel,
    compute_level_statuses,
    get_user_level_progress,
)
from sqlalchemy.orm import Session

# Legacy MVP limits retained for existing Profile statistics callers.
# GET /levels uses actual topic counts from its snapshot instead.
TOPICS_PER_LEVEL = 3
TOTAL_LEVELS = 3
MAX_STARS_PER_LEVEL = TOPICS_PER_LEVEL * MAX_STARS_PER_TOPIC
MAX_STARS_TOTAL = MAX_STARS_PER_LEVEL * TOTAL_LEVELS


def compute_status_map(
    levels: Sequence[Level | OrderedLevel],
    selected_level_id: uuid.UUID | None,
    completed_level_ids: AbstractSet[uuid.UUID] = frozenset(),
    progress_level_ids: AbstractSet[uuid.UUID] = frozenset(),
) -> dict[uuid.UUID, ContentStatus]:
    """Compatibility wrapper; Level rules live only in the progress provider."""
    return compute_level_statuses(levels, selected_level_id, completed_level_ids, progress_level_ids)


def is_level_unlocked(
    level_id: uuid.UUID,
    levels: Sequence[Level | OrderedLevel],
    selected_level_id: uuid.UUID | None,
    completed_level_ids: AbstractSet[uuid.UUID] = frozenset(),
    progress_level_ids: AbstractSet[uuid.UUID] = frozenset(),
) -> bool:
    """Compatibility predicate for callers with an existing progress context."""
    statuses = compute_status_map(levels, selected_level_id, completed_level_ids, progress_level_ids)
    return statuses.get(level_id, ContentStatus.LOCKED) in (
        ContentStatus.UNLOCKED, ContentStatus.COMPLETED,
    )


def list_levels(
    session: Session,
    user: User,
    repo: LevelRepository | None = None,
) -> list[LevelDto]:
    """Map the current user's actual progress snapshot to contract Level DTOs."""
    snapshot = get_user_level_progress(
        session, user, level_repo=repo if repo is not None else LevelRepository(session)
    )
    return [
        LevelDto(
            id=level.id,
            name=level.name,
            description=level.description,
            order_index=level.order_index,
            status=snapshot.status_of(level.id),
            stars=snapshot.stats_by_level_id[level.id].stars,
            max_stars=snapshot.stats_by_level_id[level.id].max_stars,
            topics_completed=snapshot.stats_by_level_id[level.id].topics_completed,
            topics_total=snapshot.stats_by_level_id[level.id].topics_total,
        )
        for level in snapshot.levels
    ]

__all__ = [
    "MAX_STARS_PER_LEVEL",
    "MAX_STARS_PER_ROUND",
    "MAX_STARS_TOTAL",
    "ROUNDS_PER_TOPIC",
    "TOPICS_PER_LEVEL",
    "TOTAL_LEVELS",
    "compute_status_map",
    "is_level_unlocked",
    "list_levels",
]
