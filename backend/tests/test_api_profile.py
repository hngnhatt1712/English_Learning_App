"""Contract and unit tests for Profile API endpoints (contract 7.3).

Endpoints:
    GET  /api/v1/profile
    PUT  /api/v1/profile

Zero database dependency: all fakes are local to this file.
Test naming strictly follows matrix G of Sprint 3 Prompt C2.
"""
from __future__ import annotations

import logging
import uuid
from datetime import date, datetime, timedelta, timezone
from typing import Any

import jwt as pyjwt
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from sqlalchemy.exc import IntegrityError, OperationalError

from app.api import auth, levels, profile
from app.core.errors import register_handlers
from app.core.jwt import create_access_token
from app.database.session import get_db
from app.models.user import User
from app.services import profile_service
from tests.conftest import assert_envelope, assert_error

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
FIXED_SECRET = "x" * 40
TEST_DATE = date(2026, 10, 13)

# Fixed UUIDs for deterministic test data
LEVEL1_ID = uuid.UUID("10000000-0000-0000-0000-000000000001")
LEVEL2_ID = uuid.UUID("10000000-0000-0000-0000-000000000002")
LEVEL3_ID = uuid.UUID("10000000-0000-0000-0000-000000000003")

USER_A_ID = uuid.UUID("aa000000-0000-0000-0000-000000000001")
USER_B_ID = uuid.UUID("bb000000-0000-0000-0000-000000000002")


# ---------------------------------------------------------------------------
# Test Doubles
# ---------------------------------------------------------------------------


class FakeLevel:
    """Double for Level ORM model."""

    def __init__(
        self,
        *,
        level_id: uuid.UUID,
        name: str,
        order_index: int,
        description: str | None = None,
    ) -> None:
        self.id: uuid.UUID = level_id
        self.name: str = name
        self.order_index: int = order_index
        self.description: str | None = description


FAKE_LEVELS: list[FakeLevel] = [
    FakeLevel(level_id=LEVEL1_ID, name="Beginner (A1)", order_index=1),
    FakeLevel(level_id=LEVEL2_ID, name="Intermediate (B1)", order_index=2),
    FakeLevel(level_id=LEVEL3_ID, name="Advanced (C1)", order_index=3),
]

LEVEL_MAP: dict[uuid.UUID, FakeLevel] = {lvl.id: lvl for lvl in FAKE_LEVELS}


class FakeUser:
    """Double for User ORM model (attached to FakeSession)."""

    def __init__(
        self,
        *,
        user_id: uuid.UUID,
        username: str,
        email: str,
        selected_level_id: uuid.UUID | None = None,
        current_streak: int = 0,
        last_learning_date: date | None = None,
        created_at: datetime | None = None,
        password_changed_at: datetime | None = None,
        password_hash: str = "$argon2id$v=19$fakehash",
    ) -> None:
        self.id: uuid.UUID = user_id
        self.username: str = username
        self.email: str = email
        self.selected_level_id: uuid.UUID | None = selected_level_id
        self.current_streak: int = current_streak
        self.last_learning_date: date | None = last_learning_date
        self.created_at: datetime = created_at or datetime(
            2026, 1, 1, 0, 0, 0, tzinfo=timezone.utc
        )
        self.password_changed_at: datetime | None = password_changed_at
        self.password_hash: str = password_hash


def make_user_a(
    selected_level_id: uuid.UUID | None = None,
    current_streak: int = 5,
    last_learning_date: date | None = None,
    created_at: datetime | None = None,
    password_changed_at: datetime | None = None,
) -> FakeUser:
    """Build the 'caller' user (User A)."""
    return FakeUser(
        user_id=USER_A_ID,
        username="user_a",
        email="user_a@example.com",
        selected_level_id=selected_level_id,
        current_streak=current_streak,
        last_learning_date=last_learning_date
        or date(TEST_DATE.year, TEST_DATE.month, TEST_DATE.day - 1),
        created_at=created_at
        or datetime(2026, 1, 15, 8, 0, 0, tzinfo=timezone(timedelta(hours=7))),
        password_changed_at=password_changed_at,
    )


USER_B = FakeUser(
    user_id=USER_B_ID,
    username="user_b",
    email="user_b@example.com",
)


class FakeSession:
    """Double for SQLAlchemy Session with counters for commit/rollback/flush."""

    def __init__(
        self,
        users: dict[uuid.UUID, FakeUser] | None = None,
        error_on_commit: Exception | None = None,
        error_on_flush: Exception | None = None,
        error_on_get: Exception | None = None,
    ) -> None:
        self.users: dict[uuid.UUID, FakeUser] = dict(users or {})
        self.error_on_commit: Exception | None = error_on_commit
        self.error_on_flush: Exception | None = error_on_flush
        self.error_on_get: Exception | None = error_on_get
        self.commit_count: int = 0
        self.rollback_count: int = 0
        self.flush_count: int = 0

    def commit(self) -> None:
        self.commit_count += 1
        if self.error_on_commit is not None:
            raise self.error_on_commit

    def rollback(self) -> None:
        self.rollback_count += 1

    def flush(self) -> None:
        self.flush_count += 1
        if self.error_on_flush is not None:
            raise self.error_on_flush

    def get(self, model: Any, pk: Any) -> Any:
        if self.error_on_get is not None:
            raise self.error_on_get
        if model is User or getattr(model, "__name__", "") == "User":
            return self.users.get(pk)
        return None


class FakeUserRepository:
    """Double for UserRepository.

    Matches real UserRepository method names and signatures exactly.
    Fake update_selected_level: assigns attribute on the user object, then flushes.
    Fake get_by_username: case-insensitive match.
    Does NOT self-normalise input data (mirrors real repo behaviour where normalisation
    happens in the service layer before calling the repo).
    """

    def __init__(
        self,
        users: list[FakeUser],
        session: FakeSession,
        error_on_get_by_username: FakeUser | str | None = None,
        error_on_update: Exception | None = None,
    ) -> None:
        self._users: list[FakeUser] = list(users)
        self._session: FakeSession = session
        # If error_on_get_by_username is an Exception, raise it; if FakeUser, return it
        self._error_on_get_by_username: FakeUser | str | None = error_on_get_by_username
        self._error_on_update: Exception | None = error_on_update

    def get_by_username(self, username: str) -> FakeUser | None:
        normalized = username.strip().lower()
        for u in self._users:
            if u.username.lower() == normalized:
                return u
        return None

    def get_by_id(self, user_id: uuid.UUID) -> FakeUser | None:
        for u in self._users:
            if u.id == user_id:
                return u
        return None

    def update_selected_level(
        self, user_id: uuid.UUID, level_id: uuid.UUID | None
    ) -> FakeUser | None:
        """Mirrors real repo: assign, flush immediately (may raise IntegrityError)."""
        user = self.get_by_id(user_id)
        if user is None:
            return None
        user.selected_level_id = level_id
        # Simulate flush — may raise IntegrityError (FK violation)
        if self._error_on_update is not None:
            raise self._error_on_update
        self._session.flush()
        return user


class FakeLevelRepository:
    """Double for LevelRepository."""

    def __init__(
        self,
        levels_list: list[FakeLevel] | None = None,
        error_on_get_all: Exception | None = None,
        error_on_get_by_id: Exception | None = None,
    ) -> None:
        self._levels: list[FakeLevel] = list(levels_list or FAKE_LEVELS)
        self.error_on_get_all: Exception | None = error_on_get_all
        self.error_on_get_by_id: Exception | None = error_on_get_by_id

    def get_all(self) -> list[FakeLevel]:
        if self.error_on_get_all is not None:
            raise self.error_on_get_all
        return list(self._levels)

    def get_by_id(self, level_id: uuid.UUID) -> FakeLevel | None:
        if self.error_on_get_by_id is not None:
            raise self.error_on_get_by_id
        return LEVEL_MAP.get(level_id)


# ---------------------------------------------------------------------------
# Helper: build isolated app + TestClient
# ---------------------------------------------------------------------------


def build_app_and_client(session: FakeSession) -> tuple[FastAPI, TestClient]:
    """Return (app, client) with all three routers and db overridden."""
    app = FastAPI()
    register_handlers(app)
    app.include_router(auth.router, prefix="/api/v1")
    app.include_router(levels.router, prefix="/api/v1")
    app.include_router(profile.router, prefix="/api/v1")
    app.dependency_overrides[get_db] = lambda: session
    client = TestClient(app, raise_server_exceptions=False)
    return app, client


def make_token(user_id: uuid.UUID) -> str:
    """Create a signed JWT for test user via create_access_token."""
    return create_access_token(user_id)


def auth_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def _walk_dict(d: Any) -> list[str]:
    """Recursively collect all string keys in a nested dict/list structure."""
    keys: list[str] = []
    if isinstance(d, dict):
        keys.extend(d.keys())
        for v in d.values():
            keys.extend(_walk_dict(v))
    elif isinstance(d, list):
        for item in d:
            keys.extend(_walk_dict(item))
    return keys


# ---------------------------------------------------------------------------
# autouse fixture: fixed JWT secret and today_vn
# ---------------------------------------------------------------------------


@pytest.fixture(autouse=True)
def setup_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("app.core.jwt._get_secret", lambda: FIXED_SECRET)
    monkeypatch.setattr(
        profile_service, "today_vn", lambda now=None: TEST_DATE
    )


# ---------------------------------------------------------------------------
# Helper fixture builder for most tests
# ---------------------------------------------------------------------------


def _make_ctx(
    monkeypatch: pytest.MonkeyPatch,
    user_a: FakeUser | None = None,
    session_kwargs: dict | None = None,
    user_repo_kwargs: dict | None = None,
    level_repo_kwargs: dict | None = None,
) -> dict[str, Any]:
    """Construct test context: session, repos, app, client, token."""
    if user_a is None:
        user_a = make_user_a()

    session = FakeSession(
        users={user_a.id: user_a, USER_B.id: USER_B},
        **(session_kwargs or {}),
    )

    user_repo = FakeUserRepository(
        users=[user_a, USER_B],
        session=session,
        **(user_repo_kwargs or {}),
    )
    level_repo = FakeLevelRepository(**(level_repo_kwargs or {}))

    monkeypatch.setattr(
        profile_service, "UserRepository", lambda s: user_repo
    )
    monkeypatch.setattr(
        profile_service, "LevelRepository", lambda s: level_repo
    )

    app, client = build_app_and_client(session)
    token = make_token(user_a.id)

    return {
        "app": app,
        "client": client,
        "session": session,
        "user_repo": user_repo,
        "level_repo": level_repo,
        "user": user_a,
        "token": token,
        "headers": auth_headers(token),
    }


# ===========================================================================
# GET /api/v1/profile tests
# ===========================================================================


def test_get_profile_returns_200_envelope(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    ctx = _make_ctx(monkeypatch)
    resp = ctx["client"].get("/api/v1/profile", headers=ctx["headers"])
    assert resp.status_code == 200
    data = resp.json()
    assert_envelope(data, ok=True)
    assert data["message"] == "OK"


def test_get_profile_keys_are_exact(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    user_a = make_user_a(selected_level_id=LEVEL1_ID)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].get("/api/v1/profile", headers=ctx["headers"])
    assert resp.status_code == 200
    profile_data = resp.json()["data"]
    assert set(profile_data.keys()) == {
        "id", "username", "email", "level",
        "totalStars", "maxStars", "currentStreak", "createdAt",
    }
    level_data = profile_data["level"]
    assert isinstance(level_data, dict)
    assert set(level_data.keys()) == {"id", "name", "stars", "maxStars", "progressPercent"}
    # No snake_case key anywhere
    all_keys = _walk_dict(resp.json())
    for k in all_keys:
        assert "_" not in k, f"Snake-case key found in response: {k!r}"


def test_get_profile_level_is_null_when_not_selected(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    user_a = make_user_a(selected_level_id=None)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].get("/api/v1/profile", headers=ctx["headers"])
    assert resp.status_code == 200
    profile_data = resp.json()["data"]
    assert "level" in profile_data
    assert profile_data["level"] is None


def test_get_profile_level_matches_selected_level(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    user_a = make_user_a(selected_level_id=LEVEL2_ID)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].get("/api/v1/profile", headers=ctx["headers"])
    assert resp.status_code == 200
    level_data = resp.json()["data"]["level"]
    assert level_data["id"] == str(LEVEL2_ID)
    assert level_data["name"] == "Intermediate (B1)"
    # Sprint 3 placeholders (updated at Sprint 4)
    assert level_data["stars"] == 0
    assert level_data["maxStars"] == 45  # MAX_STARS_PER_LEVEL
    assert level_data["progressPercent"] == 0.0


def test_get_profile_total_and_max_stars(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    ctx = _make_ctx(monkeypatch)
    resp = ctx["client"].get("/api/v1/profile", headers=ctx["headers"])
    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["totalStars"] == 0   # Sprint 3 placeholder
    assert data["maxStars"] == 135   # MAX_STARS_TOTAL (3 * 45)


def test_get_profile_streak_is_zero_after_two_idle_days(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # last_learning_date is 2 days before TEST_DATE (2026-10-13) → streak = 0
    two_days_ago = date(2026, 10, 11)
    user_a = make_user_a(current_streak=7, last_learning_date=two_days_ago)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].get("/api/v1/profile", headers=ctx["headers"])
    assert resp.status_code == 200
    assert resp.json()["data"]["currentStreak"] == 0

    # yesterday (gap = 1 day) → streak preserved
    yesterday = date(2026, 10, 12)
    user_a2 = make_user_a(current_streak=7, last_learning_date=yesterday)
    ctx2 = _make_ctx(monkeypatch, user_a=user_a2)
    resp2 = ctx2["client"].get("/api/v1/profile", headers=ctx2["headers"])
    assert resp2.status_code == 200
    assert resp2.json()["data"]["currentStreak"] == 7


def test_get_profile_created_at_is_utc_z(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """created_at aware +07:00 must be serialized as UTC ISO-8601 ending in Z.

    If this test fails due to schema serialization not converting +07:00 to Z,
    STOP and report — do not patch the schema.
    """
    created = datetime(2026, 1, 15, 8, 0, 0, tzinfo=timezone(timedelta(hours=7)))
    user_a = make_user_a(created_at=created)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].get("/api/v1/profile", headers=ctx["headers"])
    assert resp.status_code == 200
    created_at_str: str = resp.json()["data"]["createdAt"]
    assert created_at_str.endswith("Z"), (
        f"createdAt must end with 'Z' (UTC), got: {created_at_str!r}"
    )
    # Verify the moment is equivalent UTC: 2026-01-15T01:00:00Z
    assert "2026-01-15T01:00:00" in created_at_str


def test_get_profile_never_leaks_sensitive_fields(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    ctx = _make_ctx(monkeypatch)
    resp = ctx["client"].get("/api/v1/profile", headers=ctx["headers"])
    text = resp.text
    assert "passwordHash" not in text
    assert "password_hash" not in text
    assert "passwordChangedAt" not in text
    assert "selectedLevelId" not in text
    assert "$argon2id$" not in text


def test_get_profile_without_token_returns_401(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    ctx = _make_ctx(monkeypatch)
    resp = ctx["client"].get("/api/v1/profile")
    assert resp.status_code == 401
    assert_error(resp.json(), "unauthorized")


def test_get_profile_token_issued_before_password_change_returns_401(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Token iat earlier than password_changed_at → 401 unauthorized."""
    past_changed = datetime(2026, 10, 10, 0, 0, 0, tzinfo=timezone.utc)
    user_a = make_user_a(password_changed_at=past_changed)
    ctx = _make_ctx(monkeypatch, user_a=user_a)

    # Token issued BEFORE password change
    old_iat = int(datetime(2026, 10, 9, 0, 0, 0, tzinfo=timezone.utc).timestamp())
    token = pyjwt.encode(
        {"sub": str(user_a.id), "iat": old_iat, "exp": old_iat + 604800},
        FIXED_SECRET,
        algorithm="HS256",
    )
    resp = ctx["client"].get("/api/v1/profile", headers=auth_headers(token))
    assert resp.status_code == 401
    assert_error(resp.json(), "unauthorized")


def test_get_profile_db_error_is_not_401(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """A DB error in the service must produce 500 internal-error, not 401."""
    # Level repo raises on get_by_id (triggered because user has a selected level)
    level_repo = FakeLevelRepository(
        error_on_get_by_id=OperationalError("db down", None, Exception("db down"))
    )
    user_a2 = make_user_a(selected_level_id=LEVEL1_ID)
    session2 = FakeSession(users={user_a2.id: user_a2, USER_B.id: USER_B})

    user_repo = FakeUserRepository(users=[user_a2, USER_B], session=session2)
    monkeypatch.setattr(profile_service, "UserRepository", lambda s: user_repo)
    monkeypatch.setattr(profile_service, "LevelRepository", lambda s: level_repo)

    _, client = build_app_and_client(session2)
    token = make_token(user_a2.id)
    resp = client.get("/api/v1/profile", headers=auth_headers(token))
    assert resp.status_code == 500
    assert_error(resp.json(), "internal-error")


# ===========================================================================
# PUT /api/v1/profile — success cases
# ===========================================================================


def test_put_profile_username_only(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    user_a = make_user_a(selected_level_id=LEVEL1_ID)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"username": "new_name"},
        headers=ctx["headers"],
    )
    assert resp.status_code == 200
    data = resp.json()
    assert_envelope(data, ok=True)
    assert data["message"] == "Profile updated"
    profile_data = data["data"]
    assert profile_data["username"] == "new_name"
    # Level unchanged
    assert profile_data["level"]["id"] == str(LEVEL1_ID)


def test_put_profile_level_only_first_time_any_level(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """First-time user (selectedLevelId = null) may pick any level including L3."""
    user_a = make_user_a(selected_level_id=None)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"selectedLevelId": str(LEVEL3_ID)},
        headers=ctx["headers"],
    )
    assert resp.status_code == 200
    profile_data = resp.json()["data"]
    assert profile_data["level"]["id"] == str(LEVEL3_ID)


def test_put_profile_both_fields(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    user_a = make_user_a(selected_level_id=None)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"username": "updated_user", "selectedLevelId": str(LEVEL2_ID)},
        headers=ctx["headers"],
    )
    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["username"] == "updated_user"
    assert data["level"]["id"] == str(LEVEL2_ID)


def test_put_profile_same_level_is_idempotent(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Selecting the same level the user already has → 200."""
    user_a = make_user_a(selected_level_id=LEVEL1_ID)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"selectedLevelId": str(LEVEL1_ID)},
        headers=ctx["headers"],
    )
    assert resp.status_code == 200
    assert resp.json()["data"]["level"]["id"] == str(LEVEL1_ID)


def test_put_profile_switch_to_level1_after_level3(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Switching from L3 to L1 is always allowed (L1 always unlocked)."""
    user_a = make_user_a(selected_level_id=LEVEL3_ID)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"selectedLevelId": str(LEVEL1_ID)},
        headers=ctx["headers"],
    )
    assert resp.status_code == 200
    assert resp.json()["data"]["level"]["id"] == str(LEVEL1_ID)


def test_put_profile_back_to_level3_is_locked_without_progress(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """After switching L3 → L1, trying to pick L3 again without progress → 403.

    This is a contract consequence (D22); the unlock-via-progress gate is Sprint 7.
    Comment: update to 200 at Sprint 7 when progress_level_ids is populated.
    """
    # User is now on L1 (previously was on L3, but no progress recorded)
    user_a = make_user_a(selected_level_id=LEVEL1_ID)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"selectedLevelId": str(LEVEL3_ID)},
        headers=ctx["headers"],
    )
    assert resp.status_code == 403
    assert_error(resp.json(), "level-locked")


def test_put_profile_response_reflects_new_values(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    user_a = make_user_a(selected_level_id=LEVEL1_ID)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"username": "reflected", "selectedLevelId": str(LEVEL1_ID)},
        headers=ctx["headers"],
    )
    assert resp.status_code == 200
    data = resp.json()["data"]
    assert data["username"] == "reflected"
    assert data["level"]["id"] == str(LEVEL1_ID)


def test_put_profile_normalizes_username_to_lowercase(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    user_a = make_user_a()
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"username": "New_Name"},
        headers=ctx["headers"],
    )
    assert resp.status_code == 200
    assert resp.json()["data"]["username"] == "new_name"


def test_put_profile_own_username_returns_200(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Sending one's own username (or different case) → 200, not 409."""
    user_a = make_user_a()
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    # Same username, different case
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"username": "User_A"},  # normalized → user_a (same as current)
        headers=ctx["headers"],
    )
    assert resp.status_code == 200


def test_put_profile_commits_exactly_once_on_success(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    user_a = make_user_a()
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    ctx["client"].put(
        "/api/v1/profile",
        json={"username": "commit_once"},
        headers=ctx["headers"],
    )
    assert ctx["session"].commit_count == 1


# ===========================================================================
# PUT /api/v1/profile — 422 validation
# ===========================================================================


def test_put_profile_empty_object_returns_422(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    ctx = _make_ctx(monkeypatch)
    resp = ctx["client"].put(
        "/api/v1/profile", json={}, headers=ctx["headers"]
    )
    assert resp.status_code == 422
    data = resp.json()
    assert_error(data, "validation-error")
    assert isinstance(data["error"]["errors"], list)
    assert len(data["error"]["errors"]) > 0


def test_put_profile_without_body_returns_422(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    ctx = _make_ctx(monkeypatch)
    resp = ctx["client"].put("/api/v1/profile", headers=ctx["headers"])
    assert resp.status_code == 422
    assert_error(resp.json(), "validation-error")


def test_put_profile_null_username_returns_422(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    ctx = _make_ctx(monkeypatch)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"username": None},
        headers=ctx["headers"],
    )
    assert resp.status_code == 422
    assert_error(resp.json(), "validation-error")


def test_put_profile_null_selected_level_returns_422(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    ctx = _make_ctx(monkeypatch)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"selectedLevelId": None},
        headers=ctx["headers"],
    )
    assert resp.status_code == 422
    assert_error(resp.json(), "validation-error")


@pytest.mark.parametrize(
    "bad_username",
    [
        "ab",           # too short (< 3)
        "a" * 31,       # too long (> 30)
        "user@name",    # contains @
        "user name",    # contains space
        "user!name",    # invalid char
        "user#name",    # invalid char
    ],
)
def test_put_profile_invalid_username_returns_422(
    monkeypatch: pytest.MonkeyPatch,
    bad_username: str,
) -> None:
    ctx = _make_ctx(monkeypatch)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"username": bad_username},
        headers=ctx["headers"],
    )
    assert resp.status_code == 422
    data = resp.json()
    assert_error(data, "validation-error")
    errors = data["error"]["errors"]
    assert isinstance(errors, list) and len(errors) > 0
    # At least one error targets the username field
    username_errors = [e for e in errors if "username" in e.get("field", "")]
    assert len(username_errors) > 0, f"No username error found in {errors}"
    for err in username_errors:
        assert set(err.keys()) == {"field", "message", "code"}
        valid_codes = {
            "REQUIRED", "INVALID_FORMAT", "INVALID_ENUM", "INVALID_VALUE",
            "MIN_LENGTH", "MAX_LENGTH", "MIN_VALUE", "MAX_VALUE",
        }
        assert err["code"] in valid_codes, f"Unexpected code: {err['code']}"


def test_put_profile_invalid_level_uuid_returns_422(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    ctx = _make_ctx(monkeypatch)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"selectedLevelId": "not-a-uuid"},
        headers=ctx["headers"],
    )
    assert resp.status_code == 422
    data = resp.json()
    assert_error(data, "validation-error")
    errors = data["error"]["errors"]
    level_errors = [e for e in errors if "selectedLevelId" in e.get("field", "")]
    assert len(level_errors) > 0, f"No selectedLevelId error found in {errors}"


def test_put_profile_only_unknown_fields_returns_422(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Body with only unknown fields (e.g. email) → 422 (no valid field sent)."""
    ctx = _make_ctx(monkeypatch)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"email": "new@example.com"},
        headers=ctx["headers"],
    )
    assert resp.status_code == 422
    assert_error(resp.json(), "validation-error")


def test_put_profile_invalid_json_returns_422(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    ctx = _make_ctx(monkeypatch)
    resp = ctx["client"].put(
        "/api/v1/profile",
        content=b"not json at all!!",
        headers={**ctx["headers"], "Content-Type": "application/json"},
    )
    assert resp.status_code == 422
    assert_error(resp.json(), "validation-error")


# ===========================================================================
# PUT /api/v1/profile — error cases (404 / 403 / 409)
# ===========================================================================


def test_put_profile_unknown_level_returns_404_not_found(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Valid UUID format but level not in DB → 404 not-found."""
    unknown_id = uuid.uuid4()
    user_a = make_user_a(selected_level_id=None)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"selectedLevelId": str(unknown_id)},
        headers=ctx["headers"],
    )
    assert resp.status_code == 404
    assert_error(resp.json(), "not-found")


def test_put_profile_locked_level_returns_403_level_locked(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """User has L3; trying to switch to L2 (not unlocked) → 403 level-locked."""
    user_a = make_user_a(selected_level_id=LEVEL3_ID)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"selectedLevelId": str(LEVEL2_ID)},
        headers=ctx["headers"],
    )
    assert resp.status_code == 403
    data = resp.json()
    assert_error(data, "level-locked")
    # Confirm it is not the generic 'forbidden'
    assert data["error"]["type"] != "forbidden"


def test_put_profile_username_taken_returns_409(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Username already used by user B → 409 username-taken (case-insensitive)."""
    user_a = make_user_a()
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    # USER_B.username = "user_b"
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"username": "User_B"},  # normalized → user_b, belongs to B
        headers=ctx["headers"],
    )
    assert resp.status_code == 409
    assert_error(resp.json(), "username-taken")


@pytest.mark.parametrize(
    "desc,body,expected_status,expected_type",
    [
        (
            "404+403+409 → 404",
            {"selectedLevelId": str(uuid.uuid4()), "username": "user_b"},
            404,
            "not-found",
        ),
        (
            "403+409 → 403",
            {"selectedLevelId": str(LEVEL2_ID), "username": "user_b"},
            403,
            "level-locked",
        ),
    ],
)
def test_put_profile_error_precedence(
    monkeypatch: pytest.MonkeyPatch,
    desc: str,
    body: dict,
    expected_status: int,
    expected_type: str,
) -> None:
    """Check order: 404 not-found → 403 level-locked → 409 username-taken."""
    # User is on LEVEL3 so LEVEL2 is locked
    user_a = make_user_a(selected_level_id=LEVEL3_ID)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].put(
        "/api/v1/profile", json=body, headers=ctx["headers"]
    )
    assert resp.status_code == expected_status, f"[{desc}] Expected {expected_status}"
    assert_error(resp.json(), expected_type)


def test_put_profile_atomic_when_username_taken(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Username taken + valid level → 409; selectedLevelId unchanged; commit = 0."""
    user_a = make_user_a(selected_level_id=LEVEL1_ID)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    old_level = user_a.selected_level_id
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"username": "user_b", "selectedLevelId": str(LEVEL1_ID)},
        headers=ctx["headers"],
    )
    assert resp.status_code == 409
    assert user_a.selected_level_id == old_level
    assert ctx["session"].commit_count == 0


def test_put_profile_atomic_when_level_locked(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Level locked + new username → 403; username not changed; commit = 0."""
    user_a = make_user_a(selected_level_id=LEVEL3_ID)
    old_username = user_a.username
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"selectedLevelId": str(LEVEL2_ID), "username": "brand_new"},
        headers=ctx["headers"],
    )
    assert resp.status_code == 403
    assert user_a.username == old_username
    assert ctx["session"].commit_count == 0


def test_put_profile_atomic_when_level_not_found(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Level not found + new username → 404; username not changed; commit = 0."""
    user_a = make_user_a()
    old_username = user_a.username
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"selectedLevelId": str(uuid.uuid4()), "username": "brand_new"},
        headers=ctx["headers"],
    )
    assert resp.status_code == 404
    assert user_a.username == old_username
    assert ctx["session"].commit_count == 0


def test_put_profile_integrity_error_on_flush_returns_409(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """IntegrityError on flush (race: concurrent INSERT) → 409 username-taken + rollback.

    Simulates: pre-check returns None (no conflict yet), then flush raises IntegrityError,
    service rolls back, re-checks get_by_username → finds USER_B → 409.
    """
    user_a = make_user_a()
    flush_error = IntegrityError("unique constraint", None, Exception("unique constraint"))

    # Stateful mock: first call to get_by_username (pre-check) returns None (no conflict yet);
    # second call (post-rollback re-check) returns USER_B (race loser).
    get_by_username_calls: list[int] = [0]

    class RaceUserRepo(FakeUserRepository):
        def get_by_username(self, username: str) -> FakeUser | None:  # type: ignore[override]
            get_by_username_calls[0] += 1
            if get_by_username_calls[0] == 1:
                return None  # pre-check: no conflict visible yet
            # post-rollback re-check: conflict now visible
            return USER_B

    session = FakeSession(users={user_a.id: user_a, USER_B.id: USER_B})

    # Patch session.flush to raise on first call (simulates DB unique violation)
    original_flush = session.flush
    flush_calls: list[int] = [0]

    def patched_flush() -> None:
        flush_calls[0] += 1
        if flush_calls[0] == 1:
            raise flush_error
        original_flush()

    session.flush = patched_flush  # type: ignore[method-assign]

    user_repo = RaceUserRepo(users=[user_a, USER_B], session=session)
    monkeypatch.setattr(profile_service, "UserRepository", lambda s: user_repo)
    monkeypatch.setattr(
        profile_service, "LevelRepository", lambda s: FakeLevelRepository()
    )

    _, client = build_app_and_client(session)
    token = make_token(user_a.id)

    resp = client.put(
        "/api/v1/profile",
        json={"username": "race_winner"},  # not in repo initially → pre-check OK
        headers=auth_headers(token),
    )
    assert resp.status_code == 409
    assert_error(resp.json(), "username-taken")
    assert session.rollback_count >= 1


def test_put_profile_integrity_error_on_commit_returns_409(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """IntegrityError on commit (race condition) → 409 username-taken + rollback.

    Simulates: pre-check returns None (no conflict yet), commit raises IntegrityError,
    service rolls back, re-checks get_by_username → finds USER_B → 409.
    """
    user_a = make_user_a()
    commit_error = IntegrityError(
        "unique violation on commit", None, Exception("unique violation on commit")
    )

    # Stateful mock: first call (pre-check) → None; second call (post-rollback) → USER_B
    get_calls: list[int] = [0]

    class RaceUserRepo(FakeUserRepository):
        def get_by_username(self, username: str) -> FakeUser | None:  # type: ignore[override]
            get_calls[0] += 1
            if get_calls[0] == 1:
                return None  # pre-check: no conflict visible yet (race not yet lost)
            return USER_B   # post-rollback re-check: the other transaction committed

    session = FakeSession(
        users={user_a.id: user_a, USER_B.id: USER_B},
        error_on_commit=commit_error,
    )
    user_repo = RaceUserRepo(users=[user_a, USER_B], session=session)
    monkeypatch.setattr(profile_service, "UserRepository", lambda s: user_repo)
    monkeypatch.setattr(
        profile_service, "LevelRepository", lambda s: FakeLevelRepository()
    )

    _, client = build_app_and_client(session)
    token = make_token(user_a.id)

    resp = client.put(
        "/api/v1/profile",
        json={"username": "race_name"},  # not in repo initially → pre-check OK
        headers=auth_headers(token),
    )
    assert resp.status_code == 409
    assert_error(resp.json(), "username-taken")
    assert session.rollback_count >= 1


def test_put_profile_unknown_integrity_error_returns_500_envelope(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """IntegrityError that is NOT a username conflict → 500 internal-error (not 409/401)."""
    user_a = make_user_a()
    # Use a username that does NOT match any existing user (so re-check finds nothing)
    user_b_unique = FakeUser(
        user_id=USER_B_ID,
        username="user_b_unique",
        email="user_b@example.com",
    )
    commit_error = IntegrityError("fk_violation", None, Exception("fk_violation"))
    session = FakeSession(
        users={user_a.id: user_a, user_b_unique.id: user_b_unique},
        error_on_commit=commit_error,
    )
    user_repo = FakeUserRepository(users=[user_a, user_b_unique], session=session)
    monkeypatch.setattr(profile_service, "UserRepository", lambda s: user_repo)
    monkeypatch.setattr(
        profile_service, "LevelRepository", lambda s: FakeLevelRepository()
    )

    _, client = build_app_and_client(session)
    token = make_token(user_a.id)

    # Send a username that won't match any existing user after rollback
    resp = client.put(
        "/api/v1/profile",
        json={"username": "totally_new_name"},
        headers=auth_headers(token),
    )
    assert resp.status_code == 500
    assert_error(resp.json(), "internal-error")
    assert session.rollback_count >= 1


# ===========================================================================
# PUT security
# ===========================================================================


def test_put_profile_ignores_mass_assignment(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Extra fields (email, id, currentStreak, passwordHash) must be ignored."""
    user_a = make_user_a()
    original_email = user_a.email
    original_id = user_a.id
    original_streak = user_a.current_streak
    original_hash = user_a.password_hash

    ctx = _make_ctx(monkeypatch, user_a=user_a)
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={
            "username": "safe_update",
            "email": "hacked@evil.com",
            "id": str(uuid.uuid4()),
            "currentStreak": 9999,
            "passwordHash": "evil_hash",
        },
        headers=ctx["headers"],
    )
    assert resp.status_code == 200
    assert resp.json()["data"]["username"] == "safe_update"
    # ORM object attributes unchanged
    assert user_a.email == original_email
    assert user_a.id == original_id
    assert user_a.current_streak == original_streak
    assert user_a.password_hash == original_hash


def test_put_profile_without_token_returns_401(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    ctx = _make_ctx(monkeypatch)
    resp = ctx["client"].put("/api/v1/profile", json={"username": "hacker"})
    assert resp.status_code == 401
    assert_error(resp.json(), "unauthorized")
    assert ctx["session"].commit_count == 0


def test_put_profile_token_issued_before_password_change_returns_401(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    past_changed = datetime(2026, 10, 10, 0, 0, 0, tzinfo=timezone.utc)
    user_a = make_user_a(password_changed_at=past_changed)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    old_iat = int(datetime(2026, 10, 9, 0, 0, 0, tzinfo=timezone.utc).timestamp())
    token = pyjwt.encode(
        {"sub": str(user_a.id), "iat": old_iat, "exp": old_iat + 604800},
        FIXED_SECRET,
        algorithm="HS256",
    )
    resp = ctx["client"].put(
        "/api/v1/profile",
        json={"username": "should_fail"},
        headers=auth_headers(token),
    )
    assert resp.status_code == 401
    assert_error(resp.json(), "unauthorized")


# ===========================================================================
# Integration / flow tests
# ===========================================================================


def test_choose_level_flow_levels_put_profile_me(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """Full Choose Level flow: GET /levels → PUT /profile → GET /levels → GET /auth/me."""
    from app.services import level_service

    user_a = make_user_a(selected_level_id=None)
    session = FakeSession(users={user_a.id: user_a, USER_B.id: USER_B})
    user_repo = FakeUserRepository(users=[user_a, USER_B], session=session)
    level_repo = FakeLevelRepository()

    monkeypatch.setattr(profile_service, "UserRepository", lambda s: user_repo)
    monkeypatch.setattr(profile_service, "LevelRepository", lambda s: level_repo)
    monkeypatch.setattr(level_service, "LevelRepository", lambda s: level_repo)

    _app, client = build_app_and_client(session)
    token = make_token(user_a.id)
    hdrs = auth_headers(token)

    # 1. GET /levels — all 3 unlocked (first-time user)
    resp = client.get("/api/v1/levels", headers=hdrs)
    assert resp.status_code == 200
    items = resp.json()["data"]["items"]
    assert all(it["status"] == "unlocked" for it in items)

    # 2. PUT /profile — select Level 3
    resp = client.put(
        "/api/v1/profile",
        json={"selectedLevelId": str(LEVEL3_ID)},
        headers=hdrs,
    )
    assert resp.status_code == 200
    assert resp.json()["data"]["level"]["id"] == str(LEVEL3_ID)

    # 3. GET /levels — L2 should now be locked
    resp = client.get("/api/v1/levels", headers=hdrs)
    assert resp.status_code == 200
    items_after = {it["id"]: it for it in resp.json()["data"]["items"]}
    assert items_after[str(LEVEL2_ID)]["status"] == "locked"
    assert items_after[str(LEVEL1_ID)]["status"] == "unlocked"
    assert items_after[str(LEVEL3_ID)]["status"] == "unlocked"

    # 4. GET /auth/me — selectedLevelId reflects new choice
    resp = client.get("/api/v1/auth/me", headers=hdrs)
    assert resp.status_code == 200
    assert resp.json()["data"]["selectedLevelId"] == str(LEVEL3_ID)


def test_profile_routes_do_not_redirect(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """GET and PUT /api/v1/profile must return 200/40x directly, never 307."""
    user_a = make_user_a()
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    client_no_redirect = TestClient(
        ctx["app"], raise_server_exceptions=False, follow_redirects=False
    )

    resp_get = client_no_redirect.get(
        "/api/v1/profile", headers=ctx["headers"]
    )
    assert resp_get.status_code not in (301, 302, 307, 308)
    assert resp_get.status_code == 200

    resp_put = client_no_redirect.put(
        "/api/v1/profile",
        json={"username": "no_redirect"},
        headers=ctx["headers"],
    )
    assert resp_put.status_code not in (301, 302, 307, 308)
    assert resp_put.status_code == 200


def test_profile_responses_have_no_snake_case_keys(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """All response bodies (200 and errors) must use camelCase keys only."""
    user_a = make_user_a(selected_level_id=LEVEL3_ID)
    ctx = _make_ctx(monkeypatch, user_a=user_a)

    cases = [
        ("GET 200", lambda: ctx["client"].get("/api/v1/profile", headers=ctx["headers"])),
        ("GET 401", lambda: ctx["client"].get("/api/v1/profile")),
        (
            "PUT 403",
            lambda: ctx["client"].put(
                "/api/v1/profile",
                json={"selectedLevelId": str(LEVEL2_ID)},
                headers=ctx["headers"],
            ),
        ),
        (
            "PUT 404",
            lambda: ctx["client"].put(
                "/api/v1/profile",
                json={"selectedLevelId": str(uuid.uuid4())},
                headers=ctx["headers"],
            ),
        ),
        (
            "PUT 409",
            lambda: ctx["client"].put(
                "/api/v1/profile",
                json={"username": "user_b"},
                headers=ctx["headers"],
            ),
        ),
        (
            "PUT 422",
            lambda: ctx["client"].put(
                "/api/v1/profile",
                json={},
                headers=ctx["headers"],
            ),
        ),
    ]

    for label, call in cases:
        resp = call()
        all_keys = _walk_dict(resp.json())
        snake_keys = [k for k in all_keys if "_" in k]
        assert not snake_keys, f"[{label}] snake_case keys found: {snake_keys}"


def test_profile_error_trace_id_matches_request_id_header(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """X-Request-Id header in request must equal error.traceId in response."""
    user_a = make_user_a(selected_level_id=LEVEL3_ID)
    ctx = _make_ctx(monkeypatch, user_a=user_a)
    request_id = str(uuid.uuid4())

    cases: list[tuple[str, str, dict[str, Any] | None, dict[str, str]]] = [
        ("401 GET", "GET", None, {}),
        ("403", "PUT", {"selectedLevelId": str(LEVEL2_ID)}, ctx["headers"]),
        ("404", "PUT", {"selectedLevelId": str(uuid.uuid4())}, ctx["headers"]),
        ("409", "PUT", {"username": "user_b"}, ctx["headers"]),
        ("422", "PUT", {}, ctx["headers"]),
    ]

    for desc, method, req_json, headers_extra in cases:
        combined_headers = {**headers_extra, "X-Request-Id": request_id}
        if method == "GET":
            resp = ctx["client"].get("/api/v1/profile", headers=combined_headers)
        else:
            resp = ctx["client"].put(
                "/api/v1/profile", json=req_json, headers=combined_headers
            )
        body = resp.json()
        assert resp.status_code >= 400, f"Expected error status for {desc}"
        assert body["error"]["traceId"] == request_id, (
            f"[{desc}] traceId mismatch: {body['error']['traceId']!r} != {request_id!r}"
        )


def test_profile_422_has_errors_array(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """422 validation-error must have errors[] with field/message/code."""
    ctx = _make_ctx(monkeypatch)
    resp = ctx["client"].put(
        "/api/v1/profile", json={}, headers=ctx["headers"]
    )
    assert resp.status_code == 422
    data = resp.json()
    assert_error(data, "validation-error")
    errors = data["error"]["errors"]
    assert isinstance(errors, list) and len(errors) > 0
    for err in errors:
        assert set(err.keys()) == {"field", "message", "code"}


def test_profile_no_token_in_logs(
    monkeypatch: pytest.MonkeyPatch,
    caplog: pytest.LogCaptureFixture,
) -> None:
    """JWT token must never appear in log output at DEBUG level."""
    ctx = _make_ctx(monkeypatch)
    token = ctx["token"]
    with caplog.at_level(logging.DEBUG):
        ctx["client"].get("/api/v1/profile", headers=ctx["headers"])
    log_text = caplog.text
    assert token not in log_text


def test_openapi_documents_profile(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """OpenAPI spec must document GET and PUT /api/v1/profile correctly."""
    ctx = _make_ctx(monkeypatch)
    app: FastAPI = ctx["app"]
    spec = app.openapi()
    paths = spec.get("paths", {})
    assert "/api/v1/profile" in paths, "Path /api/v1/profile missing from OpenAPI"
    profile_paths = paths["/api/v1/profile"]

    # GET
    get_spec = profile_paths.get("get", {})
    assert get_spec.get("summary") == "Get the current user's profile"
    assert get_spec.get("tags") == ["profile"]
    assert "security" in get_spec
    assert "200" in get_spec.get("responses", {})
    assert "401" in get_spec.get("responses", {})

    # PUT
    put_spec = profile_paths.get("put", {})
    assert put_spec.get("summary") == "Update username and/or selected level (partial update)"
    assert put_spec.get("tags") == ["profile"]
    assert "security" in put_spec
    put_responses = put_spec.get("responses", {})
    for code in ("200", "401", "403", "404", "409", "422"):
        assert code in put_responses, f"PUT response {code} missing from OpenAPI"


def test_main_app_mounts_profile_routes() -> None:
    """Production app.main mounts GET and PUT /api/v1/profile (P10 allowed)."""
    from app.main import app as main_app

    paths = main_app.openapi().get("paths", {})
    assert "/api/v1/profile" in paths
    profile_ops = paths["/api/v1/profile"]
    assert "get" in profile_ops
    assert "put" in profile_ops
