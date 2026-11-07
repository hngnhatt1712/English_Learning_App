"""Lesson model: lessons belonging to a topic (contract D11, 6.5)."""
import uuid

from sqlalchemy import (
    CheckConstraint,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
    Uuid,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base


class Lesson(Base):
    """Content table: each topic has one lesson in MVP, populated by the seed script."""

    __tablename__ = "lessons"
    __table_args__ = (
        UniqueConstraint("topic_id", "order_index", name="uq_lessons_topic_id_order_index"),
        CheckConstraint("order_index >= 1", name="order_index_positive"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    topic_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("topics.id", ondelete="RESTRICT", name="fk_lessons_topic_id_topics"),
        nullable=False,
    )
    title: Mapped[str] = mapped_column(String(100), nullable=False)
    description: Mapped[str | None] = mapped_column(Text, nullable=True, default=None)
    order_index: Mapped[int] = mapped_column(Integer, nullable=False)

    def __repr__(self) -> str:
        return f"<Lesson(id={self.id}, title='{self.title}', order_index={self.order_index})>"
