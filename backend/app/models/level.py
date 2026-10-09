"""Level model: learning levels (Beginner / Elementary / Intermediate)."""
import uuid

from sqlalchemy import Integer, String, Text, UniqueConstraint, Uuid
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base


class Level(Base):
    """Content table holding the three fixed levels, populated by the seed script."""

    __tablename__ = "levels"
    __table_args__ = (UniqueConstraint("order_index", name="uq_levels_order_index"),)

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name: Mapped[str] = mapped_column(String(100), nullable=False)
    description: Mapped[str | None] = mapped_column(Text, nullable=True, default=None)
    order_index: Mapped[int] = mapped_column(Integer, nullable=False)

    def __repr__(self) -> str:
        return f"<Level(id={self.id}, name='{self.name}', order_index={self.order_index})>"
