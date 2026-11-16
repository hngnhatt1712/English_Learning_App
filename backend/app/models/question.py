"""Shared internal question storage; payload includes server-only answers (contract 16.2)."""
import uuid
from typing import TYPE_CHECKING, Any

from sqlalchemy import CheckConstraint, ForeignKey, Index, String, Text, Uuid
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.base import Base

if TYPE_CHECKING:
    from app.models.activity import Activity
    from app.models.topic import Topic

QUESTION_TYPE_VALUES: tuple[str, ...] = (
    "learn_card", "multiple_choice", "picture_choice", "matching", "fill_blank",
    "translate", "sentence_order", "sentence_correction", "sentence_completion",
)
QUESTION_SOURCE_VALUES: tuple[str, ...] = ("round", "vocabulary_quiz", "grammar")
QUESTION_PURPOSE_VALUES: tuple[str, ...] = ("practice", "quiz")


class Question(Base):
    """One owner per source; grammar referential integrity awaits the Grammar migration."""

    __tablename__ = "questions"
    __table_args__ = (
        CheckConstraint(
            "type IN ('learn_card', 'multiple_choice', 'picture_choice', 'matching', "
            "'fill_blank', 'translate', 'sentence_order', 'sentence_correction', "
            "'sentence_completion')",
            name="type_valid",
        ),
        CheckConstraint("source IN ('round', 'vocabulary_quiz', 'grammar')", name="source_valid"),
        CheckConstraint("purpose IS NULL OR purpose IN ('practice', 'quiz')", name="purpose_valid"),
        CheckConstraint(
            "(source = 'round' AND activity_id IS NOT NULL AND topic_id IS NULL "
            "AND grammar_lesson_id IS NULL AND purpose IS NULL) OR "
            "(source = 'vocabulary_quiz' AND topic_id IS NOT NULL AND activity_id IS NULL "
            "AND grammar_lesson_id IS NULL AND purpose IS NULL) OR "
            "(source = 'grammar' AND grammar_lesson_id IS NOT NULL AND activity_id IS NULL "
            "AND topic_id IS NULL AND purpose IS NOT NULL)",
            name="source_owner_match",
        ),
        CheckConstraint("jsonb_typeof(payload) = 'object'", name="payload_object"),
        Index("ix_questions_activity_id", "activity_id"),
        Index("ix_questions_topic_id", "topic_id"),
        Index("ix_questions_grammar_lesson_id", "grammar_lesson_id"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    type: Mapped[str] = mapped_column(String(20), nullable=False)
    prompt: Mapped[str] = mapped_column(Text, nullable=False)
    explanation: Mapped[str | None] = mapped_column(Text, nullable=True, default=None)
    image_url: Mapped[str | None] = mapped_column(Text, nullable=True, default=None)
    audio_url: Mapped[str | None] = mapped_column(Text, nullable=True, default=None)
    payload: Mapped[dict[str, Any]] = mapped_column(JSONB(none_as_null=True), nullable=False)
    source: Mapped[str] = mapped_column(String(20), nullable=False)
    purpose: Mapped[str | None] = mapped_column(String(20), nullable=True, default=None)
    activity_id: Mapped[uuid.UUID | None] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("activities.id", ondelete="RESTRICT", name="fk_questions_activity_id_activities"),
        nullable=True,
        default=None,
    )
    topic_id: Mapped[uuid.UUID | None] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("topics.id", ondelete="RESTRICT", name="fk_questions_topic_id_topics"),
        nullable=True,
        default=None,
    )
    # No grammar_lessons table exists yet. Its migration must add the FK + relationship.
    grammar_lesson_id: Mapped[uuid.UUID | None] = mapped_column(
        Uuid(as_uuid=True), nullable=True, default=None,
    )

    activity: Mapped["Activity | None"] = relationship(back_populates="questions")
    topic: Mapped["Topic | None"] = relationship(back_populates="questions")

    def __repr__(self) -> str:
        return f"<Question(id={self.id}, type='{self.type}', source='{self.source}')>"
