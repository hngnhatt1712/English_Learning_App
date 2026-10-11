"""Level service — contract sections 6.3, 7.3, 9.2, D11, D14, D22.

Business rules implemented here:
- compute_status_map: determine ContentStatus for each Level (contract 9.2, D14, D22).
- is_level_unlocked: single-level predicate; shared with profile_service.
- list_levels: fetch all Levels, build LevelDto list with placeholder
  stars/topics values (real values deferred to Sprint 4-7).

Constants (D11, contract 6.3, 7.3):
    TOPICS_PER_LEVEL  = 3   → maxStars = 3 * 5 * 3 = 45 per Level  (contract 6.3)
    ROUNDS_PER_TOPIC  = 5
    MAX_STARS_PER_ROUND = 3
    TOTAL_LEVELS      = 3   → overall maxStars = 45 * 3 = 135       (contract 7.3)

Cross-checked against contract:
  § 6.3  "maxStars": 45  (3 topics × 5 rounds × 3 stars)
  § 7.3  Profile "maxStars": 135  (45 × 3 levels)
  § 9.2  Level status rules (Round/Topic unlock cascading, completed/unlocked rules)
  D11    1 Topic = 1 Lesson, 5 Rounds per Lesson
  D14    Level 1 always unlocked; null selectedLevelId → all 3 unlocked
  D22    Levels in progress_level_ids remain unlocked even when no longer selected
"""
from __future__ import annotations

import uuid
from collections.abc import Sequence
from typing import AbstractSet, Optional

from sqlalchemy.orm import Session

from app.models.level import Level
from app.repositories.level_repository import LevelRepository
from app.schemas.enums import ContentStatus
from app.schemas.level import LevelDto

# ---------------------------------------------------------------------------
# Constants (D11) — cross-checked with contract 6.3 and 7.3
# ---------------------------------------------------------------------------
TOPICS_PER_LEVEL: int = 3
ROUNDS_PER_TOPIC: int = 5
MAX_STARS_PER_ROUND: int = 3
MAX_STARS_PER_LEVEL: int = TOPICS_PER_LEVEL * ROUNDS_PER_TOPIC * MAX_STARS_PER_ROUND  # 45
TOTAL_LEVELS: int = 3
MAX_STARS_TOTAL: int = MAX_STARS_PER_LEVEL * TOTAL_LEVELS  # 135


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------

def compute_status_map(
    levels: Sequence[Level],
    selected_level_id: Optional[uuid.UUID],
    completed_level_ids: AbstractSet[uuid.UUID] = frozenset(),
    progress_level_ids: AbstractSet[uuid.UUID] = frozenset(),
) -> dict[uuid.UUID, ContentStatus]:
    """Return a mapping {level.id → ContentStatus} for every level in *levels*.

    Rules (contract 9.2, D14, D22):
    - Level with id in completed_level_ids → COMPLETED.
    - Otherwise UNLOCKED if:
        * selected_level_id is None (first-time user, all unlocked per D14 / contract 7.4)
        * order_index == 1 (Level 1 always unlocked per D14)
        * id == selected_level_id (currently selected level is unlocked)
        * previous level in order_index is in completed_level_ids (cascading unlock per 9.2)
        * id in progress_level_ids (level with progress remains unlocked per D22)
    - All other levels → LOCKED.

    Defensive:
    - Empty list → returns {}.
    - selected_level_id not present in the list → treated as unknown level;
      only order_index==1, progress/completed, or prev-completed unlocked.
    - Levels are sorted by order_index internally to determine consecutive order.
    """
    result: dict[uuid.UUID, ContentStatus] = {}
    if not levels:
        return result

    sorted_levels = sorted(levels, key=lambda lvl: lvl.order_index)
    prev_level: Optional[Level] = None

    for lvl in sorted_levels:
        prev_is_completed = prev_level is not None and prev_level.id in completed_level_ids

        if lvl.id in completed_level_ids:
            result[lvl.id] = ContentStatus.COMPLETED
        elif (
            selected_level_id is None
            or lvl.order_index == 1
            or lvl.id == selected_level_id
            or prev_is_completed
            or lvl.id in progress_level_ids
        ):
            result[lvl.id] = ContentStatus.UNLOCKED
        else:
            result[lvl.id] = ContentStatus.LOCKED

        prev_level = lvl

    return result


def is_level_unlocked(
    level_id: uuid.UUID,
    levels: Sequence[Level],
    selected_level_id: Optional[uuid.UUID],
    completed_level_ids: AbstractSet[uuid.UUID] = frozenset(),
    progress_level_ids: AbstractSet[uuid.UUID] = frozenset(),
) -> bool:
    """Return True when the given level is UNLOCKED or COMPLETED.

    Exported with this exact name for shared use with profile_service.

    Args:
        level_id:             The level whose status is queried.
        levels:               Full list of all levels (any order).
        selected_level_id:    The user's currently selected level id (may be None).
        completed_level_ids:  Set of level IDs completed by the user.
        progress_level_ids:   Set of level IDs with recorded user progress.
    """
    status_map = compute_status_map(
        levels,
        selected_level_id,
        completed_level_ids=completed_level_ids,
        progress_level_ids=progress_level_ids,
    )
    status = status_map.get(level_id, ContentStatus.LOCKED)
    return status in (ContentStatus.UNLOCKED, ContentStatus.COMPLETED)


def _build_level_dto(level: Level, status: ContentStatus) -> LevelDto:
    """Build a LevelDto manually from a Level ORM object.

    Sprint 3 placeholders per spec:
        stars           = 0          (real value: Sprint 4-7)
        max_stars       = 45         (constant D11 / contract 6.3)
        topics_completed= 0          (real value: Sprint 4-7)
        topics_total    = 3          (constant D11 / contract 6.3)
    """
    return LevelDto(
        id=level.id,
        name=level.name,
        description=level.description,
        order_index=level.order_index,
        status=status,
        stars=0,
        max_stars=MAX_STARS_PER_LEVEL,
        topics_completed=0,
        topics_total=TOPICS_PER_LEVEL,
    )


# ---------------------------------------------------------------------------
# Public service function
# ---------------------------------------------------------------------------

def list_levels(
    session: Session,
    selected_level_id: Optional[uuid.UUID],
    repo: Optional[LevelRepository] = None,
    completed_level_ids: AbstractSet[uuid.UUID] = frozenset(),
    progress_level_ids: AbstractSet[uuid.UUID] = frozenset(),
) -> list[LevelDto]:
    """Fetch all Levels and return a list of LevelDto sorted by order_index.

    Sorting is performed in the service layer (not trusted from repository).
    The repository already orders by order_index, but we re-sort here as an
    explicit contract guarantee.

    Args:
        session:              SQLAlchemy Session (sync).
        selected_level_id:    The user's ``selectedLevelId`` from their profile
                              (may be None when the user has not yet chosen).
        repo:                 Optional ``LevelRepository`` instance.  When None
                              (the default, used by production routers), a new
                              ``LevelRepository(session)`` is created internally.
                              Pass a fake repository in tests to avoid DB access.
        completed_level_ids:  Set of level IDs completed by the user.
        progress_level_ids:   Set of level IDs with recorded user progress.

    Returns:
        A list of ``LevelDto``, sorted ascending by ``order_index``.
        Returns ``[]`` if no levels are found in the database.
    """
    if repo is None:
        repo = LevelRepository(session)
    levels: Sequence[Level] = repo.get_all()

    if not levels:
        return []

    # Defensive: sort by order_index in service regardless of repo order.
    sorted_levels = sorted(levels, key=lambda lvl: lvl.order_index)

    status_map = compute_status_map(
        sorted_levels,
        selected_level_id,
        completed_level_ids=completed_level_ids,
        progress_level_ids=progress_level_ids,
    )

    return [
        _build_level_dto(lvl, status_map[lvl.id])
        for lvl in sorted_levels
    ]
