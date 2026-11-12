"""Read-only retrieval of topics and user-owned topic progress."""
import uuid
from collections.abc import Sequence
from dataclasses import dataclass

from app.models.progress import UserTopicProgress
from app.models.topic import Topic
from sqlalchemy import case, func, select
from sqlalchemy.orm import Session


@dataclass(frozen=True)
class LevelTopicProgress:
    """Grouped topic progress for one user in one level."""

    topics_with_progress: int
    topics_completed: int
    stars: int


class TopicRepository:
    """Query topic content and progress without computing access states."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def get_by_id(self, topic_id: uuid.UUID) -> Topic | None:
        """Return the topic with this ID, or None when it does not exist."""
        stmt = select(Topic).where(Topic.id == topic_id)
        return self.db.scalars(stmt).one_or_none()

    def get_by_level_id(self, level_id: uuid.UUID) -> list[Topic]:
        """Return all topics in a level in deterministic content order."""
        stmt = (
            select(Topic)
            .where(Topic.level_id == level_id)
            .order_by(Topic.order_index.asc(), Topic.id.asc())
        )
        return list(self.db.scalars(stmt).all())

    def get_progress_by_topic_ids(
        self,
        user_id: uuid.UUID,
        topic_ids: Sequence[uuid.UUID],
    ) -> dict[uuid.UUID, UserTopicProgress]:
        """Retrieve one user's progress in one batch, skipping empty input."""
        if not topic_ids:
            return {}
        stmt = select(UserTopicProgress).where(
            UserTopicProgress.user_id == user_id,
            UserTopicProgress.topic_id.in_(topic_ids),
        )
        return {progress.topic_id: progress for progress in self.db.scalars(stmt).all()}

    def get_counts_by_level(self) -> dict[uuid.UUID, int]:
        """Count content topics for all levels in one grouped query."""
        stmt = select(Topic.level_id, func.count(Topic.id)).group_by(Topic.level_id)
        return {level_id: int(count) for level_id, count in self.db.execute(stmt)}

    def get_progress_totals_by_level(self, user_id: uuid.UUID) -> dict[uuid.UUID, LevelTopicProgress]:
        """Aggregate only the current user's topic progress in one query."""
        stmt = (
            select(
                Topic.level_id,
                func.count(UserTopicProgress.topic_id),
                func.coalesce(func.sum(case((UserTopicProgress.status == "completed", 1), else_=0)), 0),
                func.coalesce(func.sum(UserTopicProgress.stars), 0),
            )
            .join(UserTopicProgress, UserTopicProgress.topic_id == Topic.id)
            .where(UserTopicProgress.user_id == user_id)
            .group_by(Topic.level_id)
        )
        return {
            level_id: LevelTopicProgress(int(count), int(completed or 0), int(stars or 0))
            for level_id, count, completed, stars in self.db.execute(stmt)
        }
