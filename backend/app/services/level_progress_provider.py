"""User-owned Level snapshots and the single implementation of Level access rules."""
import uuid
from collections.abc import Mapping, Sequence
from collections.abc import Set as AbstractSet
from dataclasses import dataclass
from types import MappingProxyType
from typing import Protocol

from app.models.level import Level
from app.models.user import User
from app.repositories.level_repository import LevelRepository
from app.repositories.topic_repository import LevelTopicProgress, TopicRepository
from app.schemas.enums import ContentStatus
from app.services.content_constants import MAX_STARS_PER_TOPIC
from sqlalchemy.orm import Session


class OrderedLevel(Protocol):
    """The read-only attributes needed by the pure access calculation."""

    @property
    def id(self) -> uuid.UUID: ...

    @property
    def order_index(self) -> int: ...


@dataclass(frozen=True)
class LevelInfo:
    """Copy of content fields, independent of mutable ORM instances."""

    id: uuid.UUID
    name: str
    description: str | None
    order_index: int


@dataclass(frozen=True)
class LevelProgressStats:
    topics_total: int
    topics_completed: int
    stars: int
    max_stars: int


@dataclass(frozen=True)
class UserLevelProgress:
    """Immutable snapshot scoped to a single user; no cross-request caching."""

    user_id: uuid.UUID
    levels: tuple[LevelInfo, ...]
    selected_level_id: uuid.UUID | None
    completed_level_ids: frozenset[uuid.UUID]
    progress_level_ids: frozenset[uuid.UUID]
    stats_by_level_id: Mapping[uuid.UUID, LevelProgressStats]
    statuses: Mapping[uuid.UUID, ContentStatus]

    def __post_init__(self) -> None:
        object.__setattr__(self, "levels", tuple(self.levels))
        object.__setattr__(self, "completed_level_ids", frozenset(self.completed_level_ids))
        object.__setattr__(self, "progress_level_ids", frozenset(self.progress_level_ids))
        object.__setattr__(self, "stats_by_level_id", MappingProxyType(dict(self.stats_by_level_id)))
        object.__setattr__(self, "statuses", MappingProxyType(dict(self.statuses)))

    def status_of(self, level_id: uuid.UUID) -> ContentStatus:
        """Unknown IDs are inaccessible; services handle resource-not-found errors."""
        return self.statuses.get(level_id, ContentStatus.LOCKED)

    def is_unlocked(self, level_id: uuid.UUID) -> bool:
        return self.status_of(level_id) in (ContentStatus.UNLOCKED, ContentStatus.COMPLETED)


def compute_level_statuses(
    levels: Sequence[Level | OrderedLevel],
    selected_level_id: uuid.UUID | None,
    completed_level_ids: AbstractSet[uuid.UUID] = frozenset(),
    progress_level_ids: AbstractSet[uuid.UUID] = frozenset(),
) -> dict[uuid.UUID, ContentStatus]:
    """Apply completion precedence and unlock rules without I/O or mutation."""
    statuses: dict[uuid.UUID, ContentStatus] = {}
    previous: Level | OrderedLevel | None = None
    for level in sorted(levels, key=lambda item: (item.order_index, item.id)):
        if level.id in completed_level_ids:
            status = ContentStatus.COMPLETED
        elif (
            selected_level_id is None
            or previous is None
            or level.id == selected_level_id
            or level.id in progress_level_ids
            or previous.id in completed_level_ids
        ):
            status = ContentStatus.UNLOCKED
        else:
            status = ContentStatus.LOCKED
        statuses[level.id] = status
        previous = level
    return statuses


def build_level_progress(
    user_id: uuid.UUID,
    selected_level_id: uuid.UUID | None,
    levels: Sequence[Level],
    topic_counts: Mapping[uuid.UUID, int],
    progress_totals: Mapping[uuid.UUID, LevelTopicProgress],
) -> UserLevelProgress:
    """Build a snapshot from already user-filtered aggregates, without I/O."""
    ordered = tuple(
        LevelInfo(level.id, level.name, level.description, level.order_index)
        for level in sorted(levels, key=lambda item: (item.order_index, item.id))
    )
    stats: dict[uuid.UUID, LevelProgressStats] = {}
    completed: set[uuid.UUID] = set()
    with_progress: set[uuid.UUID] = set()
    for level in ordered:
        count = topic_counts.get(level.id, 0)
        progress = progress_totals.get(level.id, LevelTopicProgress(0, 0, 0))
        stats[level.id] = LevelProgressStats(
            count, progress.topics_completed, progress.stars, count * MAX_STARS_PER_TOPIC
        )
        if count > 0 and progress.topics_completed == count:
            completed.add(level.id)
        if progress.topics_with_progress > 0:
            with_progress.add(level.id)
    return UserLevelProgress(
        user_id, ordered, selected_level_id, frozenset(completed), frozenset(with_progress),
        stats, compute_level_statuses(ordered, selected_level_id, completed, with_progress),
    )


def get_user_level_progress(
    db: Session,
    user: User,
    *,
    level_repo: LevelRepository | None = None,
) -> UserLevelProgress:
    """Read three batches regardless of Level count; never write or cache data."""
    levels = (level_repo if level_repo is not None else LevelRepository(db)).get_all()
    topics = TopicRepository(db)
    counts = topics.get_counts_by_level()
    # TODO(sprint-5): Include round and vocabulary progress sources
    # when their models and repositories become available.
    progress = topics.get_progress_totals_by_level(user.id)
    return build_level_progress(user.id, user.selected_level_id, levels, counts, progress)
