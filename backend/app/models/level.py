"""Level model: learning levels (Beginner / Elementary / Intermediate)."""
import uuid
from typing import TYPE_CHECKING

from sqlalchemy import Integer, String, Text, UniqueConstraint, Uuid
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database.base import Base

if TYPE_CHECKING:
    from app.models.vocabulary import Vocabulary


class Level(Base):
    """Content table holding the three fixed levels, populated by the seed script."""

    __tablename__ = "levels"
    __table_args__ = (UniqueConstraint("order_index", name="uq_levels_order_index"),)

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name: Mapped[str] = mapped_column(String(100), nullable=False)
    description: Mapped[str | None] = mapped_column(Text, nullable=True, default=None)
    order_index: Mapped[int] = mapped_column(Integer, nullable=False)

    vocabularies: Mapped[list["Vocabulary"]] = relationship(
        back_populates="level", viewonly=True,
    )

    def __repr__(self) -> str:
        return f"<Level(id={self.id}, name='{self.name}', order_index={self.order_index})>"
