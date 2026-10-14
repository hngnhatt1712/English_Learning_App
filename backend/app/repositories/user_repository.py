"""Repository truy cập dữ liệu bảng users."""
from typing import Optional
import uuid

from sqlalchemy import case, func, or_, select
from sqlalchemy.orm import Session

from app.models.user import User


class UserRepository:
    """Class repository quản lý các thao tác truy vấn và lưu trữ thực thể User."""

    def __init__(self, db: Session) -> None:
        self.db = db
        self.session: Session = db

    def create_user(
        self,
        username: str,
        email: str,
        password_hash: str,
        selected_level_id: uuid.UUID | None = None,
    ) -> User:
        """Tạo mới một User.

        Mật khẩu nhận vào là hash đã tính sẵn (repository không tự hash).
        Email và username được chuẩn hóa bằng cách strip() và lower().
        Sử dụng db.flush() và db.refresh(user) để sinh các giá trị mặc định.
        Không commit trong repository; IntegrityError (nếu trùng email/username)
        sẽ nổi lên cho tầng service xử lý.
        """
        user = User(
            username=username.strip().lower(),
            email=email.strip().lower(),
            password_hash=password_hash,
            selected_level_id=selected_level_id,
        )
        self.db.add(user)
        self.db.flush()
        self.db.refresh(user)
        return user

    def get_by_email(self, email: str) -> Optional[User]:
        """Tìm User theo email (chuẩn hóa strip().lower(), so khớp không phân biệt hoa thường).

        Trả về None nếu email rỗng hoặc không tìm thấy bản ghi.
        """
        normalized_email = email.strip().lower()
        if not normalized_email:
            return None

        stmt = select(User).where(func.lower(User.email) == normalized_email)
        return self.db.scalars(stmt).first()

    def get_by_username(self, username: str) -> Optional[User]:
        """Tìm User theo username (chuẩn hóa strip().lower(), so khớp không phân biệt hoa thường).

        Trả về None nếu username rỗng hoặc không tìm thấy bản ghi.
        """
        normalized_username = username.strip().lower()
        if not normalized_username:
            return None

        stmt = select(User).where(func.lower(User.username) == normalized_username)
        return self.db.scalars(stmt).first()

    def get_by_identifier(self, identifier: str) -> Optional[User]:
        """Tìm User theo identifier (dùng cho đăng nhập bằng email hoặc username).

        Chuẩn hóa đầu vào bằng strip(). Nếu rỗng thì trả về None mà không query DB.
        Ưu tiên email nếu identifier chứa '@', ngược lại ưu tiên username.
        Thứ tự ưu tiên được triển khai tường minh bằng biểu thức CASE trong ORDER BY.
        Sử dụng limit(1) và first() để tránh phát sinh lỗi MultipleResultsFound.
        """
        cleaned_identifier = identifier.strip()
        if not cleaned_identifier:
            return None

        normalized_identifier = cleaned_identifier.lower()

        # Định nghĩa độ ưu tiên rõ ràng: giá trị 0 là ưu tiên cao nhất, 1 là dự phòng
        if "@" in normalized_identifier:
            priority = case((func.lower(User.email) == normalized_identifier, 0), else_=1)
        else:
            priority = case((func.lower(User.username) == normalized_identifier, 0), else_=1)

        stmt = (
            select(User)
            .where(
                or_(
                    func.lower(User.email) == normalized_identifier,
                    func.lower(User.username) == normalized_identifier,
                )
            )
            .order_by(priority)
            .limit(1)
        )
        return self.db.scalars(stmt).first()

    def get_by_id(self, user_id: uuid.UUID) -> Optional[User]:
        """Tìm User theo khóa chính (UUID).

        Trả về None nếu không tìm thấy.
        """
        stmt = select(User).where(User.id == user_id)
        return self.db.scalars(stmt).one_or_none()

    def update_selected_level(
        self,
        user_id: uuid.UUID,
        level_id: uuid.UUID | None,
    ) -> Optional[User]:
        """Cập nhật selected_level_id cho User.

        Lấy user theo user_id; nếu không tồn tại trả về None (không raise).
        Gán user.selected_level_id = level_id, sau đó flush() và refresh(user).
        Không kiểm tra level tồn tại trong repository (việc này thuộc tầng service);
        nếu level_id không hợp lệ, lỗi Foreign Key IntegrityError sẽ nổi lên từ DB.
        """
        user = self.get_by_id(user_id)
        if user is None:
            return None

        user.selected_level_id = level_id
        self.db.flush()
        self.db.refresh(user)
        return user
