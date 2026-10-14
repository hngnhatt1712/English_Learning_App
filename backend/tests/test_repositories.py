"""Tests for LevelRepository and UserRepository."""
import uuid

import pytest
from sqlalchemy import create_engine, event
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.database.base import Base
from app.models.level import Level
from app.models.user import User
from app.repositories.level_repository import LevelRepository
from app.repositories.user_repository import UserRepository


@pytest.fixture
def db_session():
    """Tạo in-memory SQLite database an toàn và cô lập cho mỗi test."""
    engine = create_engine("sqlite:///:memory:")

    @event.listens_for(engine, "connect")
    def set_sqlite_pragma(dbapi_connection, connection_record):
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()

    Base.metadata.create_all(engine)
    session = Session(engine)
    try:
        yield session
    finally:
        session.close()
        Base.metadata.drop_all(engine)
        engine.dispose()


@pytest.fixture
def level_repo(db_session: Session) -> LevelRepository:
    return LevelRepository(db_session)


@pytest.fixture
def user_repo(db_session: Session) -> UserRepository:
    return UserRepository(db_session)


# ============================================================================
# LevelRepository Tests
# ============================================================================

def test_level_get_all_empty(level_repo: LevelRepository):
    """get_all() trả về sequence rỗng khi chưa có dữ liệu."""
    levels = level_repo.get_all()
    assert list(levels) == []


def test_level_get_all_ordered(db_session: Session, level_repo: LevelRepository):
    """get_all() trả về toàn bộ level và sắp xếp tăng dần theo order_index."""
    l3 = Level(name="Intermediate", order_index=3)
    l1 = Level(name="Beginner", order_index=1)
    l2 = Level(name="Elementary", order_index=2)
    db_session.add_all([l3, l1, l2])
    db_session.flush()

    levels = list(level_repo.get_all())
    assert len(levels) == 3
    assert [lv.order_index for lv in levels] == [1, 2, 3]
    assert [lv.name for lv in levels] == ["Beginner", "Elementary", "Intermediate"]


def test_level_get_by_id_found(db_session: Session, level_repo: LevelRepository):
    """get_by_id() trả về đúng đối tượng Level theo UUID."""
    level = Level(name="Beginner", order_index=1, description="Dành cho người mới")
    db_session.add(level)
    db_session.flush()

    found = level_repo.get_by_id(level.id)
    assert found is not None
    assert found.id == level.id
    assert found.name == "Beginner"


def test_level_get_by_id_not_found(level_repo: LevelRepository):
    """get_by_id() trả về None khi không tồn tại ID."""
    found = level_repo.get_by_id(uuid.uuid4())
    assert found is None


# ============================================================================
# UserRepository Tests
# ============================================================================

def test_user_create_success(user_repo: UserRepository):
    """create_user() tạo user thành công, chuẩn hóa email/username và sinh giá trị mặc định."""
    user = user_repo.create_user(
        username="  Alice_01  ",
        email="  Alice@Example.COM  ",
        password_hash="hashed_pw_123",
    )
    assert user.id is not None
    assert isinstance(user.id, uuid.UUID)
    assert user.username == "alice_01"
    assert user.email == "alice@example.com"
    assert user.password_hash == "hashed_pw_123"
    assert user.current_streak == 0
    assert user.selected_level_id is None
    assert user.created_at is not None


def test_user_create_duplicate_email(db_session: Session, user_repo: UserRepository):
    """create_user() ném IntegrityError khi trùng email và không nuốt lỗi."""
    user_repo.create_user(
        username="user1",
        email="common@example.com",
        password_hash="hash1",
    )
    with pytest.raises(IntegrityError):
        user_repo.create_user(
            username="user2",
            email="common@example.com",
            password_hash="hash2",
        )
    db_session.rollback()


def test_user_create_duplicate_username(db_session: Session, user_repo: UserRepository):
    """create_user() ném IntegrityError khi trùng username và không nuốt lỗi."""
    user_repo.create_user(
        username="duplicate_user",
        email="user1@example.com",
        password_hash="hash1",
    )
    with pytest.raises(IntegrityError):
        user_repo.create_user(
            username="duplicate_user",
            email="user2@example.com",
            password_hash="hash2",
        )
    db_session.rollback()


def test_user_get_by_email_exists_and_case_insensitive(user_repo: UserRepository):
    """get_by_email() tìm thấy user không phân biệt hoa thường và khoảng trắng."""
    user = user_repo.create_user(
        username="bob",
        email="bob@example.com",
        password_hash="hash",
    )
    found = user_repo.get_by_email("  BOB@EXAMPLE.COM  ")
    assert found is not None
    assert found.id == user.id


def test_user_get_by_email_not_found(user_repo: UserRepository):
    """get_by_email() trả về None khi không tồn tại hoặc email rỗng."""
    assert user_repo.get_by_email("unknown@example.com") is None
    assert user_repo.get_by_email("   ") is None


def test_user_get_by_username_exists_and_case_insensitive(user_repo: UserRepository):
    """get_by_username() tìm thấy user không phân biệt hoa thường và khoảng trắng."""
    user = user_repo.create_user(
        username="charlie",
        email="charlie@example.com",
        password_hash="hash",
    )
    found = user_repo.get_by_username("  CHARLIE  ")
    assert found is not None
    assert found.id == user.id


def test_user_get_by_username_not_found(user_repo: UserRepository):
    """get_by_username() trả về None khi không tồn tại hoặc username rỗng."""
    assert user_repo.get_by_username("nonexistent") is None
    assert user_repo.get_by_username("   ") is None


def test_user_get_by_identifier_by_email(user_repo: UserRepository):
    """get_by_identifier() tìm đúng user khi identifier là email."""
    user = user_repo.create_user(
        username="david",
        email="david@example.com",
        password_hash="hash",
    )
    found = user_repo.get_by_identifier("  David@Example.Com  ")
    assert found is not None
    assert found.id == user.id


def test_user_get_by_identifier_by_username(user_repo: UserRepository):
    """get_by_identifier() tìm đúng user khi identifier là username."""
    user = user_repo.create_user(
        username="emma",
        email="emma@example.com",
        password_hash="hash",
    )
    found = user_repo.get_by_identifier("  EMMA  ")
    assert found is not None
    assert found.id == user.id


def test_user_get_by_identifier_blank_or_not_found(user_repo: UserRepository):
    """get_by_identifier() trả về None khi rỗng hoặc không tồn tại."""
    assert user_repo.get_by_identifier("") is None
    assert user_repo.get_by_identifier("   ") is None
    assert user_repo.get_by_identifier("nonexistent@domain.com") is None
    assert user_repo.get_by_identifier("nonexistent_user") is None


def test_user_get_by_identifier_priority_order(user_repo: UserRepository):
    """get_by_identifier() ưu tiên email khi có '@', ưu tiên username khi không có '@'."""
    # Tạo user1 có email là 'user2_tag@domain.com'
    user1 = user_repo.create_user(
        username="user1",
        email="overlap@domain.com",
        password_hash="hash1",
    )
    # Tạo user2 có username là 'overlap@domain.com' (nếu xảy ra)
    user2 = user_repo.create_user(
        username="overlap",
        email="user2@domain.com",
        password_hash="hash2",
    )
    # Khi identifier là 'overlap@domain.com' (có '@') -> phải khớp user1 qua email
    found_email = user_repo.get_by_identifier("overlap@domain.com")
    assert found_email is not None
    assert found_email.id == user1.id

    # Khi identifier là 'overlap' (không có '@') -> phải khớp user2 qua username
    found_username = user_repo.get_by_identifier("overlap")
    assert found_username is not None
    assert found_username.id == user2.id


def test_user_get_by_id_found(user_repo: UserRepository):
    """get_by_id() trả về đúng user khi tìm theo UUID."""
    user = user_repo.create_user(
        username="frank",
        email="frank@example.com",
        password_hash="hash",
    )
    found = user_repo.get_by_id(user.id)
    assert found is not None
    assert found.id == user.id


def test_user_get_by_id_not_found(user_repo: UserRepository):
    """get_by_id() trả về None khi UUID không tồn tại."""
    assert user_repo.get_by_id(uuid.uuid4()) is None


def test_user_update_selected_level_success(
    db_session: Session,
    user_repo: UserRepository,
    level_repo: LevelRepository,
):
    """update_selected_level() cập nhật selected_level_id thành công."""
    level = Level(name="Beginner", order_index=1)
    db_session.add(level)
    db_session.flush()

    user = user_repo.create_user(
        username="grace",
        email="grace@example.com",
        password_hash="hash",
    )
    assert user.selected_level_id is None

    updated = user_repo.update_selected_level(user.id, level.id)
    assert updated is not None
    assert updated.selected_level_id == level.id

    # Cho phép update về None (bỏ chọn level)
    cleared = user_repo.update_selected_level(user.id, None)
    assert cleared is not None
    assert cleared.selected_level_id is None


def test_user_update_selected_level_user_not_found(
    db_session: Session,
    user_repo: UserRepository,
):
    """update_selected_level() trả về None khi user_id không tồn tại."""
    assert user_repo.update_selected_level(uuid.uuid4(), uuid.uuid4()) is None


def test_user_update_selected_level_invalid_fk(
    db_session: Session,
    user_repo: UserRepository,
):
    """update_selected_level() ném IntegrityError khi level_id không tồn tại (FK violation)."""
    user = user_repo.create_user(
        username="helen",
        email="helen@example.com",
        password_hash="hash",
    )
    with pytest.raises(IntegrityError):
        user_repo.update_selected_level(user.id, uuid.uuid4())
    db_session.rollback()
