"""Tests for auth schemas (contract 6.2, 7.2)."""
import uuid
from datetime import date, datetime, timedelta, timezone
from uuid import UUID

import pytest
from pydantic import ValidationError

from app.schemas.auth import (
    AuthResultDto,
    LoginRequest,
    LogoutRequest,
    RegisterRequest,
    UserDto,
)


def test_auth_request_schema_keys():
    """Verify JSON schema properties match contract."""
    reg_props = set(RegisterRequest.model_json_schema(by_alias=True)["properties"].keys())
    assert reg_props == {"username", "email", "password"}

    login_props = set(LoginRequest.model_json_schema(by_alias=True)["properties"].keys())
    assert login_props == {"identifier", "password"}

    logout_props = set(LogoutRequest.model_json_schema(by_alias=True)["properties"].keys())
    assert logout_props == {"fcmToken"}


def test_auth_dto_keys():
    """Verify DTO JSON keys match contract."""
    uid = uuid.uuid4()
    now = datetime(2026, 10, 4, 10, 30, 0, tzinfo=timezone.utc)
    user = UserDto(
        id=uid,
        username="nhat_dao",
        email="nhat@example.com",
        selected_level_id=None,
        current_streak=3,
        last_learning_date=date(2026, 10, 4),
        created_at=now,
    )
    user_dump = user.model_dump(mode="json", by_alias=True)
    assert set(user_dump.keys()) == {
        "id",
        "username",
        "email",
        "selectedLevelId",
        "currentStreak",
        "lastLearningDate",
        "createdAt",
    }
    assert len(user_dump) == 7

    auth_result = AuthResultDto(
        access_token="test.jwt.token",
        expires_in=604800,
        user=user,
    )
    auth_dump = auth_result.model_dump(mode="json", by_alias=True)
    assert set(auth_dump.keys()) == {"accessToken", "tokenType", "expiresIn", "user"}
    assert auth_dump["tokenType"] == "Bearer"


def test_register_request_valid():
    """Valid register requests."""
    req = RegisterRequest(
        username="nhat_dao",
        email="nhat@example.com",
        password="Passw0rdOK",
    )
    assert req.username == "nhat_dao"
    assert req.email == "nhat@example.com"
    assert req.password == "Passw0rdOK"


def test_login_request_valid():
    """Valid login requests with email or username."""
    req_email = LoginRequest(identifier="nhat@example.com", password="Passw0rdOK")
    assert req_email.identifier == "nhat@example.com"

    req_user = LoginRequest(identifier="nhat_dao", password="Passw0rdOK")
    assert req_user.identifier == "nhat_dao"


@pytest.mark.parametrize(
    "invalid_username",
    [
        "ab",          # min length 3
        "a@b",         # invalid pattern (no @ allowed)
        "a" * 31,      # max length 30
        "abc\n",       # newline
        "has space",   # spaces not allowed
    ],
)
def test_register_invalid_username(invalid_username):
    with pytest.raises(ValidationError):
        RegisterRequest(
            username=invalid_username,
            email="valid@example.com",
            password="Passw0rdOK",
        )


@pytest.mark.parametrize(
    "invalid_password",
    [
        "1234567",                # min length 8
        "abcdefgh",               # no digit
        "12345678",               # no letter
        "A" * 65 + "1",           # > 64 chars (66 chars)
        "é" * 63 + "a1",          # 65 chars (Unicode > 72 bytes)
        "é" * 5 + "a1",           # 7 chars
    ],
)
def test_register_invalid_password(invalid_password):
    with pytest.raises(ValidationError):
        RegisterRequest(
            username="valid_user",
            email="valid@example.com",
            password=invalid_password,
        )


def test_register_password_unicode_over_72_bytes_accepted():
    """D-06: 64-char Unicode password (>72 bytes) must be accepted since Argon2 has no 72-byte limit."""
    password_64 = "é" * 62 + "a1"  # 64 chars, 126 bytes in UTF-8
    assert len(password_64) == 64
    assert len(password_64.encode("utf-8")) > 72
    req = RegisterRequest(
        username="valid_user",
        email="valid@example.com",
        password=password_64,
    )
    assert req.password == password_64


@pytest.mark.parametrize(
    "invalid_email",
    [
        "not-an-email",
        "Tên <a@b.com>",
        "a b@c.com",
        "u" * 200 + "@" + "d" * 50 + ".com",  # 255 chars
    ],
)
def test_register_invalid_email(invalid_email):
    with pytest.raises(ValidationError):
        RegisterRequest(
            username="valid_user",
            email=invalid_email,
            password="Passw0rdOK",
        )


@pytest.mark.parametrize("blank_id", ["", "   ", "\t\n"])
def test_login_blank_identifier(blank_id):
    with pytest.raises(ValidationError):
        LoginRequest(identifier=blank_id, password="Passw0rdOK")


def test_logout_invalid_fcm():
    with pytest.raises(ValidationError):
        LogoutRequest(fcm_token="")


def test_extra_fields_ignored():
    """Verify extra="ignore" drops undeclared fields."""
    req = RegisterRequest.model_validate(
        {
            "username": "nhat_dao",
            "email": "nhat@example.com",
            "password": "Passw0rdOK",
            "id": "some-id",
            "passwordHash": "hash",
        }
    )
    dumped = req.model_dump()
    assert "id" not in dumped
    assert "passwordHash" not in dumped


def test_repr_redaction():
    """Ensure sensitive tokens and passwords never appear in repr()."""
    reg = RegisterRequest(username="user1", email="user1@example.com", password="SecretPassword1")
    assert "SecretPassword1" not in repr(reg)

    login = LoginRequest(identifier="user1", password="SecretPassword1")
    assert "SecretPassword1" not in repr(login)

    user = UserDto(
        id=uuid.uuid4(),
        username="user1",
        email="user1@example.com",
        selected_level_id=None,
        current_streak=0,
        last_learning_date=None,
        created_at=datetime.now(timezone.utc),
    )
    auth_result = AuthResultDto(
        access_token="secret.jwt.token",
        expires_in=604800,
        user=user,
    )
    assert "secret.jwt.token" not in repr(auth_result)

    logout = LogoutRequest(fcm_token="secret_fcm_token")
    assert "secret_fcm_token" not in repr(logout)


def test_user_dto_datetime_and_nulls():
    """Verify UtcDatetime serialization, YYYY-MM-DD date, and preserving null keys."""
    uid = uuid.uuid4()
    # Datetime with +07:00 timezone
    tz7 = timezone(timedelta(hours=7))
    dt_with_tz = datetime(2026, 10, 4, 17, 30, 0, tzinfo=tz7)

    user1 = UserDto(
        id=uid,
        username="nhat_dao",
        email="nhat@example.com",
        selected_level_id=None,
        current_streak=5,
        last_learning_date=None,
        created_at=dt_with_tz,
    )
    dump1 = user1.model_dump(mode="json", by_alias=True)
    assert dump1["createdAt"] == "2026-10-04T10:30:00Z"
    assert dump1["selectedLevelId"] is None
    assert dump1["lastLearningDate"] is None
    assert "selectedLevelId" in dump1
    assert "lastLearningDate" in dump1

    # Naive datetime treated as UTC
    naive_dt = datetime(2026, 10, 4, 10, 30, 0)
    user2 = UserDto(
        id=uid,
        username="nhat_dao",
        email="nhat@example.com",
        selected_level_id=UUID("00000000-0000-0000-0000-000000000001"),
        current_streak=0,
        last_learning_date=date(2026, 10, 4),
        created_at=naive_dt,
    )
    dump2 = user2.model_dump(mode="json", by_alias=True)
    assert dump2["createdAt"] == "2026-10-04T10:30:00Z"
    assert dump2["lastLearningDate"] == "2026-10-04"
    assert dump2["selectedLevelId"] == "00000000-0000-0000-0000-000000000001"


def test_auth_result_expires_in_constraint():
    user = UserDto(
        id=uuid.uuid4(),
        username="u1",
        email="u1@example.com",
        selected_level_id=None,
        current_streak=0,
        last_learning_date=None,
        created_at=datetime.now(timezone.utc),
    )
    with pytest.raises(ValidationError):
        AuthResultDto(
            access_token="token",
            expires_in=0,  # type: ignore[arg-type] # ge=1 required
            user=user,
        )


def test_schema_d06_unicode_and_boundary_checks():
    """D-06: 64-char Unicode password accepted, <8 and >64 rejected."""
    pw_64_unicode = "é" * 62 + "a1"
    assert len(pw_64_unicode) == 64
    assert len(pw_64_unicode.encode("utf-8")) > 72

    req = RegisterRequest(
        username="valid_u",
        email="valid@example.com",
        password=pw_64_unicode,
    )
    assert req.password == pw_64_unicode

    # 7 chars rejected
    with pytest.raises(ValidationError):
        RegisterRequest(username="valid_u", email="v@example.com", password="é" * 5 + "a1")

    # 65 chars rejected
    with pytest.raises(ValidationError):
        RegisterRequest(username="valid_u", email="v@example.com", password="é" * 63 + "a1")

