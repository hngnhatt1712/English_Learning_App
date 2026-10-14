"""Unit tests for JWT authentication dependency (core/dependencies.py).

Test matrix strictly covers:
- Section G of the sprint prompt
- OWASP API security requirements
- Server-authoritative contract rules (api-contract.md 3.3, 4.1, 10.2)
- Zero database dependency: FakeSession and FakeUser defined locally.
"""
from __future__ import annotations

import logging
import uuid
from datetime import datetime, timezone
from typing import Any

import jwt as pyjwt
import pytest
from app.core.dependencies import CurrentUser
from app.core.errors import register_handlers
from app.core.schemas import ok
from app.database.session import get_db
from fastapi import FastAPI
from fastapi.testclient import TestClient
from sqlalchemy.exc import OperationalError

TEST_SECRET = "super-secret-key-at-least-64-characters-long-strictly-for-all-tests!!"
WRONG_SECRET = "another-secret-key-at-least-64-characters-long-too-for-all-tests!!"


class FakeUser:
    """In-memory User double for dependency testing (no ORM or DB needed)."""

    def __init__(
        self,
        user_id: uuid.UUID | None = None,
        username: str = "test_user",
        email: str = "test@example.com",
        password_changed_at: datetime | None = None,
    ) -> None:
        self.id = user_id or uuid.uuid4()
        self.username = username
        self.email = email
        self.password_changed_at = password_changed_at


class FakeSession:
    """In-memory Session double imitating session.get(User, user_id)."""

    def __init__(
        self,
        users: dict[uuid.UUID, FakeUser] | None = None,
        error_on_get: Exception | None = None,
    ) -> None:
        self.users = users or {}
        self.error_on_get = error_on_get

    def get(self, model: Any, pk: Any) -> Any:
        if self.error_on_get is not None:
            raise self.error_on_get
        return self.users.get(pk)


@pytest.fixture(autouse=True)
def patch_jwt_secret(monkeypatch: pytest.MonkeyPatch) -> None:
    """Ensure tests run against a deterministic test secret, never reading real .env."""
    monkeypatch.setattr("app.core.jwt._get_secret", lambda: TEST_SECRET)


@pytest.fixture
def test_setup():
    """Create test application, register error handlers, and setup TestClient."""
    app = FastAPI()
    register_handlers(app)

    @app.get("/_protected")
    def protected_route(user: CurrentUser):
        return ok({"id": str(user.id)})

    fake_session = FakeSession()

    def override_get_db():
        yield fake_session

    app.dependency_overrides[get_db] = override_get_db
    client = TestClient(app, raise_server_exceptions=False)

    yield {"app": app, "client": client, "session": fake_session}

    app.dependency_overrides.clear()


def make_token(
    sub: Any = None,
    exp: int | datetime | None = None,
    iat: int | datetime | None = None,
    secret: str = TEST_SECRET,
    algorithm: str = "HS256",
    extra: dict[str, Any] | None = None,
    omit_sub: bool = False,
    omit_exp: bool = False,
    omit_iat: bool = False,
) -> str:
    """Generate custom JWT tokens with explicit parameters for testing."""
    payload: dict[str, Any] = dict(extra or {})
    if not omit_sub and sub is not None:
        payload["sub"] = sub
    if not omit_exp:
        payload["exp"] = exp if exp is not None else int(datetime.now(timezone.utc).timestamp()) + 604800
    if not omit_iat:
        payload["iat"] = iat if iat is not None else int(datetime.now(timezone.utc).timestamp())

    return pyjwt.encode(payload, secret, algorithm=algorithm)


def test_missing_header_returns_401_not_403(test_setup):
    """Missing Authorization header must return 401 unauthorized, never 403."""
    client: TestClient = test_setup["client"]
    resp = client.get("/_protected")
    assert resp.status_code == 401
    body = resp.json()
    assert body["status"] is False
    assert body["error"]["type"] == "unauthorized"


def test_wrong_scheme_returns_401(test_setup):
    """Scheme other than Bearer (e.g. Basic) must return 401 unauthorized."""
    client: TestClient = test_setup["client"]
    resp = client.get("/_protected", headers={"Authorization": "Basic dXNlcjpwYXNz"})
    assert resp.status_code == 401
    assert resp.json()["error"]["type"] == "unauthorized"


def test_empty_bearer_returns_401(test_setup):
    """Bearer without token or with only whitespace must return 401 unauthorized."""
    client: TestClient = test_setup["client"]
    resp1 = client.get("/_protected", headers={"Authorization": "Bearer"})
    assert resp1.status_code == 401
    assert resp1.json()["error"]["type"] == "unauthorized"

    resp2 = client.get("/_protected", headers={"Authorization": "Bearer    "})
    assert resp2.status_code == 401
    assert resp2.json()["error"]["type"] == "unauthorized"


def test_oversized_token_returns_401(test_setup):
    """Token longer than 4096 characters is rejected before decoding to limit compute cost."""
    client: TestClient = test_setup["client"]
    oversized = "a" * 4097
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {oversized}"})
    assert resp.status_code == 401
    assert resp.json()["error"]["type"] == "unauthorized"


def test_garbage_token_returns_401(test_setup):
    """Malformed token string must return 401 unauthorized."""
    client: TestClient = test_setup["client"]
    resp = client.get("/_protected", headers={"Authorization": "Bearer abc.def.ghi"})
    assert resp.status_code == 401
    assert resp.json()["error"]["type"] == "unauthorized"


def test_wrong_secret_returns_401(test_setup):
    """Token signed with a different secret must return 401 unauthorized."""
    client: TestClient = test_setup["client"]
    token = make_token(sub=str(uuid.uuid4()), secret=WRONG_SECRET)
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert resp.json()["error"]["type"] == "unauthorized"


def test_alg_none_returns_401(test_setup):
    """Token with alg=none must be rejected by HS256 whitelist."""
    client: TestClient = test_setup["client"]
    # PyJWT decode with algorithms=["HS256"] rejects algorithm 'none'
    token = pyjwt.encode({"sub": str(uuid.uuid4()), "exp": 2000000000, "iat": 1000000000}, key="", algorithm="none")
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert resp.json()["error"]["type"] == "unauthorized"


def test_wrong_algorithm_returns_401(test_setup):
    """Token signed with HS512 using the correct secret must be rejected by HS256 whitelist."""
    client: TestClient = test_setup["client"]
    token = make_token(sub=str(uuid.uuid4()), algorithm="HS512")
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert resp.json()["error"]["type"] == "unauthorized"


def test_expired_token_returns_401(test_setup):
    """Token with exp in the past must return 401 unauthorized."""
    client: TestClient = test_setup["client"]
    token = make_token(sub=str(uuid.uuid4()), exp=1000000000, iat=999999000)
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert resp.json()["error"]["type"] == "unauthorized"


def test_missing_sub_returns_401(test_setup):
    """Token missing required sub claim must return 401 unauthorized."""
    client: TestClient = test_setup["client"]
    token = make_token(omit_sub=True)
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert resp.json()["error"]["type"] == "unauthorized"


def test_missing_exp_returns_401(test_setup):
    """Token missing required exp claim must return 401 unauthorized."""
    client: TestClient = test_setup["client"]
    token = make_token(sub=str(uuid.uuid4()), omit_exp=True)
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert resp.json()["error"]["type"] == "unauthorized"


def test_missing_iat_returns_401(test_setup):
    """Token missing required iat claim must return 401 unauthorized."""
    client: TestClient = test_setup["client"]
    token = make_token(sub=str(uuid.uuid4()), omit_iat=True)
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert resp.json()["error"]["type"] == "unauthorized"


def test_non_uuid_sub_returns_401(test_setup):
    """Token with sub not parsable as UUID must return 401 unauthorized."""
    client: TestClient = test_setup["client"]
    token = make_token(sub="not-a-valid-uuid")
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert resp.json()["error"]["type"] == "unauthorized"


def test_non_string_sub_returns_401(test_setup):
    """Token with non-string sub (e.g. integer 12345) must return 401 unauthorized."""
    client: TestClient = test_setup["client"]
    token = make_token(sub=12345)
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert resp.json()["error"]["type"] == "unauthorized"


def test_unknown_user_returns_401(test_setup):
    """Valid UUID sub whose user does not exist in the database must return 401 unauthorized."""
    client: TestClient = test_setup["client"]
    unknown_id = uuid.uuid4()
    token = make_token(sub=str(unknown_id))
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert resp.json()["error"]["type"] == "unauthorized"


def test_token_older_than_password_change_returns_401(test_setup):
    """Token with iat < password_changed_at must be invalidated and return 401 unauthorized."""
    client: TestClient = test_setup["client"]
    session: FakeSession = test_setup["session"]
    user_id = uuid.uuid4()
    changed_at = datetime(2026, 10, 12, 10, 0, 0, tzinfo=timezone.utc)
    user = FakeUser(user_id=user_id, password_changed_at=changed_at)
    session.users[user_id] = user

    iat = int(changed_at.timestamp()) - 10
    token = make_token(sub=str(user_id), iat=iat)
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 401
    assert resp.json()["error"]["type"] == "unauthorized"


def test_token_same_second_as_password_change_is_valid(test_setup):
    """Token with iat == password_changed_at (same second) is valid and returns 200 OK."""
    client: TestClient = test_setup["client"]
    session: FakeSession = test_setup["session"]
    user_id = uuid.uuid4()
    changed_at = datetime(2026, 10, 12, 10, 0, 0, tzinfo=timezone.utc)
    user = FakeUser(user_id=user_id, password_changed_at=changed_at)
    session.users[user_id] = user

    iat = int(changed_at.timestamp())
    token = make_token(sub=str(user_id), iat=iat)
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    assert resp.json()["data"]["id"] == str(user_id)


def test_token_newer_than_password_change_is_valid(test_setup):
    """Token with iat > password_changed_at is valid and returns 200 OK."""
    client: TestClient = test_setup["client"]
    session: FakeSession = test_setup["session"]
    user_id = uuid.uuid4()
    changed_at = datetime(2026, 10, 12, 10, 0, 0, tzinfo=timezone.utc)
    user = FakeUser(user_id=user_id, password_changed_at=changed_at)
    session.users[user_id] = user

    iat = int(changed_at.timestamp()) + 10
    token = make_token(sub=str(user_id), iat=iat)
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    assert resp.json()["data"]["id"] == str(user_id)


def test_password_changed_at_none_is_valid(test_setup):
    """User with password_changed_at is None accepts any valid token and returns 200 OK."""
    client: TestClient = test_setup["client"]
    session: FakeSession = test_setup["session"]
    user_id = uuid.uuid4()
    user = FakeUser(user_id=user_id, password_changed_at=None)
    session.users[user_id] = user

    token = make_token(sub=str(user_id))
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    assert resp.json()["data"]["id"] == str(user_id)


def test_naive_password_changed_at_treated_as_utc(test_setup):
    """Naive password_changed_at must be treated as UTC without crashing."""
    client: TestClient = test_setup["client"]
    session: FakeSession = test_setup["session"]
    user_id = uuid.uuid4()
    naive_dt = datetime(2026, 10, 12, 10, 0, 0)  # noqa: DTZ001 - Deliberately naive to test UTC conversion
    user = FakeUser(user_id=user_id, password_changed_at=naive_dt)
    session.users[user_id] = user

    iat = int(naive_dt.replace(tzinfo=timezone.utc).timestamp()) + 5
    token = make_token(sub=str(user_id), iat=iat)
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    assert resp.json()["data"]["id"] == str(user_id)


def test_valid_token_returns_user(test_setup):
    """Valid token returns authenticated user id via ok() response envelope."""
    client: TestClient = test_setup["client"]
    session: FakeSession = test_setup["session"]
    user_id = uuid.uuid4()
    user = FakeUser(user_id=user_id)
    session.users[user_id] = user

    token = make_token(sub=str(user_id))
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] is True
    assert body["data"]["id"] == str(user_id)
    assert body["error"] is None


def test_bearer_scheme_case_insensitive(test_setup):
    """Bearer scheme must be accepted regardless of letter casing (bearer, BEARER)."""
    client: TestClient = test_setup["client"]
    session: FakeSession = test_setup["session"]
    user_id = uuid.uuid4()
    user = FakeUser(user_id=user_id)
    session.users[user_id] = user

    token = make_token(sub=str(user_id))

    resp_lower = client.get("/_protected", headers={"Authorization": f"bearer {token}"})
    assert resp_lower.status_code == 200

    resp_upper = client.get("/_protected", headers={"Authorization": f"BEARER {token}"})
    assert resp_upper.status_code == 200


def test_401_envelope_shape(test_setup):
    """Verify exact 4-key envelope shape, null data/errors, constant detail, and traceId matching."""
    client: TestClient = test_setup["client"]
    resp = client.get("/_protected", headers={"X-Request-Id": "client-trace-1234"})
    assert resp.status_code == 401

    body = resp.json()
    assert set(body.keys()) == {"status", "message", "data", "error"}
    assert body["status"] is False
    assert body["data"] is None
    assert body["error"]["type"] == "unauthorized"
    assert body["error"]["detail"] == "Invalid or expired access token"
    assert body["error"]["errors"] is None
    assert body["error"]["traceId"] == "client-trace-1234"
    assert resp.headers.get("X-Request-Id") == "client-trace-1234"


def test_no_token_or_pyjwt_message_leaks(test_setup, caplog: pytest.LogCaptureFixture):
    """Ensure no raw token string and no PyJWT exception messages leak into DEBUG logs or response."""
    client: TestClient = test_setup["client"]
    token = make_token(sub=str(uuid.uuid4()), exp=1000000000, iat=999999000)

    with caplog.at_level(logging.DEBUG):
        resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})

    assert resp.status_code == 401
    body_text = resp.text
    log_text = caplog.text

    # No token leakage
    assert token not in body_text
    assert token not in log_text

    # No PyJWT message leakage (e.g. Signature has expired)
    assert "Signature has expired" not in body_text
    assert "Signature has expired" not in log_text


def test_db_error_is_not_converted_to_401(test_setup):
    """Database infrastructure failure must result in HTTP 500 internal-error, never 401."""
    client: TestClient = test_setup["client"]
    session: FakeSession = test_setup["session"]
    session.error_on_get = OperationalError("connection failure", params=None, orig=Exception("DB dead"))

    token = make_token(sub=str(uuid.uuid4()))
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 500
    body = resp.json()
    assert body["status"] is False
    assert body["error"]["type"] == "internal-error"


def test_secret_misconfig_is_not_converted_to_401(test_setup, monkeypatch: pytest.MonkeyPatch):
    """Secret configuration error (RuntimeError) must result in HTTP 500, never 401."""
    monkeypatch.setattr("app.core.jwt._get_secret", lambda: (_ for _ in ()).throw(RuntimeError("Invalid secret length")))
    client: TestClient = test_setup["client"]

    token = pyjwt.encode({"sub": str(uuid.uuid4()), "exp": 2000000000, "iat": 1000000000}, key="some-temp-key-longer-than-32-chars", algorithm="HS256")
    resp = client.get("/_protected", headers={"Authorization": f"Bearer {token}"})
    assert resp.status_code == 500
    body = resp.json()
    assert body["status"] is False
    assert body["error"]["type"] == "internal-error"
