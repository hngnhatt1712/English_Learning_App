"""SQLAlchemy model for password_reset_requests (contract 7.2, 16)."""
import uuid
from datetime import datetime, timezone

from app.database.base import Base
from sqlalchemy import DateTime, ForeignKey, Index, Integer, String, Uuid, func, text
from sqlalchemy.orm import Mapped, mapped_column


class PasswordResetRequest(Base):
    """Stores OTP and opaque one-time reset token hashes for the forgot-password flow."""

    __tablename__ = "password_reset_requests"
    __table_args__ = (
        Index("ix_password_reset_requests_user_id_created_at", "user_id", "created_at"),
        Index("ix_password_reset_requests_reset_token_hash", "reset_token_hash"),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4
    )
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey(
            "users.id",
            ondelete="CASCADE",
            name="fk_password_reset_requests_user_id_users",
        ),
        nullable=False,
    )
    otp_hash: Mapped[str] = mapped_column(String(128), nullable=False)
    otp_expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    attempts: Mapped[int] = mapped_column(
        Integer, nullable=False, default=0, server_default=text("0")
    )
    reset_token_hash: Mapped[str | None] = mapped_column(
        String(128), nullable=True, default=None
    )
    reset_token_expires_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True, default=None
    )
    consumed_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), nullable=True, default=None
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(timezone.utc),
        server_default=func.now(),
    )

    def __repr__(self) -> str:
        return f"<PasswordResetRequest(id={self.id}, user_id={self.user_id})>"
