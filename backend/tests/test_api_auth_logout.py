"""Contract and API integration tests for POST /api/v1/auth/logout (contract 7.2).

Zero database dependency: Local FakeSession, FakeUser, and FakeDeviceTokenRepository.
Strictly follows Table G of Sprint 3 Prompt B.
"""
from __future__ import annotations

import logging
import uuid
from datetime import datetime, timezone
from typing import Any

import jwt as pyjwt
import pytest
from app.api import auth
from app.core.errors import register_handlers
from app.database.session import get_db
from app.services import logout_service
from fastapi import FastAPI
from fastapi.testclient import TestClient

from tests.conftest import assert_envelope, assert_error

FIXED_SECRET = "x" * 40


class FakeUser:
    """Double for User model for authentication dependency testing."""

    def __init__(
        self,
        user_id: uuid.UUID | None = None,
        username: str = "testuser",
        email: str = "test@example.com",
        password_changed_at: datetime | None = None,
    ) -> None:
        self.id = user_id or uuid.uuid4()
        self.username = username
        self.email = email
        self.password_changed_at = password_changed_at


class FakeSession:
    """Double for SQLAlchemy Session tracking commit/rollback counts and entity lookups."""

    def __init__(
        self,
        users: dict[uuid.UUID, FakeUser] | None = None,
        error_on_commit: Exception | None = None,
    ) -> None:
        self.users = users or {}
        self.commit_count = 0
        self.rollback_count = 0
        self.error_on_commit = error_on_commit

    def commit(self) -> None:
        self.commit_count += 1
        if self.error_on_commit is not None:
            raise self.error_on_commit

    def rollback(self) -> None:
        self.rollback_count += 1

    def get(self, model: Any, pk: Any) -> Any:
        return self.users.get(pk)


class FakeDeviceTokenRepository:
    """Double for DeviceTokenRepository tracking tokens and calls."""

    def __init__(
        self,
        tokens: list[tuple[uuid.UUID, str]] | None = None,
        error_on_delete: Exception | None = None,
    ) -> None:
        self.tokens: list[tuple[uuid.UUID, str]] = list(tokens or [])
        self.delete_calls: list[tuple[uuid.UUID, str]] = []
        self.error_on_delete = error_on_delete

    def delete_by_user_and_token(self, user_id: uuid.UUID, fcm_token: str) -> None:
        self.delete_calls.append((user_id, fcm_token))
        if self.error_on_delete is not None:
            raise self.error_on_delete
        self.tokens = [t for t in self.tokens if not (t[0] == user_id and t[1] == fcm_token)]


def make_token(
    user_id: uuid.UUID,
    exp_delta_seconds: int = 604800,
    iat_timestamp: int | None = None,
    secret: str = FIXED_SECRET,
) -> str:
    """Generate test JWT token signed with FIXED_SECRET."""
    now_ts = iat_timestamp if iat_timestamp is not None else int(datetime.now(timezone.utc).timestamp())
    payload = {
        "sub": str(user_id),
        "iat": now_ts,
        "exp": now_ts + exp_delta_seconds,
    }
    return pyjwt.encode(payload, secret, algorithm="HS256")


@pytest.fixture
def test_ctx(monkeypatch: pytest.MonkeyPatch):
    """Build isolated FastAPI app and TestClient for each test."""
    app = FastAPI()
    register_handlers(app)
    app.include_router(auth.router, prefix="/api/v1")

    session = FakeSession()
    device_repo = FakeDeviceTokenRepository()

    monkeypatch.setattr(logout_service, "DeviceTokenRepository", lambda s: device_repo)
    monkeypatch.setattr("app.core.jwt._get_secret", lambda: FIXED_SECRET)

    def override_get_db():
        yield session

    app.dependency_overrides[get_db] = override_get_db
    client = TestClient(app, raise_server_exceptions=False)

    ctx = {
        "app": app,
        "client": client,
        "session": session,
        "repo": device_repo,
    }
    yield ctx
    app.dependency_overrides.clear()


# ============================================================================
# Test Cases theo Ma Trận G
# ============================================================================


def test_logout_without_bearer_returns_401_unauthorized(test_ctx):
    """POST /auth/logout without Bearer token returns 401 unauthorized."""
    client: TestClient = test_ctx["client"]
    resp = client.post("/api/v1/auth/logout")
    assert resp.status_code == 401
    assert_error(resp.json(), "unauthorized")


def test_logout_with_token_returns_200_null_data(test_ctx):
    """POST /auth/logout with valid token returns 200 OK, message='Logged out', data=None."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    repo: FakeDeviceTokenRepository = test_ctx["repo"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id=user_id)
    token = make_token(user_id)

    fcm_token = "valid_fcm_token_123"
    repo.tokens.append((user_id, fcm_token))

    resp = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": fcm_token},
    )

    assert resp.status_code == 200
    data = resp.json()
    assert_envelope(data, ok=True)
    assert data["message"] == "Logged out"
    assert data["data"] is None
    assert data["error"] is None
    assert (user_id, fcm_token) not in repo.tokens
    assert session.commit_count == 1


def test_logout_deletes_only_callers_row(test_ctx):
    """Logout only deletes the row belonging to the caller; other users' tokens remain intact."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    repo: FakeDeviceTokenRepository = test_ctx["repo"]

    user1_id = uuid.uuid4()
    user2_id = uuid.uuid4()
    session.users[user1_id] = FakeUser(user1_id)
    session.users[user2_id] = FakeUser(user2_id)

    token1 = make_token(user1_id)
    fcm1 = "fcm_token_user_1"
    fcm2 = "fcm_token_user_2"
    repo.tokens.extend([(user1_id, fcm1), (user2_id, fcm2)])

    resp = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token1}"},
        json={"fcmToken": fcm1},
    )

    assert resp.status_code == 200
    assert (user1_id, fcm1) not in repo.tokens
    assert (user2_id, fcm2) in repo.tokens


def test_logout_twice_returns_200(test_ctx):
    """Logout is idempotent; second invocation returns 200 without error."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    repo: FakeDeviceTokenRepository = test_ctx["repo"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)
    token = make_token(user_id)
    fcm_token = "fcm_repeat_token"
    repo.tokens.append((user_id, fcm_token))

    # First call
    r1 = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": fcm_token},
    )
    assert r1.status_code == 200

    # Second call
    r2 = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": fcm_token},
    )
    assert r2.status_code == 200
    assert r2.json()["data"] is None


def test_logout_without_body_returns_200(test_ctx):
    """POST /auth/logout with no request body succeeds with 200 without deleting anything."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    repo: FakeDeviceTokenRepository = test_ctx["repo"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)
    token = make_token(user_id)
    repo.tokens.append((user_id, "untouched_token"))

    resp = client.post("/api/v1/auth/logout", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    assert resp.json()["data"] is None
    assert len(repo.delete_calls) == 0
    assert session.commit_count == 0


def test_logout_with_empty_object_returns_200(test_ctx):
    """POST /auth/logout with empty JSON object {} returns 200."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    repo: FakeDeviceTokenRepository = test_ctx["repo"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)
    token = make_token(user_id)

    resp = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={},
    )
    assert resp.status_code == 200
    assert resp.json()["data"] is None
    assert len(repo.delete_calls) == 0


def test_logout_with_null_fcm_token_returns_200(test_ctx):
    """POST /auth/logout with {'fcmToken': null} returns 200."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    repo: FakeDeviceTokenRepository = test_ctx["repo"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)
    token = make_token(user_id)

    resp = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": None},
    )
    assert resp.status_code == 200
    assert resp.json()["data"] is None
    assert len(repo.delete_calls) == 0


def test_logout_with_empty_string_returns_422(test_ctx):
    """Empty string fcmToken returns 422 validation-error with field='fcmToken'."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)
    token = make_token(user_id)

    resp = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": ""},
    )
    assert resp.status_code == 422
    data = resp.json()
    assert_error(data, "validation-error")
    assert any(e["field"] == "fcmToken" for e in data["error"]["errors"])


def test_logout_with_whitespace_only_returns_422(test_ctx):
    """Whitespace-only fcmToken returns 422 validation-error due to strip_whitespace=True."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)
    token = make_token(user_id)

    resp = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": "   "},
    )
    assert resp.status_code == 422
    assert_error(resp.json(), "validation-error")


def test_logout_with_oversized_token_returns_422(test_ctx):
    """Token with 4097 characters returns 422; token with 4096 characters returns 200."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)
    token = make_token(user_id)

    # 4097 chars -> 422
    resp_over = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": "x" * 4097},
    )
    assert resp_over.status_code == 422
    assert_error(resp_over.json(), "validation-error")

    # 4096 chars -> 200
    resp_max = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": "x" * 4096},
    )
    assert resp_max.status_code == 200


def test_logout_with_non_string_token_returns_422(test_ctx):
    """Non-string token (number) returns 422 validation-error."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)
    token = make_token(user_id)

    resp = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": 123},
    )
    assert resp.status_code == 422
    assert_error(resp.json(), "validation-error")


def test_logout_with_invalid_json_returns_422(test_ctx):
    """Malformed non-JSON request body returns 422 validation-error."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)
    token = make_token(user_id)

    resp = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        content="not valid json",
    )
    assert resp.status_code == 422
    assert_error(resp.json(), "validation-error")


def test_logout_other_users_token_returns_200_and_keeps_row(test_ctx):
    """Attempting to unregister another user's token returns 200 (idempotent, anti-BOLA) and preserves row."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    repo: FakeDeviceTokenRepository = test_ctx["repo"]

    caller_id = uuid.uuid4()
    victim_id = uuid.uuid4()
    session.users[caller_id] = FakeUser(caller_id)
    session.users[victim_id] = FakeUser(victim_id)

    token = make_token(caller_id)
    victim_fcm = "victim_secret_fcm_token"
    repo.tokens.append((victim_id, victim_fcm))

    resp = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": victim_fcm},
    )

    assert resp.status_code == 200
    assert resp.json()["data"] is None
    # Victim's token is NOT deleted
    assert (victim_id, victim_fcm) in repo.tokens


def test_logout_response_never_contains_fcm_token(test_ctx):
    """Response text never contains the sensitive fcmToken value even on error."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)
    token = make_token(user_id)
    secret_fcm = "secret_device_fcm_token_super_unique_12345"

    resp = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": secret_fcm},
    )
    assert resp.status_code == 200
    assert secret_fcm not in resp.text


def test_logout_token_issued_before_password_change_returns_401(test_ctx):
    """Token with iat prior to user password_changed_at returns 401 unauthorized."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]

    user_id = uuid.uuid4()
    changed_at = datetime(2026, 10, 14, 12, 0, 0, tzinfo=timezone.utc)
    session.users[user_id] = FakeUser(user_id, password_changed_at=changed_at)

    old_iat = int(changed_at.timestamp()) - 100
    token = make_token(user_id, iat_timestamp=old_iat)

    resp = client.post("/api/v1/auth/logout", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert_error(resp.json(), "unauthorized")


def test_logout_repo_error_returns_500_not_401(test_ctx):
    """Database error during token deletion returns 500 internal-error, not 401."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    repo: FakeDeviceTokenRepository = test_ctx["repo"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)
    token = make_token(user_id)
    repo.error_on_delete = RuntimeError("DB network down")

    resp = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": "valid_token"},
    )
    assert resp.status_code == 500
    assert_error(resp.json(), "internal-error")
    assert session.rollback_count == 1


def test_logout_commit_error_returns_500(test_ctx):
    """Commit error during logout returns 500 internal-error and triggers rollback."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)
    token = make_token(user_id)
    session.error_on_commit = RuntimeError("Commit failure")

    resp = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": "valid_token"},
    )
    assert resp.status_code == 500
    assert_error(resp.json(), "internal-error")
    assert session.rollback_count == 1


def test_logout_error_trace_id_matches_request_id_header(test_ctx):
    """X-Request-Id header matches error.traceId in 401 and 422 responses."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]

    # 401 unauthorized
    r401 = client.post("/api/v1/auth/logout")
    assert r401.status_code == 401
    assert r401.headers["X-Request-Id"] == r401.json()["error"]["traceId"]

    # 422 validation-error
    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)
    token = make_token(user_id)
    r422 = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": ""},
    )
    assert r422.status_code == 422
    assert r422.headers["X-Request-Id"] == r422.json()["error"]["traceId"]


def _assert_no_snake_case_keys(obj: Any) -> None:
    if isinstance(obj, dict):
        for k, v in obj.items():
            assert "_" not in k, f"Found snake_case key '{k}' in JSON response"
            _assert_no_snake_case_keys(v)
    elif isinstance(obj, list):
        for item in obj:
            _assert_no_snake_case_keys(item)


def test_logout_responses_have_no_snake_case_keys(test_ctx):
    """Response keys recursively conform to camelCase without snake_case."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)
    token = make_token(user_id)

    # 200 response
    r200 = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": "valid_token"},
    )
    assert r200.status_code == 200
    _assert_no_snake_case_keys(r200.json())

    # 422 response
    r422 = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {token}"},
        json={"fcmToken": ""},
    )
    assert r422.status_code == 422
    _assert_no_snake_case_keys(r422.json())


def test_logout_no_token_in_logs(test_ctx, caplog: pytest.LogCaptureFixture):
    """DEBUG logging during logout does not leak the bearer token or fcmToken."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)
    token = make_token(user_id)
    fcm_token = "secret_fcm_for_log_test"

    with caplog.at_level(logging.DEBUG):
        resp = client.post(
            "/api/v1/auth/logout",
            headers={"Authorization": f"Bearer {token}"},
            json={"fcmToken": fcm_token},
        )
    assert resp.status_code == 200
    log_text = caplog.text
    assert token not in log_text
    assert fcm_token not in log_text


def test_openapi_documents_logout(test_ctx):
    """OpenAPI schema correctly specifies POST /api/v1/auth/logout with tags, summary, security."""
    app: FastAPI = test_ctx["app"]
    schema = app.openapi()

    paths = schema.get("paths", {})
    assert "/api/v1/auth/logout" in paths

    logout_op = paths["/api/v1/auth/logout"]["post"]
    assert "auth" in logout_op["tags"]
    assert logout_op["summary"] == "Log out and unregister the device token"
    assert "200" in logout_op["responses"]
    assert "401" in logout_op["responses"]
    assert "422" in logout_op["responses"]
    assert "security" in logout_op
    assert any("BearerAuth" in sec for sec in logout_op["security"])


def test_logout_with_invalid_or_expired_token_returns_401_unauthorized(test_ctx):
    """Wrong signature and expired JWT token both return 401 unauthorized."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]

    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id)

    # 1. Wrong signature
    wrong_sig_token = make_token(user_id, secret="y" * 40)
    resp_sig = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {wrong_sig_token}"},
    )
    assert resp_sig.status_code == 401
    assert_error(resp_sig.json(), "unauthorized")

    # 2. Expired token
    expired_token = make_token(user_id, exp_delta_seconds=-100)
    resp_exp = client.post(
        "/api/v1/auth/logout",
        headers={"Authorization": f"Bearer {expired_token}"},
    )
    assert resp_exp.status_code == 401
    assert_error(resp_exp.json(), "unauthorized")
