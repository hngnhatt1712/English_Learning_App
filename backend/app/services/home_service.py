"""Draft Home orchestration using one user-owned Level progress snapshot."""
import logging
from collections.abc import Sequence
from typing import TypedDict

from app.core.errors import AppError, ErrorType
from app.models.user import User
from app.repositories.lesson_repository import LessonRepository
from app.schemas.enums import ContentStatus
from app.schemas.home import HomeCurrentTopicDto, HomeDto
from app.schemas.profile import LevelBriefDto
from app.schemas.streak import StreakDto
from app.services import topic_service
from app.services.level_progress_provider import (
    UserLevelProgress,
    get_user_level_progress,
)
from app.services.topic_service import TopicState
from sqlalchemy.orm import Session

logger = logging.getLogger(__name__)


class _HomePlaceholders(TypedDict):
    streak: StreakDto
    total_stars: int
    review_count: int
    flashcard_count: int


def _home_placeholders() -> _HomePlaceholders:
    # TODO(sprint-9): Replace Home streak and aggregate
    # placeholders with real learning statistics.
    return {
        "streak": StreakDto(current=0, last_learning_date=None, learned_today=False),
        "total_stars": 0,
        "review_count": 0,
        "flashcard_count": 0,
    }


def _current_topic_is_new() -> bool:
    # TODO(sprint-7): Determine whether the user has
    # started any Round within the current Topic.
    return True


def compute_progress_percent(stars: int, max_stars: int) -> float:
    """Return a percentage rounded to one decimal, including empty content."""
    return round(stars / max_stars * 100, 1) if max_stars > 0 else 0.0


def select_current_topic(states: Sequence[TopicState]) -> TopicState | None:
    """Select the first effectively unlocked Topic without mutating input."""
    return min(
        (state for state in states if state.status == ContentStatus.UNLOCKED),
        key=lambda state: (state.topic.order_index, state.topic.id),
        default=None,
    )


def _selected_level(snapshot: UserLevelProgress) -> LevelBriefDto | None:
    if snapshot.selected_level_id is None:
        return None
    for level in snapshot.levels:
        if level.id == snapshot.selected_level_id:
            stats = snapshot.stats_by_level_id[level.id]
            return LevelBriefDto(
                id=level.id, name=level.name, stars=stats.stars, max_stars=stats.max_stars,
                progress_percent=compute_progress_percent(stats.stars, stats.max_stars),
            )
    raise AppError(404, ErrorType.NOT_FOUND, "Level not found")


def _current_topic(db: Session, state: TopicState) -> HomeCurrentTopicDto:
    lessons = LessonRepository(db).get_by_topic_id(state.topic.id)
    if not lessons:
        logger.error("Home current Topic has no Lesson")
        raise AppError(500, ErrorType.INTERNAL_ERROR, "Learning content is unavailable")
    return HomeCurrentTopicDto(
        id=state.topic.id, name=state.topic.name, lesson_id=lessons[0].id,
        # TODO(sprint-5): Resolve the first incomplete Round
        # when Round and Activity data become available.
        round=None,
        is_new=_current_topic_is_new(),
    )


def get_home(db: Session, user: User) -> HomeDto:
    """Build Home with at most six read queries, independent of content count."""
    snapshot = get_user_level_progress(db, user)
    level = _selected_level(snapshot)
    current_topic = None
    if level is not None:
        states = topic_service.get_topic_states_for_level(db, user.id, level.id, snapshot=snapshot)
        selected = select_current_topic(states)
        if selected is not None:
            current_topic = _current_topic(db, selected)
    return HomeDto(
        username=user.username, level=level, current_topic=current_topic,
        max_stars=sum(stats.max_stars for stats in snapshot.stats_by_level_id.values()),
        **_home_placeholders(),
    )
