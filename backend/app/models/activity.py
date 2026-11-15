"""Round content: an Activity belongs to a Lesson (contract D11, 6.6)."""
import uuid
from typing import TYPE_CHECKING

from sqlalchemy import (
    CheckConstraint,
    ForeignKey,
    Integer,
    String,
    UniqueConstraint,
    Uuid,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.base import Base

if TYPE_CHECKING:
    from app.models.lesson import Lesson
    from app.models.progress import UserRoundProgress
    from app.models.question import Question

ROUND_TYPE_VALUES: tuple[str, ...] = (
    "learn", "recognition", "matching", "application", "review",
)


class Activity(Base):
    """At most five numbered rounds per lesson; seed validation ensures all five exist."""

    __tablename__ = "activities"
    __table_args__ = (
        UniqueConstraint("lesson_id", "round_number", name="uq_activities_lesson_id_round_number"),
        CheckConstraint("round_number BETWEEN 1 AND 5", name="round_number_range"),
        CheckConstraint(
            "round_type IN ('learn', 'recognition', 'matching', 'application', 'review')",
            name="round_type_valid",
        ),
        CheckConstraint(
            "(round_number = 1 AND round_type = 'learn') OR "
            "(round_number = 2 AND round_type = 'recognition') OR "
            "(round_number = 3 AND round_type = 'matching') OR "
            "(round_number = 4 AND round_type = 'application') OR "
            "(round_number = 5 AND round_type = 'review')",
            name="round_number_type_match",
        ),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    lesson_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("lessons.id", ondelete="RESTRICT", name="fk_activities_lesson_id_lessons"),
        nullable=False,
    )
    round_number: Mapped[int] = mapped_column(Integer, nullable=False)
    round_type: Mapped[str] = mapped_column(String(20), nullable=False)
    title: Mapped[str] = mapped_column(String(100), nullable=False)

    lesson: Mapped["Lesson"] = relationship(back_populates="activities")
    questions: Mapped[list["Question"]] = relationship(
        back_populates="activity", passive_deletes="all",
    )
    round_progress: Mapped[list["UserRoundProgress"]] = relationship(
        back_populates="activity", passive_deletes="all",
    )

    def __repr__(self) -> str:
        return f"<Activity(id={self.id}, round_number={self.round_number}, round_type='{self.round_type}')>"
