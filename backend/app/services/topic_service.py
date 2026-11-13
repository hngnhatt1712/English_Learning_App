"""Compute and retrieve topic states according to contract sections 7.4 and 9.2."""
import uuid
from collections.abc import Mapping, Sequence
from dataclasses import dataclass

from app.core.errors import AppError, ErrorType
from app.models.progress import UserTopicProgress
from app.models.topic import Topic
from app.repositories.lesson_repository import LessonRepository
from app.repositories.topic_repository import TopicRepository
from app.repositories.user_repository import UserRepository
from app.schemas.enums import ContentStatus
from app.schemas.lesson import LessonSummaryDto
from app.schemas.topic import TopicDetailDto, TopicDto
from app.services.content_constants import MAX_STARS_PER_TOPIC, ROUNDS_PER_TOPIC
from app.services.level_progress_provider import (
    UserLevelProgress,
    get_user_level_progress,
)
from sqlalchemy.orm import Session


@dataclass(frozen=True)
class TopicState:
    """A topic and its effective state for a particular user."""

    topic: Topic
    status: ContentStatus
    stars: int


def compute_topic_states(
    topics_in_order: Sequence[Topic],
    progress_by_topic_id: Mapping[uuid.UUID, UserTopicProgress],
    level_unlocked: bool,
) -> list[TopicState]:
    """Compute states without I/O or mutation; progress must belong to one user.

    Only completed progress affects access. Preserve stored stars even when
    locked: the contract does not require clearing them when access changes.
    """
    result: list[TopicState] = []
    for topic in sorted(topics_in_order, key=lambda item: (item.order_index, item.id)):
        progress = progress_by_topic_id.get(topic.id)
        if not level_unlocked:
            status = ContentStatus.LOCKED
        elif progress is not None and progress.status == ContentStatus.COMPLETED:
            status = ContentStatus.COMPLETED
        elif not result or result[-1].status == ContentStatus.COMPLETED:
            status = ContentStatus.UNLOCKED
        else:
            status = ContentStatus.LOCKED
        result.append(TopicState(topic, status, progress.stars if progress is not None else 0))
    return result


def get_topic_states_for_level(
    db: Session,
    user_id: uuid.UUID,
    level_id: uuid.UUID,
    *,
    snapshot: UserLevelProgress | None = None,
) -> list[TopicState]:
    """Return every topic using the current user's centralized Level snapshot."""
    if snapshot is None:
        user = UserRepository(db).get_by_id(user_id)
        if user is None:
            raise AppError(404, ErrorType.NOT_FOUND, "User not found")
        snapshot = get_user_level_progress(db, user)
    elif snapshot.user_id != user_id:
        raise ValueError("Level progress snapshot must belong to the requested user")
    if level_id not in snapshot.statuses:
        raise AppError(404, ErrorType.NOT_FOUND, "Level not found")
    topics_repo = TopicRepository(db)
    topics = topics_repo.get_by_level_id(level_id)
    if not topics:
        return []
    progress = topics_repo.get_progress_by_topic_ids(user_id, [topic.id for topic in topics])
    return compute_topic_states(topics, progress, snapshot.is_unlocked(level_id))


def get_topic_state(
    db: Session,
    user_id: uuid.UUID,
    topic_id: uuid.UUID,
) -> TopicState:
    """Return a topic's state in the context of all topics in its level."""
    topic = TopicRepository(db).get_by_id(topic_id)
    if topic is None:
        raise AppError(404, ErrorType.NOT_FOUND, "Topic not found")
    states = get_topic_states_for_level(
        db,
        user_id,
        topic.level_id,
    )
    for state in states:
        if state.topic.id == topic_id:
            return state
    raise AppError(404, ErrorType.NOT_FOUND, "Topic not found")


def _topic_dto(state: TopicState) -> TopicDto:
    """Share Topic fields and temporary Round statistics across both endpoints."""
    topic = state.topic
    # TODO(sprint-7): Replace placeholder round progress
    # with values derived from user_round_progress.
    return TopicDto(
        id=topic.id, level_id=topic.level_id, name=topic.name, description=topic.description,
        order_index=topic.order_index, status=state.status, stars=state.stars,
        max_stars=MAX_STARS_PER_TOPIC, rounds_completed=0, rounds_total=ROUNDS_PER_TOPIC,
        progress_percent=0.0, theme_key=topic.theme_key or "default",
    )


def list_topics(db: Session, user_id: uuid.UUID, level_id: uuid.UUID) -> list[TopicDto]:
    """Return topic DTOs with Round statistics deferred until Sprint 7."""
    return [_topic_dto(state) for state in get_topic_states_for_level(db, user_id, level_id)]


def get_topic_detail(db: Session, user_id: uuid.UUID, topic_id: uuid.UUID) -> TopicDetailDto:
    """Include actual lessons even for a locked topic; never fabricate content."""
    state = get_topic_state(db, user_id, topic_id)
    lessons = LessonRepository(db).get_by_topic_id(topic_id)
    return TopicDetailDto(
        **_topic_dto(state).model_dump(),
        lessons=[LessonSummaryDto.model_validate(lesson) for lesson in lessons],
    )
