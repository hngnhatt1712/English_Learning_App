"""Repository truy cập dữ liệu bảng levels."""
from collections.abc import Sequence
from typing import Optional
import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.level import Level


class LevelRepository:
    """Class repository quản lý các thao tác truy vấn thực thể Level."""

    def __init__(self, db: Session) -> None:
        self.db = db

    def get_all(self) -> Sequence[Level]:
        """Lấy toàn bộ danh sách Level, sắp xếp xác định tăng dần theo order_index.

        Trả về Sequence rỗng nếu chưa có dữ liệu (không raise).
        """
        stmt = select(Level).order_by(Level.order_index.asc())
        return self.db.scalars(stmt).all()

    def get_by_id(self, level_id: uuid.UUID) -> Optional[Level]:
        """Tìm Level theo khóa chính (UUID).

        Trả về None nếu không tìm thấy.
        """
        stmt = select(Level).where(Level.id == level_id)
        return self.db.scalars(stmt).one_or_none()
