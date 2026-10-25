"""Unit and contract tests for Levels API endpoint (contract 7.4).

Endpoint:
- GET /api/v1/levels

Zero database dependency: Local FakeSession and FakeLevelRepository.
"""
from __future__ import annotations

import logging
import uuid
from collections.abc import Generator
from datetime import datetime, timedelta, timezone
from typing import Any

import jwt as pyjwt
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api import levels
from app.core.errors import register_handlers
from app.core.jwt import create_access_token
from app.database.session import get_db
from app.models.user import User
from app.services import level_service
from tests.conftest import assert_envelope, assert_error

FIXED_SECRET = "x" * 40


# ---------------------------------------------------------------------------
# Test Doubles (local to this test file)
# ---------------------------------------------------------------------------

class FakeLevel:
    """Double for Level model."""

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


class FakeLevelRepository:
    """Double for LevelRepository returning configurable levels."""

    def __init__(
        self,
        levels_list: list[FakeLevel] | None = None,
        *,
        error_on_get_all: Exception | None = None,
    ) -> None:
        self._levels: list[FakeLevel] = list(levels_list or [])
        self.error_on_get_all: Exception | None = error_on_get_all

    def get_all(self) -> list[FakeLevel]:
        if self.error_on_get_all is not None:
            raise self.error_on_get_all
        return list(self._levels)


class FakeUser:
    """Double for User model for testing without database."""

    def __init__(
        self,
        *,
        user_id: uuid.UUID | None = None,
        username: str = "testuser",
        email: str = "test@example.com",
        selected_level_id: uuid.UUID | None = None,
        password_changed_at: datetime | None = None,
    ) -> None:
        self.id: uuid.UUID = user_id or uuid.uuid4()
        self.username: str = username
        self.email: str = email
        self.selected_level_id: uuid.UUID | None = selected_level_id
        self.password_changed_at: datetime | None = password_changed_at


class FakeSession:
    """Double for SQLAlchemy Session tracking commit/rollback calls."""

    def __init__(self, users: dict[uuid.UUID, FakeUser] | None = None) -> None:
        self.users: dict[uuid.UUID, FakeUser] = dict(users or {})
        self.commit_count: int = 0
        self.rollback_count: int = 0

    def commit(self) -> None:
        self.commit_count += 1

    def rollback(self) -> None:
        self.rollback_count += 1

    def get(self, model: Any, pk: Any) -> Any:
        # get_current_user calls db.get(User, user_id)
        if model is User or getattr(model, "__name__", "") == "User":
            return self.users.get(pk)
        return None


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(autouse=True)
def setup_jwt_secret(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("app.core.jwt._get_secret", lambda: FIXED_SECRET)


@pytest.fixture
def level_ids() -> tuple[uuid.UUID, uuid.UUID, uuid.UUID]:
    return (uuid.uuid4(), uuid.uuid4(), uuid.uuid4())


@pytest.fixture
def fake_levels(level_ids: tuple[uuid.UUID, uuid.UUID, uuid.UUID]) -> list[FakeLevel]:
    id1, id2, id3 = level_ids
    # Shuffled order: index 3, 1, 2 (to verify sorting by order_index in service)
    return [
        FakeLevel(level_id=id3, name="Advanced", order_index=3, description="C1-C2 Level"),
        FakeLevel(level_id=id1, name="Beginner", order_index=1, description=None),  # Nullable description
        FakeLevel(level_id=id2, name="Intermediate", order_index=2, description="B1-B2 Level"),
    ]


@pytest.fixture
def default_user() -> FakeUser:
    return FakeUser(
        username="john_doe",
        email="john@example.com",
        selected_level_id=None,
    )


@pytest.fixture
def test_ctx(
    default_user: FakeUser,
    fake_levels: list[FakeLevel],
    monkeypatch: pytest.MonkeyPatch,
) -> Generator[dict[str, Any], None, None]:
    session = FakeSession(users={default_user.id: default_user})
    fake_repo = FakeLevelRepository(fake_levels)

    # Monkeypatch LevelRepository instantiation in level_service
    monkeypatch.setattr(level_service, "LevelRepository", lambda s: fake_repo)

    app = FastAPI()
    register_handlers(app)
    app.include_router(levels.router, prefix="/api/v1")
    app.dependency_overrides[get_db] = lambda: session

    client = TestClient(app, raise_server_exceptions=False)
    token = create_access_token(default_user.id)
    auth_headers = {"Authorization": f"Bearer {token}"}

    yield {
        "app": app,
        "client": client,
        "session": session,
        "repo": fake_repo,
        "user": default_user,
        "token": token,
        "auth_headers": auth_headers,
        "levels": fake_levels,
    }

    app.dependency_overrides.clear()


# ---------------------------------------------------------------------------
# Test Cases (Matrix G)
# ---------------------------------------------------------------------------

def test_levels_returns_200_envelope(test_ctx: dict[str, Any]) -> None:
    client: TestClient = test_ctx["client"]
    res = client.get("/api/v1/levels", headers=test_ctx["auth_headers"])
    assert res.status_code == 200
    body = res.json()
    assert_envelope(body, ok=True)
    assert body["message"] == "OK"


def test_levels_items_are_three_and_sorted_by_order_index(test_ctx: dict[str, Any]) -> None:
    client: TestClient = test_ctx["client"]
    res = client.get("/api/v1/levels", headers=test_ctx["auth_headers"])
    assert res.status_code == 200
    data = res.json()["data"]
    assert set(data.keys()) == {"items"}
    items = data["items"]
    assert len(items) == 3
    order_indices = [item["orderIndex"] for item in items]
    assert order_indices == [1, 2, 3]


def test_levels_item_keys_are_exact(test_ctx: dict[str, Any]) -> None:
    client: TestClient = test_ctx["client"]
    res = client.get("/api/v1/levels", headers=test_ctx["auth_headers"])
    assert res.status_code == 200
    items = res.json()["data"]["items"]

    expected_keys = {
        "id",
        "name",
        "description",
        "orderIndex",
        "status",
        "stars",
        "maxStars",
        "topicsCompleted",
        "topicsTotal",
    }
    for item in items:
        assert set(item.keys()) == expected_keys
        for key in item:
            assert "_" not in key


def test_levels_nullable_description_is_present_as_null(test_ctx: dict[str, Any]) -> None:
    client: TestClient = test_ctx["client"]
    res = client.get("/api/v1/levels", headers=test_ctx["auth_headers"])
    assert res.status_code == 200
    items = res.json()["data"]["items"]
    # Level 1 has description=None
    lvl1 = next(item for item in items if item["orderIndex"] == 1)
    assert "description" in lvl1
    assert lvl1["description"] is None


def test_levels_status_when_no_level_selected(test_ctx: dict[str, Any]) -> None:
    user: FakeUser = test_ctx["user"]
    user.selected_level_id = None
    client: TestClient = test_ctx["client"]

    res = client.get("/api/v1/levels", headers=test_ctx["auth_headers"])
    assert res.status_code == 200
    items = res.json()["data"]["items"]
    # D14 / Contract 7.4: When selectedLevelId is null, all 3 levels are unlocked
    for item in items:
        assert item["status"] == "unlocked"


def test_levels_status_when_level1_selected(test_ctx: dict[str, Any]) -> None:
    user: FakeUser = test_ctx["user"]
    levels_list: list[FakeLevel] = test_ctx["levels"]
    lvl1 = next(lvl for lvl in levels_list if lvl.order_index == 1)
    user.selected_level_id = lvl1.id
    client: TestClient = test_ctx["client"]

    res = client.get("/api/v1/levels", headers=test_ctx["auth_headers"])
    assert res.status_code == 200
    items = res.json()["data"]["items"]
    status_by_order = {item["orderIndex"]: item["status"] for item in items}
    assert status_by_order[1] == "unlocked"
    assert status_by_order[2] == "locked"
    assert status_by_order[3] == "locked"


def test_levels_status_when_level2_selected(test_ctx: dict[str, Any]) -> None:
    user: FakeUser = test_ctx["user"]
    levels_list: list[FakeLevel] = test_ctx["levels"]
    lvl2 = next(lvl for lvl in levels_list if lvl.order_index == 2)
    user.selected_level_id = lvl2.id
    client: TestClient = test_ctx["client"]

    res = client.get("/api/v1/levels", headers=test_ctx["auth_headers"])
    assert res.status_code == 200
    items = res.json()["data"]["items"]
    status_by_order = {item["orderIndex"]: item["status"] for item in items}
    assert status_by_order[1] == "unlocked"
    assert status_by_order[2] == "unlocked"
    assert status_by_order[3] == "locked"


def test_levels_status_when_level3_selected(test_ctx: dict[str, Any]) -> None:
    user: FakeUser = test_ctx["user"]
    levels_list: list[FakeLevel] = test_ctx["levels"]
    lvl3 = next(lvl for lvl in levels_list if lvl.order_index == 3)
    user.selected_level_id = lvl3.id
    client: TestClient = test_ctx["client"]

    res = client.get("/api/v1/levels", headers=test_ctx["auth_headers"])
    assert res.status_code == 200
    items = res.json()["data"]["items"]
    status_by_order = {item["orderIndex"]: item["status"] for item in items}
    assert status_by_order[1] == "unlocked"
    assert status_by_order[3] == "unlocked"
    assert status_by_order[2] == "locked"


def test_levels_sprint3_placeholder_values(test_ctx: dict[str, Any]) -> None:
    # Placeholders for Sprint 3 (to be updated in Sprint 4 when progress is implemented)
    client: TestClient = test_ctx["client"]
    res = client.get("/api/v1/levels", headers=test_ctx["auth_headers"])
    assert res.status_code == 200
    items = res.json()["data"]["items"]
    for item in items:
        assert item["stars"] == 0
        assert item["maxStars"] == 45
        assert item["topicsCompleted"] == 0
        assert item["topicsTotal"] == 3


def test_levels_empty_table_returns_empty_items(test_ctx: dict[str, Any]) -> None:
    repo: FakeLevelRepository = test_ctx["repo"]
    repo._levels = []
    client: TestClient = test_ctx["client"]

    res = client.get("/api/v1/levels", headers=test_ctx["auth_headers"])
    assert res.status_code == 200
    items = res.json()["data"]["items"]
    assert items == []
    assert items is not None


def test_levels_without_token_returns_401_unauthorized(test_ctx: dict[str, Any]) -> None:
    client: TestClient = test_ctx["client"]
    res = client.get("/api/v1/levels")
    assert res.status_code == 401
    assert_error(res.json(), "unauthorized")


def test_levels_expired_token_returns_401(test_ctx: dict[str, Any]) -> None:
    user: FakeUser = test_ctx["user"]
    past_timestamp = int(datetime.now(timezone.utc).timestamp()) - 3600
    expired_token = pyjwt.encode(
        {
            "sub": str(user.id),
            "iat": past_timestamp - 3600,
            "exp": past_timestamp,
        },
        FIXED_SECRET,
        algorithm="HS256",
    )
    client: TestClient = test_ctx["client"]
    res = client.get("/api/v1/levels", headers={"Authorization": f"Bearer {expired_token}"})
    assert res.status_code == 401
    assert_error(res.json(), "unauthorized")


def test_levels_invalid_token_returns_401(test_ctx: dict[str, Any]) -> None:
    client: TestClient = test_ctx["client"]
    res = client.get("/api/v1/levels", headers={"Authorization": "Bearer not_a_valid_jwt_token"})
    assert res.status_code == 401
    assert_error(res.json(), "unauthorized")


def test_levels_token_issued_before_password_change_returns_401(test_ctx: dict[str, Any]) -> None:
    user: FakeUser = test_ctx["user"]
    now = datetime.now(timezone.utc)
    # Token iat is 1 hour in the past
    token_iat = int((now - timedelta(hours=1)).timestamp())
    token = pyjwt.encode(
        {
            "sub": str(user.id),
            "iat": token_iat,
            "exp": int((now + timedelta(hours=1)).timestamp()),
        },
        FIXED_SECRET,
        algorithm="HS256",
    )
    # Password changed after token iat
    user.password_changed_at = now

    client: TestClient = test_ctx["client"]
    res = client.get("/api/v1/levels", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 401
    assert_error(res.json(), "unauthorized")


def test_levels_does_not_commit(test_ctx: dict[str, Any]) -> None:
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    res = client.get("/api/v1/levels", headers=test_ctx["auth_headers"])
    assert res.status_code == 200
    assert session.commit_count == 0


def test_levels_without_trailing_slash_does_not_redirect(test_ctx: dict[str, Any]) -> None:
    client: TestClient = test_ctx["client"]
    # Verify no 307 redirect when calling exact path without trailing slash
    res = client.get("/api/v1/levels", headers=test_ctx["auth_headers"], follow_redirects=False)
    assert res.status_code == 200


def test_levels_repo_error_returns_500_not_401(test_ctx: dict[str, Any]) -> None:
    repo: FakeLevelRepository = test_ctx["repo"]
    repo.error_on_get_all = RuntimeError("Database connection failure")
    client: TestClient = test_ctx["client"]

    res = client.get("/api/v1/levels", headers=test_ctx["auth_headers"])
    assert res.status_code == 500
    body = res.json()
    assert_envelope(body, ok=False)
    assert body["error"]["type"] == "internal-error"


def test_levels_error_trace_id_matches_request_id_header(test_ctx: dict[str, Any]) -> None:
    client: TestClient = test_ctx["client"]
    custom_trace_id = "trace-test-12345"
    res = client.get(
        "/api/v1/levels",
        headers={"X-Request-Id": custom_trace_id},
    )
    assert res.status_code == 401
    assert res.headers.get("X-Request-Id") == custom_trace_id
    body = res.json()
    assert body["error"]["traceId"] == custom_trace_id


def test_levels_responses_have_no_snake_case_keys(test_ctx: dict[str, Any]) -> None:
    client: TestClient = test_ctx["client"]

    def assert_no_snake_case(obj: Any) -> None:
        if isinstance(obj, dict):
            for k, v in obj.items():
                assert "_" not in k, f"Found snake_case key: '{k}'"
                assert_no_snake_case(v)
        elif isinstance(obj, list):
            for item in obj:
                assert_no_snake_case(item)

    # 1. Check 200 response
    res_200 = client.get("/api/v1/levels", headers=test_ctx["auth_headers"])
    assert res_200.status_code == 200
    assert_no_snake_case(res_200.json())

    # 2. Check 401 response
    res_401 = client.get("/api/v1/levels")
    assert res_401.status_code == 401
    assert_no_snake_case(res_401.json())


def test_levels_no_token_in_logs(
    test_ctx: dict[str, Any],
    caplog: pytest.LogCaptureFixture,
) -> None:
    caplog.set_level(logging.DEBUG)
    client: TestClient = test_ctx["client"]
    token: str = test_ctx["token"]

    res = client.get("/api/v1/levels", headers=test_ctx["auth_headers"])
    assert res.status_code == 200
    assert token not in res.text

    # Verify no log record contains the raw token
    for record in caplog.records:
        assert token not in record.getMessage()


def test_openapi_documents_levels(test_ctx: dict[str, Any]) -> None:
    app: FastAPI = test_ctx["app"]
    schema = app.openapi()
    paths = schema.get("paths", {})
    assert "/api/v1/levels" in paths
    get_spec = paths["/api/v1/levels"].get("get")
    assert get_spec is not None
    assert get_spec.get("tags") == ["levels"]
    assert get_spec.get("summary") == "List all levels with their status"
    assert "security" in get_spec
    responses = get_spec.get("responses", {})
    assert "200" in responses
    assert "401" in responses


def test_main_app_mounts_levels_route() -> None:
    from app.main import app as main_app

    paths = main_app.openapi().get("paths", {})
    assert "/api/v1/levels" in paths
