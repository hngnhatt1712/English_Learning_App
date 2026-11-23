"""Shared vocabulary content (contract 6.1, 6.7); user state belongs to progress."""
import uuid
from typing import TYPE_CHECKING

from app.database.base import Base
from sqlalchemy import (
    CheckConstraint,
    ForeignKey,
    ForeignKeyConstraint,
    Index,
    String,
    Text,
    Uuid,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

if TYPE_CHECKING:
    from app.models.level import Level
    from app.models.progress import UserVocabularyProgress
    from app.models.topic import Topic

PART_OF_SPEECH_VALUES: tuple[str, ...] = (
    "noun", "verb", "adjective", "adverb", "pronoun", "preposition",
    "conjunction", "interjection", "phrase", "other",
)


class Vocabulary(Base):
    """Topic owns both association keys; Level is a read-only projection of that association."""

    __tablename__ = "vocabularies"
    __table_args__ = (
        CheckConstraint(
            "part_of_speech IN ('noun', 'verb', 'adjective', 'adverb', 'pronoun', "
            "'preposition', 'conjunction', 'interjection', 'phrase', 'other')",
            name="part_of_speech_valid",
        ),
        ForeignKeyConstraint(
            ["topic_id", "level_id"], ["topics.id", "topics.level_id"],
            name="fk_vocabularies_topic_id_level_id_topics", ondelete="RESTRICT",
        ),
        Index("ix_vocabularies_topic_id_level_id", "topic_id", "level_id"),
        Index("ix_vocabularies_level_id", "level_id"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    word: Mapped[str] = mapped_column(Text, nullable=False)
    meaning: Mapped[str] = mapped_column(Text, nullable=False)
    part_of_speech: Mapped[str] = mapped_column(String(20), nullable=False)
    phonetic: Mapped[str] = mapped_column(Text, nullable=False)
    example: Mapped[str | None] = mapped_column(Text, nullable=True, default=None)
    image_url: Mapped[str | None] = mapped_column(Text, nullable=True, default=None)
    audio_url: Mapped[str] = mapped_column(Text, nullable=False)
    # No difficulty scale/type is specified yet. Preserve an optional opaque label,
    # with no default or domain CHECK; it is not a VocabularyDto field.
    difficulty: Mapped[str | None] = mapped_column(Text, nullable=True, default=None)
    topic_id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), nullable=False)
    level_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("levels.id", ondelete="RESTRICT", name="fk_vocabularies_level_id_levels"),
        nullable=False,
    )

    topic: Mapped["Topic"] = relationship(back_populates="vocabularies")
    # Topic synchronizes both topic_id and level_id. A second writable association
    # to Level would compete for level_id and could leave the two keys inconsistent.
    level: Mapped["Level"] = relationship(back_populates="vocabularies", viewonly=True)
    # The DB restricts deleting content with progress, even for loaded children.
    vocabulary_progress: Mapped[list["UserVocabularyProgress"]] = relationship(
        back_populates="vocabulary", passive_deletes="all",
    )

    def __repr__(self) -> str:
        return f"<Vocabulary(id={self.id}, word={self.word!r}, part_of_speech={self.part_of_speech!r})>"
