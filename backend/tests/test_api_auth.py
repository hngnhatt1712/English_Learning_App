"""Unit and contract tests for Auth API endpoints (contract 7.2).

Endpoints:
- POST /api/v1/auth/register
- POST /api/v1/auth/login
- GET /api/v1/auth/me

Zero database dependency: Local FakeSession and FakeUserRepository.
Strictly follows Section G of Sprint 3 Prompt A.
"""
from __future__ import annotations

import logging
import uuid
from datetime import date, datetime, timedelta, timezone
from typing import Any

import jwt as pyjwt
import pytest
from app.api import auth
from app.core.errors import register_handlers
from app.core.security import hash_password
from app.database.session import get_db
from app.services import auth_service
from fastapi import FastAPI
from fastapi.testclient import TestClient
from sqlalchemy.exc import IntegrityError, OperationalError

from tests.conftest import assert_envelope, assert_error

FIXED_SECRET = "x" * 40
TEST_DATE = date(2026, 10, 13)
PRECOMPUTED_VALID_HASH = hash_password("ValidPassword123")


class FakeUser:
    """Double for User model for testing without database."""

    def __init__(
        self,
        user_id: uuid.UUID | None = None,
        username: str = "testuser",
        email: str = "test@example.com",
        password_hash: str = "$argon2id$v=19$m=65536,t=3,p=4$fakehash",
        selected_level_id: uuid.UUID | None = None,
        current_streak: int = 0,
        last_learning_date: date | None = None,
        created_at: datetime | None = None,
        password_changed_at: datetime | None = None,
    ) -> None:
        self.id = user_id or uuid.uuid4()
        self.username = username
        self.email = email
        self.password_hash = password_hash
        self.selected_level_id = selected_level_id
        self.current_streak = current_streak
        self.last_learning_date = last_learning_date
        self.created_at = created_at or datetime(2026, 10, 1, 12, 0, 0, tzinfo=timezone.utc)
        self.password_changed_at = password_changed_at


class FakeSession:
    """Double for SQLAlchemy Session tracking commit/rollback counts."""

    def __init__(
        self,
        users: dict[uuid.UUID, FakeUser] | None = None,
        error_on_commit: Exception | None = None,
        error_on_get: Exception | None = None,
    ) -> None:
        self.users = users or {}
        self.error_on_commit = error_on_commit
        self.error_on_get = error_on_get
        self.commit_count = 0
        self.rollback_count = 0

    def commit(self) -> None:
        self.commit_count += 1
        if self.error_on_commit is not None:
            raise self.error_on_commit

    def rollback(self) -> None:
        self.rollback_count += 1

    def get(self, model: Any, pk: Any) -> Any:
        if self.error_on_get is not None:
            raise self.error_on_get
        return self.users.get(pk)


class FakeUserRepository:
    """Double for UserRepository tracking created users."""

    def __init__(
        self,
        users: list[FakeUser] | None = None,
        error_on_create: Exception | None = None,
    ) -> None:
        self.users = list(users or [])
        self.error_on_create = error_on_create
        self.created_users: list[FakeUser] = []

    def get_by_email(self, email: str) -> FakeUser | None:
        for u in self.users:
            if u.email == email:
                return u
        return None

    def get_by_username(self, username: str) -> FakeUser | None:
        for u in self.users:
            if u.username == username:
                return u
        return None

    def create_user(self, username: str, email: str, password_hash: str) -> FakeUser:
        if self.error_on_create is not None:
            raise self.error_on_create
        user = FakeUser(
            username=username,
            email=email,
            password_hash=password_hash,
        )
        self.users.append(user)
        self.created_users.append(user)
        return user


@pytest.fixture(autouse=True)
def setup_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    """Configure deterministic test environment without reading .env."""
    monkeypatch.setattr("app.core.jwt._get_secret", lambda: FIXED_SECRET)
    monkeypatch.setattr(auth_service, "today_vn", lambda now=None: TEST_DATE)


@pytest.fixture
def test_ctx(monkeypatch: pytest.MonkeyPatch):
    """Build isolated FastAPI app and TestClient for each test."""
    app = FastAPI()
    register_handlers(app)
    app.include_router(auth.router, prefix="/api/v1")

    session = FakeSession()
    repo = FakeUserRepository()

    monkeypatch.setattr(auth_service, "UserRepository", lambda s: repo)

    def override_get_db():
        yield session

    app.dependency_overrides[get_db] = override_get_db
    client = TestClient(app, raise_server_exceptions=False)

    ctx = {
        "app": app,
        "client": client,
        "session": session,
        "repo": repo,
    }
    yield ctx
    app.dependency_overrides.clear()


def make_token(
    user_id: uuid.UUID,
    exp_delta_seconds: int = 604800,
    iat_timestamp: int | None = None,
    secret: str = FIXED_SECRET,
    token_type: str | None = None,
    extra_claims: dict[str, Any] | None = None,
) -> str:
    """Generate test JWT token with optional claims and type override."""
    now_ts = iat_timestamp if iat_timestamp is not None else int(datetime.now(timezone.utc).timestamp())
    payload: dict[str, Any] = {
        **(extra_claims or {}),
        "sub": str(user_id),
        "iat": now_ts,
        "exp": now_ts + exp_delta_seconds,
    }
    if token_type is not None:
        payload["type"] = token_type
    return pyjwt.encode(payload, secret, algorithm="HS256")


# ============================================================================
# Nhóm register
# ============================================================================


def test_register_returns_201_and_envelope(test_ctx):
    """Register returns 201 Created and standard success envelope."""
    client: TestClient = test_ctx["client"]
    body = {
        "username": "johndoe",
        "email": "john@example.com",
        "password": "ValidPassword123",
    }
    resp = client.post("/api/v1/auth/register", json=body)
    assert resp.status_code == 201
    data = resp.json()
    assert_envelope(data, ok=True)
    assert data["message"] == "User registered"


def test_register_auth_result_keys_are_exact(test_ctx):
    """Register response contains exactly the keys defined in AuthResultDto and UserDto."""
    client: TestClient = test_ctx["client"]
    body = {
        "username": "johndoe",
        "email": "john@example.com",
        "password": "ValidPassword123",
    }
    resp = client.post("/api/v1/auth/register", json=body)
    assert resp.status_code == 201
    data = resp.json()["data"]

    assert set(data.keys()) == {"accessToken", "tokenType", "expiresIn", "user"}
    assert data["tokenType"] == "Bearer"
    assert data["expiresIn"] == 604800

    user = data["user"]
    assert set(user.keys()) == {
        "id",
        "username",
        "email",
        "selectedLevelId",
        "currentStreak",
        "lastLearningDate",
        "createdAt",
    }


def test_register_token_matches_user_and_expiry(test_ctx):
    """Decoded accessToken sub matches user id and exp - iat equals expiresIn."""
    client: TestClient = test_ctx["client"]
    body = {
        "username": "johndoe",
        "email": "john@example.com",
        "password": "ValidPassword123",
    }
    resp = client.post("/api/v1/auth/register", json=body)
    assert resp.status_code == 201
    result = resp.json()["data"]

    payload = pyjwt.decode(result["accessToken"], FIXED_SECRET, algorithms=["HS256"], options={"require": ["sub", "exp", "iat"]})
    assert payload["sub"] == result["user"]["id"]
    assert payload["exp"] - payload["iat"] == result["expiresIn"] == 604800


def test_register_normalizes_email_and_username(test_ctx):
    """Email and username are lowercased and stripped before reaching the repository."""
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]
    body = {
        "username": "Nhat_Dao",
        "email": "Nhat@Example.COM",
        "password": "ValidPassword123",
    }
    resp = client.post("/api/v1/auth/register", json=body)
    assert resp.status_code == 201
    assert len(repo.created_users) == 1
    assert repo.created_users[0].username == "nhat_dao"
    assert repo.created_users[0].email == "nhat@example.com"


def test_register_hashes_password(test_ctx):
    """Password stored in repository is hashed with Argon2id and never stored plaintext."""
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]
    body = {
        "username": "johndoe",
        "email": "john@example.com",
        "password": "ValidPassword123",
    }
    resp = client.post("/api/v1/auth/register", json=body)
    assert resp.status_code == 201
    hashed = repo.created_users[0].password_hash
    assert hashed.startswith("$argon2id$")
    assert hashed != "ValidPassword123"


def test_register_email_taken_returns_409(test_ctx):
    """Registering with an existing email returns 409 email-taken."""
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]
    existing = FakeUser(email="taken@example.com", username="firstuser")
    repo.users.append(existing)

    body = {
        "username": "newuser",
        "email": "TAKEN@example.com",
        "password": "ValidPassword123",
    }
    resp = client.post("/api/v1/auth/register", json=body)
    assert resp.status_code == 409
    assert_error(resp.json(), "email-taken")


def test_register_username_taken_returns_409(test_ctx):
    """Registering with an existing username (case-insensitive) returns 409 username-taken."""
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]
    existing = FakeUser(email="first@example.com", username="john_doe")
    repo.users.append(existing)

    body = {
        "username": "JOHN_DOE",
        "email": "second@example.com",
        "password": "ValidPassword123",
    }
    resp = client.post("/api/v1/auth/register", json=body)
    assert resp.status_code == 409
    assert_error(resp.json(), "username-taken")


def test_register_integrity_error_on_create_returns_409(test_ctx):
    """IntegrityError during create_user resolves race condition to 409 and triggers rollback."""
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]
    session: FakeSession = test_ctx["session"]

    def mock_create_user(username: str, email: str, password_hash: str) -> FakeUser:
        conflict = FakeUser(username="other_u", email=email, password_hash=password_hash)
        repo.users.append(conflict)
        raise IntegrityError("create duplicate", params=None, orig=Exception("unique constraint"))

    repo.create_user = mock_create_user

    body = {
        "username": "newuser",
        "email": "race@example.com",
        "password": "ValidPassword123",
    }
    resp = client.post("/api/v1/auth/register", json=body)
    assert resp.status_code == 409
    assert_error(resp.json(), "email-taken")
    assert session.rollback_count >= 1


def test_register_integrity_error_on_commit_returns_409(test_ctx):
    """IntegrityError during session.commit resolves race condition and triggers rollback."""
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]
    session: FakeSession = test_ctx["session"]

    session.error_on_commit = IntegrityError("commit failed", params=None, orig=Exception("unique constraint"))

    def mock_create_user(username: str, email: str, password_hash: str) -> FakeUser:
        created = FakeUser(username=username, email=email, password_hash=password_hash)
        conflict = FakeUser(username=username, email="diff@example.com", password_hash=password_hash)
        repo.users.append(conflict)
        return created

    repo.create_user = mock_create_user

    body = {
        "username": "taken_user",
        "email": "unique@example.com",
        "password": "ValidPassword123",
    }
    resp = client.post("/api/v1/auth/register", json=body)
    assert resp.status_code == 409
    assert_error(resp.json(), "username-taken")
    assert session.rollback_count >= 1


def test_register_unknown_integrity_error_returns_500_envelope(test_ctx):
    """Unrecognized IntegrityError not finding duplicates re-raises as 500 internal-error."""
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]
    session: FakeSession = test_ctx["session"]

    session.error_on_commit = IntegrityError("unknown constraint", params=None, orig=Exception("foreign key"))

    def mock_create_user(username: str, email: str, password_hash: str) -> FakeUser:
        # Return user without adding to repo so lookup finds nothing
        return FakeUser(username=username, email=email, password_hash=password_hash)

    repo.create_user = mock_create_user

    body = {
        "username": "freshuser",
        "email": "fresh@example.com",
        "password": "ValidPassword123",
    }
    resp = client.post("/api/v1/auth/register", json=body)
    assert resp.status_code == 500
    assert_error(resp.json(), "internal-error")
    assert session.rollback_count >= 1


@pytest.mark.parametrize(
    ("payload", "expected_field", "expected_code"),
    [
        ({"email": "a@b.com", "password": "Password1"}, "username", "REQUIRED"),
        ({"username": "valid", "password": "Password1"}, "email", "REQUIRED"),
        ({"username": "valid", "email": "a@b.com"}, "password", "REQUIRED"),
        ({"username": "valid", "email": "a@b.com", "password": "Pass1"}, "password", "MIN_LENGTH"),
        ({"username": "valid", "email": "a@b.com", "password": "A1" + "a" * 63}, "password", "MAX_LENGTH"),
        ({"username": "valid", "email": "a@b.com", "password": "PasswordOnly"}, "password", "INVALID_FORMAT"),
        ({"username": "valid", "email": "a@b.com", "password": "12345678"}, "password", "INVALID_FORMAT"),
        ({"username": "valid", "email": "notanemail", "password": "Password1"}, "email", "INVALID_FORMAT"),
        ({"username": "valid", "email": "a b@c.com", "password": "Password1"}, "email", "INVALID_FORMAT"),
        ({"username": "user@name", "email": "a@b.com", "password": "Password1"}, "username", "INVALID_FORMAT"),
        ({"username": "user!name", "email": "a@b.com", "password": "Password1"}, "username", "INVALID_FORMAT"),
        ({"username": "ab", "email": "a@b.com", "password": "Password1"}, "username", "MIN_LENGTH"),
        ({"username": "a" * 31, "email": "a@b.com", "password": "Password1"}, "username", "MAX_LENGTH"),
    ],
)
def test_register_validation_errors(test_ctx, payload, expected_field, expected_code):
    """Validation errors return 422 with proper error codes and fields."""
    client: TestClient = test_ctx["client"]
    resp = client.post("/api/v1/auth/register", json=payload)
    assert resp.status_code == 422
    data = resp.json()
    assert_error(data, "validation-error")
    errors = data["error"]["errors"]
    assert isinstance(errors, list) and len(errors) > 0

    fields = [e["field"] for e in errors]
    codes = [e["code"] for e in errors]
    assert expected_field in fields
    assert expected_code in codes
    for err in errors:
        assert not err["field"].startswith("body.")


def test_register_invalid_json_returns_422(test_ctx):
    """Malformed non-JSON request body returns 422 validation-error."""
    client: TestClient = test_ctx["client"]
    resp = client.post(
        "/api/v1/auth/register",
        content="this is not a json",
        headers={"Content-Type": "application/json"},
    )
    assert resp.status_code == 422
    assert_error(resp.json(), "validation-error")


def test_register_422_does_not_echo_password(test_ctx):
    """Validation error response never contains the plaintext password submitted."""
    client: TestClient = test_ctx["client"]
    secret_pass = "SuperSecretPassword123"
    body = {
        "username": "u",  # invalid username (too short)
        "email": "test@example.com",
        "password": secret_pass,
    }
    resp = client.post("/api/v1/auth/register", json=body)
    assert resp.status_code == 422
    assert secret_pass not in resp.text


def test_register_is_public(test_ctx):
    """Register endpoint does not require Authorization header."""
    client: TestClient = test_ctx["client"]
    body = {
        "username": "publicuser",
        "email": "public@example.com",
        "password": "ValidPassword123",
    }
    resp = client.post("/api/v1/auth/register", json=body)
    assert resp.status_code == 201


# ============================================================================
# Nhóm login
# ============================================================================


def test_login_with_email_returns_200(test_ctx):
    """Login with email returns 200 OK and AuthResultDto."""
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]
    raw_pw = "ValidPassword123"
    user = FakeUser(
        username="john_doe",
        email="john@example.com",
        password_hash=PRECOMPUTED_VALID_HASH,
    )
    repo.users.append(user)

    body = {"identifier": "john@example.com", "password": raw_pw}
    resp = client.post("/api/v1/auth/login", json=body)
    assert resp.status_code == 200
    data = resp.json()
    assert_envelope(data, ok=True)
    assert data["message"] == "Login successful"
    assert set(data["data"].keys()) == {"accessToken", "tokenType", "expiresIn", "user"}


def test_login_with_username_returns_200(test_ctx):
    """Login with username (no @) returns 200 OK."""
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]
    raw_pw = "ValidPassword123"
    user = FakeUser(
        username="john_doe",
        email="john@example.com",
        password_hash=PRECOMPUTED_VALID_HASH,
    )
    repo.users.append(user)

    body = {"identifier": "john_doe", "password": raw_pw}
    resp = client.post("/api/v1/auth/login", json=body)
    assert resp.status_code == 200
    assert resp.json()["message"] == "Login successful"


def test_login_identifier_is_case_insensitive(test_ctx):
    """Login identifier is case-insensitive for both email and username."""
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]
    raw_pw = "ValidPassword123"
    user = FakeUser(
        username="john_doe",
        email="john@example.com",
        password_hash=PRECOMPUTED_VALID_HASH,
    )
    repo.users.append(user)

    resp_email = client.post("/api/v1/auth/login", json={"identifier": "JOHN@EXAMPLE.COM", "password": raw_pw})
    assert resp_email.status_code == 200

    resp_user = client.post("/api/v1/auth/login", json={"identifier": "JOHN_DOE", "password": raw_pw})
    assert resp_user.status_code == 200


def test_login_wrong_password_returns_401_invalid_credentials(test_ctx):
    """Incorrect password returns 401 invalid-credentials."""
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]
    user = FakeUser(
        username="john_doe",
        email="john@example.com",
        password_hash=PRECOMPUTED_VALID_HASH,
    )
    repo.users.append(user)

    body = {"identifier": "john_doe", "password": "WrongPassword123"}
    resp = client.post("/api/v1/auth/login", json=body)
    assert resp.status_code == 401
    assert_error(resp.json(), "invalid-credentials")
    assert resp.json()["error"]["detail"] == "Invalid credentials"


def test_login_unknown_identifier_returns_identical_401(test_ctx):
    """Unknown identifier produces identical 401 response as incorrect password."""
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]
    user = FakeUser(
        username="john_doe",
        email="john@example.com",
        password_hash=PRECOMPUTED_VALID_HASH,
    )
    repo.users.append(user)

    resp_wrong_pw = client.post("/api/v1/auth/login", json={"identifier": "john_doe", "password": "WrongPassword123"})
    resp_unknown_user = client.post("/api/v1/auth/login", json={"identifier": "unknown_user", "password": "AnyPassword123"})

    assert resp_wrong_pw.status_code == resp_unknown_user.status_code == 401
    assert resp_wrong_pw.json()["error"]["type"] == resp_unknown_user.json()["error"]["type"] == "invalid-credentials"
    assert resp_wrong_pw.json()["error"]["detail"] == resp_unknown_user.json()["error"]["detail"] == "Invalid credentials"
    assert resp_wrong_pw.json()["message"] == resp_unknown_user.json()["message"]


def test_login_unknown_identifier_resistance_or_behavior(test_ctx, monkeypatch):
    """Unknown identifier executes dummy verification to maintain constant-time resistance.

    Verifies that auth_service.verify_password is called exactly once when identifier is unknown,
    matching the call count when an existing user provides an invalid password.
    Both return 401 invalid-credentials without leaking user existence.
    """
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]
    existing_user = FakeUser(
        username="existing_user",
        email="existing@example.com",
        password_hash=PRECOMPUTED_VALID_HASH,
    )
    repo.users.append(existing_user)

    call_records: list[tuple[str, str]] = []
    orig_verify = auth_service.verify_password

    def spy_verify(password: str, hashed: str) -> bool:
        call_records.append((password, hashed))
        return orig_verify(password, hashed)

    monkeypatch.setattr(auth_service, "verify_password", spy_verify)

    # 1. Unknown identifier -> executes dummy verify
    resp_unknown = client.post(
        "/api/v1/auth/login",
        json={"identifier": "nonexistent_user", "password": "AnyPassword123"},
    )
    assert resp_unknown.status_code == 401
    assert_error(resp_unknown.json(), "invalid-credentials")
    assert len(call_records) == 1
    assert call_records[0][0] == "dummy_password_for_timing_safety"

    # Reset calls
    call_records.clear()

    # 2. Existing user with wrong password -> executes normal verify
    resp_wrong = client.post(
        "/api/v1/auth/login",
        json={"identifier": "existing_user", "password": "WrongPassword123"},
    )
    assert resp_wrong.status_code == 401
    assert_error(resp_wrong.json(), "invalid-credentials")
    assert len(call_records) == 1
    assert call_records[0][0] == "WrongPassword123"
    assert call_records[0][1] == PRECOMPUTED_VALID_HASH


def test_login_invalid_credentials_is_not_unauthorized(test_ctx):
    """Login failures must never return unauthorized to prevent Android clearing session."""
    client: TestClient = test_ctx["client"]
    body = {"identifier": "nobody@example.com", "password": "WrongPassword123"}
    resp = client.post("/api/v1/auth/login", json=body)
    assert resp.status_code == 401
    assert resp.json()["error"]["type"] != "unauthorized"
    assert resp.json()["error"]["type"] == "invalid-credentials"


def test_login_missing_fields_returns_422(test_ctx):
    """Missing identifier or password returns 422 REQUIRED."""
    client: TestClient = test_ctx["client"]
    resp1 = client.post("/api/v1/auth/login", json={"password": "Password123"})
    assert resp1.status_code == 422
    assert_error(resp1.json(), "validation-error", code="REQUIRED")

    resp2 = client.post("/api/v1/auth/login", json={"identifier": "user"})
    assert resp2.status_code == 422
    assert_error(resp2.json(), "validation-error", code="REQUIRED")


def test_login_blank_identifier_is_never_5xx(test_ctx):
    """Blank identifier is rejected with 401 or 422, never a 5xx server error."""
    client: TestClient = test_ctx["client"]
    resp = client.post("/api/v1/auth/login", json={"identifier": "   ", "password": "Password123"})
    assert resp.status_code in (401, 422)
    assert resp.status_code < 500


def test_login_if_schema_caps_password_length(test_ctx):
    """Password exceeding schema limit (64 chars) returns 422 MAX_LENGTH."""
    client: TestClient = test_ctx["client"]
    oversized_password = "P" * 65
    body = {"identifier": "john_doe", "password": oversized_password}
    resp = client.post("/api/v1/auth/login", json=body)
    assert resp.status_code == 422
    assert_error(resp.json(), "validation-error", code="MAX_LENGTH")


def test_login_does_not_commit(test_ctx):
    """Login must not execute database commits."""
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]
    session: FakeSession = test_ctx["session"]
    raw_pw = "ValidPassword123"
    user = FakeUser(
        username="john_doe",
        email="john@example.com",
        password_hash=PRECOMPUTED_VALID_HASH,
    )
    repo.users.append(user)

    resp = client.post("/api/v1/auth/login", json={"identifier": "john_doe", "password": raw_pw})
    assert resp.status_code == 200
    assert session.commit_count == 0


def test_login_is_public(test_ctx):
    """Login endpoint does not require Authorization header."""
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]
    raw_pw = "ValidPassword123"
    user = FakeUser(
        username="john_doe",
        email="john@example.com",
        password_hash=PRECOMPUTED_VALID_HASH,
    )
    repo.users.append(user)

    resp = client.post("/api/v1/auth/login", json={"identifier": "john_doe", "password": raw_pw})
    assert resp.status_code == 200


# ============================================================================
# Nhóm me
# ============================================================================


def test_me_without_token_returns_401_unauthorized(test_ctx):
    """GET /auth/me without Bearer token returns 401 unauthorized."""
    client: TestClient = test_ctx["client"]
    resp = client.get("/api/v1/auth/me")
    assert resp.status_code == 401
    assert_error(resp.json(), "unauthorized")


def test_me_returns_user_dto(test_ctx):
    """GET /auth/me with valid Bearer token returns 200 OK and UserDto."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    user_id = uuid.uuid4()
    user = FakeUser(
        user_id=user_id,
        username="john_doe",
        email="john@example.com",
        current_streak=3,
        last_learning_date=TEST_DATE,
    )
    session.users[user_id] = user

    token = make_token(user_id)
    resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    data = resp.json()
    assert_envelope(data, ok=True)
    assert data["message"] == "OK"

    user_data = data["data"]
    assert set(user_data.keys()) == {
        "id",
        "username",
        "email",
        "selectedLevelId",
        "currentStreak",
        "lastLearningDate",
        "createdAt",
    }
    assert user_data["id"] == str(user_id)
    assert user_data["username"] == "john_doe"
    assert user_data["email"] == "john@example.com"
    assert user_data["currentStreak"] == 3


def test_me_nullable_fields_are_null_not_missing(test_ctx):
    """Nullable fields in UserDto (selectedLevelId, lastLearningDate) are explicit null."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    user_id = uuid.uuid4()
    user = FakeUser(
        user_id=user_id,
        selected_level_id=None,
        last_learning_date=None,
    )
    session.users[user_id] = user

    token = make_token(user_id)
    resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    user_data = resp.json()["data"]
    assert "selectedLevelId" in user_data
    assert user_data["selectedLevelId"] is None
    assert "lastLearningDate" in user_data
    assert user_data["lastLearningDate"] is None


def test_me_streak_is_zero_after_two_idle_days(test_ctx):
    """Read-time streak adjustment resets streak to 0 if last_learning_date is >= 2 days ago."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    user_id = uuid.uuid4()
    # 2 days prior to TEST_DATE (2026-10-13)
    user = FakeUser(
        user_id=user_id,
        current_streak=7,
        last_learning_date=date(2026, 10, 11),
    )
    session.users[user_id] = user

    token = make_token(user_id)
    resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    assert resp.json()["data"]["currentStreak"] == 0


def test_me_created_at_is_utc_z(test_ctx):
    """User createdAt aware of +07:00 is serialized to UTC ending with 'Z'."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    user_id = uuid.uuid4()
    # 14:00 in +07:00 is 07:00 UTC
    tz_plus_7 = timezone(timedelta(hours=7))
    aware_dt = datetime(2026, 10, 13, 14, 0, 0, tzinfo=tz_plus_7)
    user = FakeUser(user_id=user_id, created_at=aware_dt)
    session.users[user_id] = user

    token = make_token(user_id)
    resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    created_at_str = resp.json()["data"]["createdAt"]
    assert created_at_str.endswith("Z")
    assert created_at_str == "2026-10-13T07:00:00Z"


def test_me_never_leaks_sensitive_fields(test_ctx):
    """Sensitive fields (passwordHash, passwordChangedAt) must never appear in response."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    user_id = uuid.uuid4()
    fake_hash = "$argon2id$v=19$m=65536,t=3,p=4$somehashvalue"
    user = FakeUser(
        user_id=user_id,
        password_hash=fake_hash,
        password_changed_at=datetime(2026, 10, 12, 10, 0, 0, tzinfo=timezone.utc),
    )
    session.users[user_id] = user

    token = make_token(user_id)
    resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    text = resp.text
    assert "passwordHash" not in text
    assert "password_hash" not in text
    assert "passwordChangedAt" not in text
    assert "password_changed_at" not in text
    assert fake_hash not in text


def test_me_token_issued_before_password_change_returns_401(test_ctx):
    """Token with iat prior to password_changed_at returns 401 unauthorized."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    user_id = uuid.uuid4()
    change_time = datetime(2026, 10, 13, 10, 0, 0, tzinfo=timezone.utc)
    user = FakeUser(user_id=user_id, password_changed_at=change_time)
    session.users[user_id] = user

    old_iat = int(change_time.timestamp()) - 10
    old_token = make_token(user_id, iat_timestamp=old_iat)
    resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {old_token}"})
    assert resp.status_code == 401
    assert_error(resp.json(), "unauthorized")


def test_me_db_error_is_not_401(test_ctx):
    """Database query failure during session lookup returns 500 internal-error, not 401."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    user_id = uuid.uuid4()
    session.error_on_get = OperationalError("db failure", params=None, orig=Exception("DB dead"))

    token = make_token(user_id)
    resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 500
    assert_error(resp.json(), "internal-error")


def test_me_expired_token_returns_401_unauthorized(test_ctx):
    """Expired JWT token returns 401 unauthorized."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id=user_id)

    token = make_token(user_id, exp_delta_seconds=-60)
    resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert_error(resp.json(), "unauthorized")


def test_me_wrong_signature_token_returns_401_unauthorized(test_ctx):
    """Token signed with untrusted secret returns 401 unauthorized."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id=user_id)

    token = make_token(user_id, secret="y" * 40)
    resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert_error(resp.json(), "unauthorized")


@pytest.mark.parametrize(
    "auth_header",
    [
        "Bearer not.a.jwt",
        "Bearer ",
        "Basic dXNlcjpwYXNz",
        " TokenWithoutBearer",
    ],
)
def test_me_malformed_token_returns_401_unauthorized(test_ctx, auth_header):
    """Malformed tokens or invalid schemes return 401 unauthorized without server error."""
    client: TestClient = test_ctx["client"]
    resp = client.get("/api/v1/auth/me", headers={"Authorization": auth_header})
    assert resp.status_code == 401
    assert_error(resp.json(), "unauthorized")


def test_me_deleted_or_nonexistent_user_returns_401_unauthorized(test_ctx):
    """Token for deleted or non-existent user id in database returns 401 unauthorized."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    unknown_id = uuid.uuid4()
    assert unknown_id not in session.users

    token = make_token(unknown_id)
    resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert_error(resp.json(), "unauthorized")


def test_me_invalid_sub_uuid_in_token_returns_401_unauthorized(test_ctx):
    """Token with non-UUID subject returns 401 unauthorized instead of 500 error."""
    client: TestClient = test_ctx["client"]
    now_ts = int(datetime.now(timezone.utc).timestamp())
    payload = {
        "sub": "not-a-valid-uuid",
        "iat": now_ts,
        "exp": now_ts + 604800,
    }
    token = pyjwt.encode(payload, FIXED_SECRET, algorithm="HS256")

    resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert_error(resp.json(), "unauthorized")


def test_me_wrong_token_type_handling(test_ctx):
    """Token with type claim other than 'access' (e.g. 'refresh') returns 401 unauthorized.

    core/jwt.py decode_access_token explicitly raises TokenError when 'type' != 'access'.
    dependencies.py get_current_user catches jwt.InvalidTokenError and raises 401 UNAUTHORIZED.
    """
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]
    user_id = uuid.uuid4()
    session.users[user_id] = FakeUser(user_id=user_id)

    token = make_token(user_id, token_type="refresh")
    resp = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert_error(resp.json(), "unauthorized")


# ============================================================================
# Nhóm chung
# ============================================================================


def _assert_no_snake_case_keys(obj: Any) -> None:
    if isinstance(obj, dict):
        for k, v in obj.items():
            assert "_" not in k, f"Found snake_case key '{k}' in JSON response"
            _assert_no_snake_case_keys(v)
    elif isinstance(obj, list):
        for item in obj:
            _assert_no_snake_case_keys(item)


def test_no_snake_case_keys_in_json(test_ctx):
    """Recursively checks that no response keys contain snake_case."""
    client: TestClient = test_ctx["client"]
    session: FakeSession = test_ctx["session"]

    # Register success
    r1 = client.post("/api/v1/auth/register", json={"username": "user1", "email": "u1@a.com", "password": "Password1"})
    assert r1.status_code == 201
    _assert_no_snake_case_keys(r1.json())

    # Register error 422
    r2 = client.post("/api/v1/auth/register", json={})
    assert r2.status_code == 422
    _assert_no_snake_case_keys(r2.json())

    # Login success
    r3 = client.post("/api/v1/auth/login", json={"identifier": "user1", "password": "Password1"})
    assert r3.status_code == 200
    _assert_no_snake_case_keys(r3.json())

    # Login error 401
    r4 = client.post("/api/v1/auth/login", json={"identifier": "user1", "password": "WrongPassword"})
    assert r4.status_code == 401
    _assert_no_snake_case_keys(r4.json())

    # Me success
    user_id = uuid.UUID(r1.json()["data"]["user"]["id"])
    session.users[user_id] = FakeUser(user_id=user_id)
    token = r1.json()["data"]["accessToken"]
    r5 = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {token}"})
    assert r5.status_code == 200
    _assert_no_snake_case_keys(r5.json())


def test_error_trace_id_matches_request_id_header(test_ctx):
    """X-Request-Id header matches error.traceId in 401, 409, and 422 responses."""
    client: TestClient = test_ctx["client"]
    repo: FakeUserRepository = test_ctx["repo"]

    # 401
    r401 = client.get("/api/v1/auth/me")
    assert r401.status_code == 401
    assert r401.headers["X-Request-Id"] == r401.json()["error"]["traceId"]

    # 409
    repo.users.append(FakeUser(email="dup@a.com"))
    r409 = client.post("/api/v1/auth/register", json={"username": "newu", "email": "dup@a.com", "password": "Password1"})
    assert r409.status_code == 409
    assert r409.headers["X-Request-Id"] == r409.json()["error"]["traceId"]

    # 422
    r422 = client.post("/api/v1/auth/register", json={})
    assert r422.status_code == 422
    assert r422.headers["X-Request-Id"] == r422.json()["error"]["traceId"]


def test_client_request_id_is_echoed_when_valid(test_ctx):
    """Valid incoming X-Request-Id is echoed in response headers and error traceId."""
    client: TestClient = test_ctx["client"]
    custom_id = "trace-uuid-1234-abcd"
    resp = client.get("/api/v1/auth/me", headers={"X-Request-Id": custom_id})
    assert resp.status_code == 401
    assert resp.headers["X-Request-Id"] == custom_id
    assert resp.json()["error"]["traceId"] == custom_id


def test_openapi_documents_auth_routes(test_ctx):
    """OpenAPI schema contains all 3 auth endpoints with tags, summaries, and correct security."""
    app: FastAPI = test_ctx["app"]
    schema = app.openapi()

    paths = schema.get("paths", {})
    assert "/api/v1/auth/register" in paths
    assert "/api/v1/auth/login" in paths
    assert "/api/v1/auth/me" in paths

    reg_op = paths["/api/v1/auth/register"]["post"]
    assert "auth" in reg_op["tags"]
    assert "summary" in reg_op
    assert "201" in reg_op["responses"]
    assert "security" not in reg_op or reg_op["security"] == []

    login_op = paths["/api/v1/auth/login"]["post"]
    assert "auth" in login_op["tags"]
    assert "summary" in login_op
    assert "200" in login_op["responses"]
    assert "security" not in login_op or login_op["security"] == []

    me_op = paths["/api/v1/auth/me"]["get"]
    assert "auth" in me_op["tags"]
    assert "summary" in me_op
    assert "200" in me_op["responses"]
    assert "security" in me_op
    assert any("BearerAuth" in sec for sec in me_op["security"])


def test_no_secret_or_token_in_logs(test_ctx, caplog: pytest.LogCaptureFixture):
    """DEBUG logging and responses do not leak plain passwords, JWT tokens, or password hashes."""
    client: TestClient = test_ctx["client"]
    plain_pw = "SuperSecretPassword123"
    with caplog.at_level(logging.DEBUG):
        resp = client.post(
            "/api/v1/auth/register",
            json={"username": "logtest", "email": "log@example.com", "password": plain_pw},
        )
    assert resp.status_code == 201
    log_text = caplog.text
    token = resp.json()["data"]["accessToken"]

    assert plain_pw not in log_text
    assert token not in log_text
    assert "$argon2id$" not in log_text


def test_main_app_mounts_auth_routes():
    """Verify that the production app in app.main mounts all auth routes with /api/v1 prefix."""
    from app.main import app as main_app

    openapi_paths = set(main_app.openapi().get("paths", {}).keys())
    assert "/api/v1/auth/register" in openapi_paths
    assert "/api/v1/auth/login" in openapi_paths
    assert "/api/v1/auth/me" in openapi_paths


