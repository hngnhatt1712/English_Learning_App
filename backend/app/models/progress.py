"""UserTopicProgress model: per-user progress and stars on a topic (contract 6.4, 9.2, 16)."""
import uuid
from datetime import datetime

from sqlalchemy import (
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    Uuid,
    func,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base

TOPIC_STATUS_VALUES: tuple[str, ...] = ("locked", "unlocked", "completed")
MAX_STARS_PER_TOPIC: int = 15


class UserTopicProgress(Base):
    """Tracks explicit topic unlock/completion status and accumulated stars per user."""

    __tablename__ = "user_topic_progress"
    __table_args__ = (
        CheckConstraint("status IN ('locked', 'unlocked', 'completed')", name="status_valid"),
        CheckConstraint("stars >= 0 AND stars <= 15", name="stars_range"),
        Index("ix_user_topic_progress_topic_id", "topic_id"),
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE", name="fk_user_topic_progress_user_id_users"),
        primary_key=True,
    )
    topic_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("topics.id", ondelete="RESTRICT", name="fk_user_topic_progress_topic_id_topics"),
        primary_key=True,
    )
    status: Mapped[str] = mapped_column(String(20), nullable=False)
    stars: Mapped[int] = mapped_column(
        Integer, nullable=False, default=0, server_default=text("0")
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
        onupdate=func.now(),
    )

    def __repr__(self) -> str:
        return (
            f"<UserTopicProgress(topic_id={self.topic_id}, "
            f"status='{self.status}', stars={self.stars})>"
        )
