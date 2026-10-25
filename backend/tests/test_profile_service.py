"""Unit tests for profile_service (contract 7.3, D14).

All tests are in-memory with fake repositories and fake sessions (no DB required).
"""
from __future__ import annotations

import logging
import uuid
from collections.abc import Callable, Sequence
from datetime import date, datetime, timezone
from typing import Any

import pytest
from sqlalchemy.exc import IntegrityError

from app.core.errors import AppError, ErrorType
from app.schemas.profile import ProfileUpdateRequest
from app.services import profile_service
from app.services.level_service import MAX_STARS_PER_LEVEL, MAX_STARS_TOTAL
from app.services.profile_service import get_profile, update_profile

FIXED_NOW = datetime(2026, 10, 13, 5, 0, tzinfo=timezone.utc)


# ---------------------------------------------------------------------------
# Test Fakes (in-file only, isolated per test)
# ---------------------------------------------------------------------------

class FakeSession:
    """Minimal SQLAlchemy session double for unit tests."""

    def __init__(
        self,
        *,
        force_integrity_error_on_flush: bool = False,
        force_integrity_error_on_commit: bool = False,
        force_generic_error_on_commit: Exception | None = None,
        on_flush_error_hook: Callable[[], None] | None = None,
        on_commit_error_hook: Callable[[], None] | None = None,
        on_rollback_hook: Callable[[], None] | None = None,
    ) -> None:
        self.commit_count: int = 0
        self.rollback_count: int = 0
        self.flush_count: int = 0
        self.force_integrity_error_on_flush: bool = force_integrity_error_on_flush
        self.force_integrity_error_on_commit: bool = force_integrity_error_on_commit
        self.force_generic_error_on_commit: Exception | None = force_generic_error_on_commit
        self.on_flush_error_hook: Callable[[], None] | None = on_flush_error_hook
        self.on_commit_error_hook: Callable[[], None] | None = on_commit_error_hook
        self.on_rollback_hook: Callable[[], None] | None = on_rollback_hook

    def flush(self) -> None:
        if self.force_integrity_error_on_flush:
            if self.on_flush_error_hook is not None:
                self.on_flush_error_hook()
            raise IntegrityError(statement="FLUSH ...", params={}, orig=Exception("unique violation on flush"))
        self.flush_count += 1

    def commit(self) -> None:
        if self.force_generic_error_on_commit is not None:
            raise self.force_generic_error_on_commit
        if self.force_integrity_error_on_commit:
            if self.on_commit_error_hook is not None:
                self.on_commit_error_hook()
            raise IntegrityError(statement="COMMIT ...", params={}, orig=Exception("unique violation on commit"))
        self.commit_count += 1

    def rollback(self) -> None:
        self.rollback_count += 1
        if self.on_rollback_hook is not None:
            self.on_rollback_hook()


class FakeUser:
    """Substitute for User ORM object."""

    def __init__(
        self,
        *,
        username: str,
        email: str,
        password_hash: str = "argon2_dummy_hash",
        selected_level_id: uuid.UUID | None = None,
        current_streak: int = 0,
        last_learning_date: date | None = None,
        user_id: uuid.UUID | None = None,
        created_at: datetime | None = None,
        password_changed_at: datetime | None = None,
    ) -> None:
        self.id: uuid.UUID = user_id or uuid.uuid4()
        self.username: str = username
        self.email: str = email
        self.password_hash: str = password_hash
        self.selected_level_id: uuid.UUID | None = selected_level_id
        self.current_streak: int = current_streak
        self.last_learning_date: date | None = last_learning_date
        self.created_at: datetime = created_at or FIXED_NOW
        self.password_changed_at: datetime | None = password_changed_at


class FakeLevel:
    """Substitute for Level ORM object."""

    def __init__(self, *, level_id: uuid.UUID, name: str, order_index: int) -> None:
        self.id: uuid.UUID = level_id
        self.name: str = name
        self.order_index: int = order_index


class FakeUserRepository:
    """In-memory UserRepository double with same signatures as real repo."""

    def __init__(
        self,
        users: list[FakeUser] | None = None,
        session: FakeSession | None = None,
        *,
        force_integrity_error_on_update_selected_level: bool = False,
    ) -> None:
        self.users: list[FakeUser] = list(users or [])
        self.session: FakeSession | None = session
        self.force_integrity_error_on_update_selected_level: bool = (
            force_integrity_error_on_update_selected_level
        )
        self.update_selected_level_calls: list[tuple[uuid.UUID, uuid.UUID | None]] = []

    def get_by_username(self, username: str) -> FakeUser | None:
        norm = username.strip().lower()
        if not norm:
            return None
        for u in self.users:
            if u.username.lower() == norm:
                return u
        return None

    def get_by_id(self, user_id: uuid.UUID) -> FakeUser | None:
        for u in self.users:
            if u.id == user_id:
                return u
        return None

    def update_selected_level(
        self,
        user_id: uuid.UUID,
        level_id: uuid.UUID | None,
    ) -> FakeUser | None:
        self.update_selected_level_calls.append((user_id, level_id))
        user = self.get_by_id(user_id)
        if user is None:
            return None
        user.selected_level_id = level_id
        if self.force_integrity_error_on_update_selected_level:
            raise IntegrityError(
                statement="UPDATE users ...",
                params={},
                orig=Exception("foreign key violation"),
            )
        if self.session is not None:
            self.session.flush()
        return user


class FakeLevelRepository:
    """In-memory LevelRepository double."""

    def __init__(self, levels: list[FakeLevel] | None = None) -> None:
        self._levels: list[FakeLevel] = list(levels or [])

    def get_all(self) -> Sequence[FakeLevel]:
        # Return levels (can be shuffled order to verify service-level sorting)
        return list(self._levels)

    def get_by_id(self, level_id: uuid.UUID) -> FakeLevel | None:
        for lvl in self._levels:
            if lvl.id == level_id:
                return lvl
        return None


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def level_ids() -> tuple[uuid.UUID, uuid.UUID, uuid.UUID]:
    return (uuid.uuid4(), uuid.uuid4(), uuid.uuid4())


@pytest.fixture
def fake_levels(level_ids: tuple[uuid.UUID, uuid.UUID, uuid.UUID]) -> list[FakeLevel]:
    id1, id2, id3 = level_ids
    # Shuffled order: index 3, 1, 2
    return [
        FakeLevel(level_id=id3, name="Advanced", order_index=3),
        FakeLevel(level_id=id1, name="Beginner", order_index=1),
        FakeLevel(level_id=id2, name="Intermediate", order_index=2),
    ]


@pytest.fixture
def level_repo(fake_levels: list[FakeLevel]) -> FakeLevelRepository:
    return FakeLevelRepository(fake_levels)


@pytest.fixture
def user_a() -> FakeUser:
    return FakeUser(
        username="user_a",
        email="usera@example.com",
        current_streak=3,
        last_learning_date=date(2026, 10, 13),
    )


@pytest.fixture
def user_b() -> FakeUser:
    return FakeUser(
        username="user_b",
        email="userb@example.com",
        current_streak=0,
        last_learning_date=None,
    )


@pytest.fixture
def session() -> FakeSession:
    return FakeSession()


@pytest.fixture
def user_repo(user_a: FakeUser, user_b: FakeUser, session: FakeSession) -> FakeUserRepository:
    return FakeUserRepository(users=[user_a, user_b], session=session)


# ---------------------------------------------------------------------------
# GET Profile Tests
# ---------------------------------------------------------------------------

def test_get_profile_maps_core_fields(
    session: FakeSession,
    user_a: FakeUser,
    level_repo: FakeLevelRepository,
) -> None:
    profile = get_profile(session, user_a, level_repo=level_repo, now=FIXED_NOW)
    assert profile.id == user_a.id
    assert profile.username == user_a.username
    assert profile.email == user_a.email
    assert profile.created_at == user_a.created_at


def test_get_profile_without_level_returns_null_level(
    session: FakeSession,
    user_a: FakeUser,
    level_repo: FakeLevelRepository,
) -> None:
    user_a.selected_level_id = None
    profile = get_profile(session, user_a, level_repo=level_repo, now=FIXED_NOW)
    assert profile.level is None


def test_get_profile_with_selected_level_builds_brief(
    session: FakeSession,
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    level_repo: FakeLevelRepository,
) -> None:
    # Selected level is Beginner (order_index=1)
    lvl = fake_levels[1]
    user_a.selected_level_id = lvl.id

    profile = get_profile(session, user_a, level_repo=level_repo, now=FIXED_NOW)
    assert profile.level is not None
    assert profile.level.id == lvl.id
    assert profile.level.name == lvl.name
    # Sprint 3 placeholders (updated in Sprint 4)
    assert profile.level.stars == 0
    assert profile.level.max_stars == MAX_STARS_PER_LEVEL
    assert profile.level.progress_percent == 0.0


def test_get_profile_total_and_max_stars(
    session: FakeSession,
    user_a: FakeUser,
    level_repo: FakeLevelRepository,
) -> None:
    profile = get_profile(session, user_a, level_repo=level_repo, now=FIXED_NOW)
    assert profile.total_stars == 0
    assert profile.max_stars == MAX_STARS_TOTAL  # 135


def test_get_profile_streak_is_adjusted_on_read(
    session: FakeSession,
    user_a: FakeUser,
    level_repo: FakeLevelRepository,
) -> None:
    # Today: 2026-10-13 (in VN timezone)
    user_a.current_streak = 5

    # Case 1: last_learning_date is today -> keep 5
    user_a.last_learning_date = date(2026, 10, 13)
    p1 = get_profile(session, user_a, level_repo=level_repo, now=FIXED_NOW)
    assert p1.current_streak == 5

    # Case 2: last_learning_date is yesterday -> keep 5
    user_a.last_learning_date = date(2026, 10, 12)
    p2 = get_profile(session, user_a, level_repo=level_repo, now=FIXED_NOW)
    assert p2.current_streak == 5

    # Case 3: last_learning_date is 2 days ago -> adjusted to 0
    user_a.last_learning_date = date(2026, 10, 11)
    p3 = get_profile(session, user_a, level_repo=level_repo, now=FIXED_NOW)
    assert p3.current_streak == 0

    # Case 4: last_learning_date is None -> 0
    user_a.last_learning_date = None
    p4 = get_profile(session, user_a, level_repo=level_repo, now=FIXED_NOW)
    assert p4.current_streak == 0


def test_get_profile_dangling_selected_level_returns_null_level(
    session: FakeSession,
    user_a: FakeUser,
    level_repo: FakeLevelRepository,
) -> None:
    # Dangling UUID not present in level_repo
    user_a.selected_level_id = uuid.uuid4()
    profile = get_profile(session, user_a, level_repo=level_repo, now=FIXED_NOW)
    assert profile.level is None


def test_get_profile_is_read_only(
    session: FakeSession,
    user_a: FakeUser,
    level_repo: FakeLevelRepository,
) -> None:
    get_profile(session, user_a, level_repo=level_repo, now=FIXED_NOW)
    assert session.flush_count == 0
    assert session.commit_count == 0
    assert session.rollback_count == 0


def test_get_profile_dto_keys_are_exact(
    session: FakeSession,
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    level_repo: FakeLevelRepository,
) -> None:
    user_a.selected_level_id = fake_levels[1].id
    profile = get_profile(session, user_a, level_repo=level_repo, now=FIXED_NOW)
    dump = profile.model_dump(by_alias=True)

    expected_keys = {
        "id",
        "username",
        "email",
        "level",
        "totalStars",
        "maxStars",
        "currentStreak",
        "createdAt",
    }
    assert set(dump.keys()) == expected_keys

    level_dump = dump["level"]
    assert isinstance(level_dump, dict)
    expected_level_keys = {"id", "name", "stars", "maxStars", "progressPercent"}
    assert set(level_dump.keys()) == expected_level_keys

    # Confirm sensitive or forbidden fields are absent
    assert "password" not in dump
    assert "passwordHash" not in dump
    assert "selectedLevelId" not in dump


# ---------------------------------------------------------------------------
# UPDATE Profile Tests: Happy Path
# ---------------------------------------------------------------------------

def test_update_username_only(
    session: FakeSession,
    user_a: FakeUser,
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    original_level_id = user_a.selected_level_id
    payload = ProfileUpdateRequest.model_validate({"username": "new_username_a"})

    res = update_profile(
        session,
        user_a,
        payload,
        user_repo=user_repo,
        level_repo=level_repo,
        now=FIXED_NOW,
    )
    assert user_a.username == "new_username_a"
    assert user_a.selected_level_id == original_level_id
    assert len(user_repo.update_selected_level_calls) == 0
    assert res.username == "new_username_a"
    assert session.commit_count == 1
    assert session.rollback_count == 0


def test_update_level_only_first_time_allows_any_level(
    session: FakeSession,
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    # First time user with selected_level_id = None can choose any level (e.g. Level 3)
    user_a.selected_level_id = None
    level3 = next(lvl for lvl in fake_levels if lvl.order_index == 3)
    payload = ProfileUpdateRequest.model_validate({"selectedLevelId": str(level3.id)})

    res = update_profile(
        session,
        user_a,
        payload,
        user_repo=user_repo,
        level_repo=level_repo,
        now=FIXED_NOW,
    )
    assert user_a.selected_level_id == level3.id
    assert res.level is not None
    assert res.level.id == level3.id
    assert session.commit_count == 1
    assert session.rollback_count == 0


def test_update_both_fields(
    session: FakeSession,
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    user_a.selected_level_id = None
    level1 = next(lvl for lvl in fake_levels if lvl.order_index == 1)
    payload = ProfileUpdateRequest.model_validate(
        {"username": "user_a_updated", "selectedLevelId": str(level1.id)}
    )

    res = update_profile(
        session,
        user_a,
        payload,
        user_repo=user_repo,
        level_repo=level_repo,
        now=FIXED_NOW,
    )
    assert user_a.username == "user_a_updated"
    assert user_a.selected_level_id == level1.id
    assert res.username == "user_a_updated"
    assert res.level is not None
    assert res.level.id == level1.id
    assert session.commit_count == 1
    assert session.rollback_count == 0


def test_update_same_level_is_idempotent(
    session: FakeSession,
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    level2 = next(lvl for lvl in fake_levels if lvl.order_index == 2)
    user_a.selected_level_id = level2.id
    payload = ProfileUpdateRequest.model_validate({"selectedLevelId": str(level2.id)})

    res = update_profile(
        session,
        user_a,
        payload,
        user_repo=user_repo,
        level_repo=level_repo,
        now=FIXED_NOW,
    )
    assert user_a.selected_level_id == level2.id
    assert res.level is not None
    assert res.level.id == level2.id
    assert session.commit_count == 1


def test_update_switch_to_level1_after_level3(
    session: FakeSession,
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    level3 = next(lvl for lvl in fake_levels if lvl.order_index == 3)
    level1 = next(lvl for lvl in fake_levels if lvl.order_index == 1)
    user_a.selected_level_id = level3.id

    payload = ProfileUpdateRequest.model_validate({"selectedLevelId": str(level1.id)})
    res = update_profile(
        session,
        user_a,
        payload,
        user_repo=user_repo,
        level_repo=level_repo,
        now=FIXED_NOW,
    )
    assert user_a.selected_level_id == level1.id
    assert res.level is not None
    assert res.level.id == level1.id
    assert session.commit_count == 1


def test_update_back_to_level3_after_level1_is_locked(
    session: FakeSession,
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    # Contract D14/D22: user previously switched to Level 1 and has no completed/progress set yet.
    # Level 3 is now locked (will change in Sprint 7 when progress is tracked).
    level1 = next(lvl for lvl in fake_levels if lvl.order_index == 1)
    level3 = next(lvl for lvl in fake_levels if lvl.order_index == 3)
    user_a.selected_level_id = level1.id

    payload = ProfileUpdateRequest.model_validate({"selectedLevelId": str(level3.id)})
    with pytest.raises(AppError) as exc_info:
        update_profile(
            session,
            user_a,
            payload,
            user_repo=user_repo,
            level_repo=level_repo,
            now=FIXED_NOW,
        )
    assert exc_info.value.status_code == 403
    assert exc_info.value.type == ErrorType.LEVEL_LOCKED.value


def test_update_normalizes_username_to_lowercase(
    session: FakeSession,
    user_a: FakeUser,
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    payload = ProfileUpdateRequest.model_validate({"username": "New_Name"})
    res = update_profile(
        session,
        user_a,
        payload,
        user_repo=user_repo,
        level_repo=level_repo,
        now=FIXED_NOW,
    )
    assert user_a.username == "new_name"
    assert res.username == "new_name"


def test_update_own_username_is_allowed(
    session: FakeSession,
    user_a: FakeUser,
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    user_a.username = "my_username"
    # Case variation of own username
    payload = ProfileUpdateRequest.model_validate({"username": "MY_USERNAME"})

    res = update_profile(
        session,
        user_a,
        payload,
        user_repo=user_repo,
        level_repo=level_repo,
        now=FIXED_NOW,
    )
    assert user_a.username == "my_username"
    assert res.username == "my_username"
    assert session.commit_count == 1
    assert session.rollback_count == 0


def test_update_commits_exactly_once(
    session: FakeSession,
    user_a: FakeUser,
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    payload = ProfileUpdateRequest.model_validate({"username": "user_a_once"})
    update_profile(
        session,
        user_a,
        payload,
        user_repo=user_repo,
        level_repo=level_repo,
        now=FIXED_NOW,
    )
    assert session.commit_count == 1
    assert session.rollback_count == 0


def test_update_returns_profile_with_new_values(
    session: FakeSession,
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    level1 = next(lvl for lvl in fake_levels if lvl.order_index == 1)
    payload = ProfileUpdateRequest.model_validate(
        {"username": "updated_name", "selectedLevelId": str(level1.id)}
    )
    res = update_profile(
        session,
        user_a,
        payload,
        user_repo=user_repo,
        level_repo=level_repo,
        now=FIXED_NOW,
    )
    assert res.username == "updated_name"
    assert res.level is not None
    assert res.level.id == level1.id


def test_update_does_not_touch_other_fields(
    session: FakeSession,
    user_a: FakeUser,
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    orig_id = user_a.id
    orig_email = user_a.email
    orig_streak = user_a.current_streak
    orig_last = user_a.last_learning_date
    orig_hash = user_a.password_hash
    orig_created = user_a.created_at

    payload = ProfileUpdateRequest.model_validate({"username": "user_a_only"})
    update_profile(
        session,
        user_a,
        payload,
        user_repo=user_repo,
        level_repo=level_repo,
        now=FIXED_NOW,
    )
    assert user_a.id == orig_id
    assert user_a.email == orig_email
    assert user_a.current_streak == orig_streak
    assert user_a.last_learning_date == orig_last
    assert user_a.password_hash == orig_hash
    assert user_a.created_at == orig_created


def test_update_respects_model_fields_set(
    session: FakeSession,
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    # 1. Payload with username only: selected_level_id untouched, update_selected_level not called
    orig_level_id = user_a.selected_level_id
    payload1 = ProfileUpdateRequest.model_validate({"username": "username_respect"})
    update_profile(
        session,
        user_a,
        payload1,
        user_repo=user_repo,
        level_repo=level_repo,
        now=FIXED_NOW,
    )
    assert user_a.selected_level_id == orig_level_id
    assert len(user_repo.update_selected_level_calls) == 0

    # 2. Payload with selectedLevelId only: username untouched
    level1 = next(lvl for lvl in fake_levels if lvl.order_index == 1)
    payload2 = ProfileUpdateRequest.model_validate({"selectedLevelId": str(level1.id)})
    update_profile(
        session,
        user_a,
        payload2,
        user_repo=user_repo,
        level_repo=level_repo,
        now=FIXED_NOW,
    )
    assert user_a.username == "username_respect"


def test_update_uses_is_level_unlocked_from_level_service(
    session: FakeSession,
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    level1 = next(lvl for lvl in fake_levels if lvl.order_index == 1)
    orig_selected = user_a.selected_level_id

    calls: list[tuple[Any, ...]] = []
    real_func = profile_service.is_level_unlocked

    def spy_is_level_unlocked(target: Any, levels: Any, selected: Any) -> bool:
        calls.append((target, levels, selected))
        return real_func(target, levels, selected)

    monkeypatch.setattr(profile_service, "is_level_unlocked", spy_is_level_unlocked)

    payload = ProfileUpdateRequest.model_validate({"selectedLevelId": str(level1.id)})
    update_profile(
        session,
        user_a,
        payload,
        user_repo=user_repo,
        level_repo=level_repo,
        now=FIXED_NOW,
    )
    assert len(calls) == 1
    assert calls[0][0] == level1.id
    assert calls[0][2] == orig_selected  # Called with current level id before update


# ---------------------------------------------------------------------------
# UPDATE Profile Tests: Errors & Edge Cases
# ---------------------------------------------------------------------------

def test_update_unknown_level_raises_404_not_found(
    session: FakeSession,
    user_a: FakeUser,
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    unknown_id = uuid.uuid4()
    payload = ProfileUpdateRequest.model_validate({"selectedLevelId": str(unknown_id)})

    with pytest.raises(AppError) as exc_info:
        update_profile(
            session,
            user_a,
            payload,
            user_repo=user_repo,
            level_repo=level_repo,
            now=FIXED_NOW,
        )
    assert exc_info.value.status_code == 404
    assert exc_info.value.type == ErrorType.NOT_FOUND.value


def test_update_locked_level_raises_403_level_locked(
    session: FakeSession,
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    # User currently has Level 3; Level 2 is locked
    level3 = next(lvl for lvl in fake_levels if lvl.order_index == 3)
    level2 = next(lvl for lvl in fake_levels if lvl.order_index == 2)
    user_a.selected_level_id = level3.id

    payload = ProfileUpdateRequest.model_validate({"selectedLevelId": str(level2.id)})
    with pytest.raises(AppError) as exc_info:
        update_profile(
            session,
            user_a,
            payload,
            user_repo=user_repo,
            level_repo=level_repo,
            now=FIXED_NOW,
        )
    assert exc_info.value.status_code == 403
    assert exc_info.value.type == ErrorType.LEVEL_LOCKED.value


def test_update_username_taken_raises_409(
    session: FakeSession,
    user_a: FakeUser,
    user_b: FakeUser,
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    # User B has "user_b"; User A tries to update to "USER_B"
    payload = ProfileUpdateRequest.model_validate({"username": "USER_B"})

    with pytest.raises(AppError) as exc_info:
        update_profile(
            session,
            user_a,
            payload,
            user_repo=user_repo,
            level_repo=level_repo,
            now=FIXED_NOW,
        )
    assert exc_info.value.status_code == 409
    assert exc_info.value.type == ErrorType.USERNAME_TAKEN.value


@pytest.mark.parametrize(
    "level_id_fn, username, expected_status, expected_type",
    [
        # Case 1: 404 + 403 + 409 (unknown level takes precedence over 409)
        (
            lambda levels: uuid.uuid4(),
            "user_b",
            404,
            ErrorType.NOT_FOUND.value,
        ),
        # Case 2: 403 + 409 (locked level takes precedence over 409)
        (
            lambda levels: next(lvl.id for lvl in levels if lvl.order_index == 2),
            "user_b",
            403,
            ErrorType.LEVEL_LOCKED.value,
        ),
    ],
)
def test_update_error_precedence(
    session: FakeSession,
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
    level_id_fn: Callable[[list[FakeLevel]], uuid.UUID],
    username: str,
    expected_status: int,
    expected_type: str,
) -> None:
    # Set user_a to Level 3 so Level 2 is locked
    level3 = next(lvl for lvl in fake_levels if lvl.order_index == 3)
    user_a.selected_level_id = level3.id

    target_id = level_id_fn(fake_levels)
    payload = ProfileUpdateRequest.model_validate(
        {"username": username, "selectedLevelId": str(target_id)}
    )

    with pytest.raises(AppError) as exc_info:
        update_profile(
            session,
            user_a,
            payload,
            user_repo=user_repo,
            level_repo=level_repo,
            now=FIXED_NOW,
        )
    assert exc_info.value.status_code == expected_status
    assert exc_info.value.type == expected_type


def test_update_atomic_when_username_taken(
    session: FakeSession,
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    orig_username = user_a.username
    orig_level = user_a.selected_level_id
    level1 = next(lvl for lvl in fake_levels if lvl.order_index == 1)

    payload = ProfileUpdateRequest.model_validate(
        {"username": "user_b", "selectedLevelId": str(level1.id)}
    )
    with pytest.raises(AppError) as exc_info:
        update_profile(
            session,
            user_a,
            payload,
            user_repo=user_repo,
            level_repo=level_repo,
            now=FIXED_NOW,
        )
    assert exc_info.value.status_code == 409
    assert user_a.username == orig_username
    assert user_a.selected_level_id == orig_level
    assert session.flush_count == 0
    assert session.commit_count == 0
    assert len(user_repo.update_selected_level_calls) == 0


def test_update_atomic_when_level_locked(
    session: FakeSession,
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    level3 = next(lvl for lvl in fake_levels if lvl.order_index == 3)
    level2 = next(lvl for lvl in fake_levels if lvl.order_index == 2)
    user_a.selected_level_id = level3.id
    orig_username = user_a.username

    payload = ProfileUpdateRequest.model_validate(
        {"username": "brand_new_name", "selectedLevelId": str(level2.id)}
    )
    with pytest.raises(AppError) as exc_info:
        update_profile(
            session,
            user_a,
            payload,
            user_repo=user_repo,
            level_repo=level_repo,
            now=FIXED_NOW,
        )
    assert exc_info.value.status_code == 403
    assert user_a.username == orig_username
    assert session.flush_count == 0
    assert session.commit_count == 0


def test_update_atomic_when_level_not_found(
    session: FakeSession,
    user_a: FakeUser,
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
) -> None:
    orig_username = user_a.username
    payload = ProfileUpdateRequest.model_validate(
        {"username": "brand_new_name", "selectedLevelId": str(uuid.uuid4())}
    )
    with pytest.raises(AppError) as exc_info:
        update_profile(
            session,
            user_a,
            payload,
            user_repo=user_repo,
            level_repo=level_repo,
            now=FIXED_NOW,
        )
    assert exc_info.value.status_code == 404
    assert user_a.username == orig_username
    assert session.flush_count == 0
    assert session.commit_count == 0


def test_update_integrity_error_on_username_flush_returns_409(
    user_a: FakeUser,
    user_b: FakeUser,
    level_repo: FakeLevelRepository,
) -> None:
    # Simulate concurrent registration: during check, 'concurrent_name' is free.
    # At session.flush(), IntegrityError is raised and 'concurrent_name' belongs to user_b.
    orig_a_username = user_a.username

    def on_flush() -> None:
        user_b.username = "concurrent_name"

    def on_rollback() -> None:
        user_a.username = orig_a_username

    session = FakeSession(
        force_integrity_error_on_flush=True,
        on_flush_error_hook=on_flush,
        on_rollback_hook=on_rollback,
    )
    repo = FakeUserRepository(users=[user_a, user_b], session=session)

    payload = ProfileUpdateRequest.model_validate({"username": "concurrent_name"})
    with pytest.raises(AppError) as exc_info:
        update_profile(
            session,
            user_a,
            payload,
            user_repo=repo,
            level_repo=level_repo,
            now=FIXED_NOW,
        )
    assert exc_info.value.status_code == 409
    assert exc_info.value.type == ErrorType.USERNAME_TAKEN.value
    assert session.rollback_count == 1
    assert session.commit_count == 0


def test_update_integrity_error_on_commit_returns_409(
    user_a: FakeUser,
    user_b: FakeUser,
    level_repo: FakeLevelRepository,
) -> None:
    # Simulate concurrent conflict detected at commit time
    orig_a_username = user_a.username

    def on_commit() -> None:
        user_b.username = "commit_conflict_name"

    def on_rollback() -> None:
        user_a.username = orig_a_username

    session = FakeSession(
        force_integrity_error_on_commit=True,
        on_commit_error_hook=on_commit,
        on_rollback_hook=on_rollback,
    )
    repo = FakeUserRepository(users=[user_a, user_b], session=session)

    payload = ProfileUpdateRequest.model_validate({"username": "commit_conflict_name"})
    with pytest.raises(AppError) as exc_info:
        update_profile(
            session,
            user_a,
            payload,
            user_repo=repo,
            level_repo=level_repo,
            now=FIXED_NOW,
        )
    assert exc_info.value.status_code == 409
    assert exc_info.value.type == ErrorType.USERNAME_TAKEN.value
    assert session.rollback_count == 1


def test_update_unknown_integrity_error_on_level_flush_is_reraised(
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    level_repo: FakeLevelRepository,
) -> None:
    # Simulates FK constraint failure inside update_selected_level
    session = FakeSession()
    repo = FakeUserRepository(
        users=[user_a],
        session=session,
        force_integrity_error_on_update_selected_level=True,
    )

    level1 = next(lvl for lvl in fake_levels if lvl.order_index == 1)
    payload = ProfileUpdateRequest.model_validate({"selectedLevelId": str(level1.id)})

    with pytest.raises(IntegrityError):
        update_profile(
            session,
            user_a,
            payload,
            user_repo=repo,
            level_repo=level_repo,
            now=FIXED_NOW,
        )
    assert session.rollback_count == 1


def test_update_unknown_integrity_error_on_commit_is_reraised(
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    level_repo: FakeLevelRepository,
) -> None:
    # IntegrityError at commit with no username conflict (e.g. unknown DB constraint)
    session = FakeSession(force_integrity_error_on_commit=True)
    repo = FakeUserRepository(users=[user_a], session=session)

    level1 = next(lvl for lvl in fake_levels if lvl.order_index == 1)
    payload = ProfileUpdateRequest.model_validate({"selectedLevelId": str(level1.id)})

    with pytest.raises(IntegrityError):
        update_profile(
            session,
            user_a,
            payload,
            user_repo=repo,
            level_repo=level_repo,
            now=FIXED_NOW,
        )
    assert session.rollback_count == 1


def test_update_generic_error_rolls_back_and_reraises(
    user_a: FakeUser,
    fake_levels: list[FakeLevel],
    level_repo: FakeLevelRepository,
) -> None:
    session = FakeSession(force_generic_error_on_commit=RuntimeError("DB disconnect"))
    repo = FakeUserRepository(users=[user_a], session=session)

    level1 = next(lvl for lvl in fake_levels if lvl.order_index == 1)
    payload = ProfileUpdateRequest.model_validate({"selectedLevelId": str(level1.id)})

    with pytest.raises(RuntimeError, match="DB disconnect"):
        update_profile(
            session,
            user_a,
            payload,
            user_repo=repo,
            level_repo=level_repo,
            now=FIXED_NOW,
        )
    assert session.rollback_count == 1


def test_update_does_not_log(
    session: FakeSession,
    user_a: FakeUser,
    user_repo: FakeUserRepository,
    level_repo: FakeLevelRepository,
    caplog: pytest.LogCaptureFixture,
    capsys: pytest.CaptureFixture,
) -> None:
    caplog.set_level(logging.DEBUG)
    payload = ProfileUpdateRequest.model_validate({"username": "silent_user"})

    update_profile(
        session,
        user_a,
        payload,
        user_repo=user_repo,
        level_repo=level_repo,
        now=FIXED_NOW,
    )

    # No log messages from app logger
    app_records = [r for r in caplog.records if r.name.startswith("app")]
    assert len(app_records) == 0

    # No stdout or stderr output
    captured = capsys.readouterr()
    assert captured.out == ""
    assert captured.err == ""
