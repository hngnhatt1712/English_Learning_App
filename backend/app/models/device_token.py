"""Model DeviceToken đại diện cho token thiết bị FCM của người dùng."""
import uuid
from datetime import datetime

from app.database.base import Base
from sqlalchemy import DateTime, ForeignKey, String, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column


class DeviceToken(Base):
    """Bảng lưu trữ device token (FCM) phục vụ gửi thông báo đẩy."""

    __tablename__ = "device_tokens"

    id: Mapped[uuid.UUID] = mapped_column(Uuid(as_uuid=True), primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE", name="fk_device_tokens_user_id_users"),
        nullable=False,
        index=True,
    )
    fcm_token: Mapped[str] = mapped_column(String(4096), unique=True, index=True, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    def __repr__(self) -> str:
        return f"<DeviceToken(id={self.id}, user_id={self.user_id})>"
