"""Repository truy cập và quản lý dữ liệu bảng device_tokens."""
from __future__ import annotations

import uuid
from typing import Any

from app.models.device_token import DeviceToken
from sqlalchemy import delete
from sqlalchemy.orm import Session


class DeviceTokenRepository:
    """Quản lý các thao tác truy vấn và xóa thực thể DeviceToken."""

    def __init__(self, db: Session | Any) -> None:
        self.db = db
        self.session = db

    def delete_by_user_and_token(self, user_id: uuid.UUID, fcm_token: str) -> None:
        """Xóa token thiết bị theo cặp (user_id, fcm_token).

        Idempotent, không ném lỗi nếu không tìm thấy bản ghi.
        """
        stmt = delete(DeviceToken).where(
            DeviceToken.user_id == user_id,
            DeviceToken.fcm_token == fcm_token,
        )
        self.db.execute(stmt)
