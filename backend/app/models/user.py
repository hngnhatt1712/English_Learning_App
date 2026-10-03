"""Model User đại diện cho người dùng của hệ thống."""
from datetime import date, datetime

from sqlalchemy import Date, DateTime, Integer, String, func, text
from sqlalchemy.orm import Mapped, mapped_column

from app.database.base import Base


class User(Base):
    """Bảng lưu thông tin tài khoản người dùng, tiến độ streak và level đã chọn."""

    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    username: Mapped[str] = mapped_column(String(50), unique=True, index=True, nullable=False)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True, nullable=False)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)

    # FK -> levels.id sẽ được thêm bằng migration riêng ngay sau khi bảng levels được tạo (Sprint 3)
    selected_level_id: Mapped[int | None] = mapped_column(Integer, nullable=True, default=None)

    current_streak: Mapped[int] = mapped_column(
        Integer, nullable=False, default=0, server_default=text("0")
    )
    last_learning_date: Mapped[date | None] = mapped_column(Date, nullable=True, default=None)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, server_default=func.now()
    )

    def __repr__(self) -> str:
        return f"<User(id={self.id}, username='{self.username}', email='{self.email}')>"
