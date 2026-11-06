"""Topic model: learning topics of a level (contract D11, 6.4, C4)."""
import uuid

from sqlalchemy import (
    CheckConstraint,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
    Uuid,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base

DEFAULT_THEME_KEY = "default"


class Topic(Base):
    """Content table: each level has three topics, populated by the seed script."""

    __tablename__ = "topics"
    __table_args__ = (
        UniqueConstraint("level_id", "order_index", name="uq_topics_level_id_order_index"),
        CheckConstraint("order_index >= 1", name="order_index_positive"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    level_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("levels.id", ondelete="RESTRICT", name="fk_topics_level_id_levels"),
        nullable=False,
    )
    name: Mapped[str] = mapped_column(String(100), nullable=False)
    description: Mapped[str | None] = mapped_column(Text, nullable=True, default=None)
    order_index: Mapped[int] = mapped_column(Integer, nullable=False)
    theme_key: Mapped[str] = mapped_column(
        String(50), nullable=False, default=DEFAULT_THEME_KEY, server_default=text("'default'")
    )

    def __repr__(self) -> str:
        return f"<Topic(id={self.id}, name='{self.name}', order_index={self.order_index})>"
