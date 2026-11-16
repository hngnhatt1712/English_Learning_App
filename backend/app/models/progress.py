"""Per-user content progress storage; transition rules belong to services."""
import uuid
from datetime import datetime
from decimal import Decimal
from typing import TYPE_CHECKING

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    Numeric,
    String,
    Uuid,
    func,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.base import Base

if TYPE_CHECKING:
    from app.models.activity import Activity
    from app.models.user import User
    from app.models.vocabulary import Vocabulary

TOPIC_STATUS_VALUES: tuple[str, ...] = ("locked", "unlocked", "completed")
ROUND_STATUS_VALUES: tuple[str, ...] = TOPIC_STATUS_VALUES
VOCABULARY_STATUS_VALUES: tuple[str, ...] = (
    "new", "skipped", "in_flashcard", "learning", "review", "mastered",
)
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


class UserRoundProgress(Base):
    """Stored progress only; submit writes, best-score retention and unlocks belong to Sprint 7."""

    __tablename__ = "user_round_progress"
    __table_args__ = (
        CheckConstraint("status IN ('locked', 'unlocked', 'completed')", name="status_valid"),
        CheckConstraint("stars >= 0 AND stars <= 3", name="stars_range"),
        CheckConstraint("attempts >= 0", name="attempts_nonnegative"),
        CheckConstraint(
            "best_score IS NULL OR (best_score >= 0 AND best_score <= 100)",
            name="best_score_range",
        ),
        Index("ix_user_round_progress_round_id", "round_id"),
    )

    # The composite PK also provides UNIQUE(user_id, round_id) and user-first lookups.
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE", name="fk_user_round_progress_user_id_users"),
        primary_key=True,
    )
    round_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("activities.id", ondelete="RESTRICT", name="fk_user_round_progress_round_id_activities"),
        primary_key=True,
    )
    status: Mapped[str] = mapped_column(
        String(20), nullable=False, default="locked", server_default=text("'locked'"),
    )
    best_score: Mapped[Decimal | None] = mapped_column(Numeric(4, 1), nullable=True, default=None)
    stars: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default=text("0"))
    attempts: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default=text("0"))
    # First completion time: future submit service sets and preserves this value.
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True, default=None)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now(), onupdate=func.now(),
    )

    user: Mapped["User"] = relationship(back_populates="round_progress")
    activity: Mapped["Activity"] = relationship(back_populates="round_progress")

    def __repr__(self) -> str:
        return f"<UserRoundProgress(round_id={self.round_id}, status={self.status!r}, stars={self.stars})>"


class UserVocabularyProgress(Base):
    """Vocabulary state and SRS storage, without action or scheduling side effects."""

    __tablename__ = "user_vocabulary_progress"
    __table_args__ = (
        CheckConstraint(
            "status IN ('new', 'skipped', 'in_flashcard', 'learning', 'review', 'mastered')",
            name="status_valid",
        ),
        CheckConstraint("repetition_count >= 0", name="repetition_count_nonnegative"),
        CheckConstraint("interval >= 0", name="interval_nonnegative"),
        CheckConstraint(
            "ease_factor >= 1.3 AND ease_factor NOT IN "
            "('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)",
            name="ease_factor_min_finite",
        ),
        CheckConstraint(
            "(status IN ('new', 'skipped') AND is_in_flashcard = false) OR "
            "(status IN ('in_flashcard', 'learning', 'review', 'mastered') AND is_in_flashcard = true)",
            name="status_flashcard_consistent",
        ),
        Index("ix_user_vocabulary_progress_vocabulary_id", "vocabulary_id"),
    )

    # The composite PK also covers uniqueness and user-first lookups.
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE", name="fk_user_vocabulary_progress_user_id_users"),
        primary_key=True,
    )
    vocabulary_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "vocabularies.id", ondelete="RESTRICT",
            name="fk_user_vocabulary_progress_vocabulary_id_vocabularies",
        ),
        primary_key=True,
    )
    status: Mapped[str] = mapped_column(
        String(20), nullable=False, default="new", server_default=text("'new'"),
    )
    is_in_flashcard: Mapped[bool] = mapped_column(
        Boolean, nullable=False, default=False, server_default=text("false"),
    )
    repetition_count: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default=text("0"))
    interval: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default=text("0"))
    # Unconstrained NUMERIC preserves decimal precision without imposing an SRS ceiling.
    ease_factor: Mapped[Decimal] = mapped_column(
        Numeric(asdecimal=True), nullable=False, default=Decimal("2.5"), server_default=text("2.5"),
    )
    # Action/SRS services decide when to set, preserve or reset these instants.
    last_reviewed: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True, default=None)
    next_review: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True, default=None)
    added_to_flashcard_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True, default=None,
    )

    user: Mapped["User"] = relationship(back_populates="vocabulary_progress")
    vocabulary: Mapped["Vocabulary"] = relationship(back_populates="vocabulary_progress")

    def __repr__(self) -> str:
        return f"<UserVocabularyProgress(vocabulary_id={self.vocabulary_id}, status={self.status!r})>"
