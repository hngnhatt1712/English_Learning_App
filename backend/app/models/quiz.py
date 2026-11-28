"""Persist submitted quiz results and the learner's answers."""
import uuid
from datetime import datetime
from decimal import Decimal
from typing import TYPE_CHECKING, Any

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    Numeric,
    String,
    UniqueConstraint,
    Uuid,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.base import Base

if TYPE_CHECKING:
    from app.models.question import Question
    from app.models.topic import Topic
    from app.models.user import User


class QuizAttempt(Base):
    __tablename__ = "quiz_attempts"
    __table_args__ = (
        # Extend the owner check in the same revision that adds the Grammar FK.
        CheckConstraint(
            "kind = 'vocabulary_quiz' AND topic_id IS NOT NULL AND grammar_lesson_id IS NULL",
            name="kind_owner_match",
        ),
        CheckConstraint("total_questions > 0", name="total_questions_positive"),
        CheckConstraint("correct_count BETWEEN 0 AND total_questions", name="correct_count_range"),
        CheckConstraint("score_percent BETWEEN 0 AND 100", name="score_percent_range"),
        Index("ix_quiz_attempts_user_id_completed_at", "user_id", "completed_at"),
        Index("ix_quiz_attempts_topic_id", "topic_id"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False,
    )
    kind: Mapped[str] = mapped_column(String(20), nullable=False)
    topic_id: Mapped[uuid.UUID | None] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("topics.id", ondelete="RESTRICT"), nullable=True,
    )
    # Sprint 9 adds the FK after creating grammar_lessons.
    grammar_lesson_id: Mapped[uuid.UUID | None] = mapped_column(Uuid(as_uuid=True), nullable=True)
    total_questions: Mapped[int] = mapped_column(Integer, nullable=False)
    correct_count: Mapped[int] = mapped_column(Integer, nullable=False)
    score_percent: Mapped[Decimal] = mapped_column(Numeric(4, 1), nullable=False)
    completed_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    user: Mapped["User"] = relationship()
    topic: Mapped["Topic | None"] = relationship()
    answers: Mapped[list["QuizAnswer"]] = relationship(back_populates="attempt", passive_deletes="all")


class QuizAnswer(Base):
    __tablename__ = "quiz_answers"
    __table_args__ = (
        UniqueConstraint("attempt_id", "question_id", name="uq_quiz_answers_attempt_id_question_id"),
        CheckConstraint("jsonb_typeof(answer) = 'object'", name="answer_object"),
        Index("ix_quiz_answers_question_id", "question_id"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    attempt_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("quiz_attempts.id", ondelete="CASCADE"), nullable=False,
    )
    question_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), ForeignKey("questions.id", ondelete="RESTRICT"), nullable=False,
    )
    is_correct: Mapped[bool] = mapped_column(Boolean, nullable=False)
    # Store the validated AnswerDto submitted by the learner.
    answer: Mapped[dict[str, Any]] = mapped_column(JSONB(none_as_null=True), nullable=False)

    attempt: Mapped["QuizAttempt"] = relationship(back_populates="answers")
    question: Mapped["Question"] = relationship()
