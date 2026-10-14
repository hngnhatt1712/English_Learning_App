"""Unit tests for auth_service (no DB required).

Covers full test matrix from Section G of the sprint prompt.
"""
from __future__ import annotations

import logging
import uuid
from datetime import date, datetime, timezone
from unittest.mock import patch

import pytest
from pydantic import ValidationError
from sqlalchemy.exc import IntegrityError

from app.core.config import settings
from app.core.errors import AppError, ErrorType
from app.core.jwt import decode_access_token
from app.core.security import hash_password, verify_password
from app.schemas.auth import AuthResultDto, LoginRequest, RegisterRequest, UserDto
from app.services import auth_service
from app.services.auth_service import (
    assert_valid_username,
    get_me,
    login,
    normalize_email,
    normalize_identifier,
    normalize_username,
    register,
    to_user_dto,
)
from tests.fakes import FakeSession, FakeUser, FakeUserRepository


# ===========================================================================
# 1. Register Group
# ===========================================================================

def test_register_success_normalizes_and_hashes():
    """Register succeeds: email and username stored lowercase, password hashed with Argon2id."""
    session = FakeSession()
    repo = FakeUserRepository()
    fixed_now = datetime(2026, 10, 4, 10, 30, 0, tzinfo=timezone.utc)

    req = RegisterRequest(
        username="John_Doe",
        email="John.Doe@Example.COM",
        password="ValidPass123",
    )

    result = register(session, req, repo=repo, now=fixed_now)

    assert session.commit_count == 1
    assert session.rollback_count == 0
    assert len(repo.users) == 1

    stored_user = repo.users[0]
    assert stored_user.username == "john_doe"
    assert stored_user.email == "john.doe@example.com"
    assert stored_user.password_hash.startswith("$argon2id$")
    assert stored_user.password_hash != "ValidPass123"
    assert verify_password("ValidPass123", stored_user.password_hash)

    # Token payload verification
    payload = decode_access_token(result.access_token)
    assert payload["sub"] == str(stored_user.id)
    assert "email" not in payload
    assert "username" not in payload
    assert payload["exp"] - payload["iat"] == 604800
    assert result.expires_in == 604800
    assert result.token_type == "Bearer"
    assert result.user.username == "john_doe"
    assert result.user.email == "john.doe@example.com"


def test_register_duplicate_email_different_case():
    """Pre-check rejects duplicate email regardless of case with 409 email-taken."""
    session = FakeSession()
    existing_user = FakeUser(
        username="existing_user",
        email="alice@example.com",
        password_hash=hash_password("Pass1234"),
    )
    repo = FakeUserRepository(users=[existing_user])

    req = RegisterRequest(
        username="new_user",
        email="ALICE@EXAMPLE.COM",
        password="Passw0rd123",
    )

    with pytest.raises(AppError) as exc_info:
        register(session, req, repo=repo)

    assert exc_info.value.status_code == 409
    assert exc_info.value.type == ErrorType.EMAIL_TAKEN.value
    assert session.commit_count == 0


def test_register_duplicate_username_different_case():
    """Pre-check rejects duplicate username regardless of case with 409 username-taken."""
    session = FakeSession()
    existing_user = FakeUser(
        username="alice_w",
        email="alice@example.com",
        password_hash=hash_password("Pass1234"),
    )
    repo = FakeUserRepository(users=[existing_user])

    req = RegisterRequest(
        username="ALICE_W",
        email="new_alice@example.com",
        password="Passw0rd123",
    )

    with pytest.raises(AppError) as exc_info:
        register(session, req, repo=repo)

    assert exc_info.value.status_code == 409
    assert exc_info.value.type == ErrorType.USERNAME_TAKEN.value
    assert session.commit_count == 0


def test_register_both_email_and_username_duplicate():
    """When both email and username conflict, email-taken is prioritized (fixed order)."""
    session = FakeSession()
    existing_user = FakeUser(
        username="charlie",
        email="charlie@example.com",
        password_hash=hash_password("Pass1234"),
    )
    repo = FakeUserRepository(users=[existing_user])

    req = RegisterRequest(
        username="charlie",
        email="charlie@example.com",
        password="Passw0rd123",
    )

    with pytest.raises(AppError) as exc_info:
        register(session, req, repo=repo)

    assert exc_info.value.status_code == 409
    assert exc_info.value.type == ErrorType.EMAIL_TAKEN.value


def test_register_integrity_error_email_conflict_rollbacks():
    """Race condition: IntegrityError during commit rolls back and detects email conflict."""
    session = FakeSession(force_integrity_error=True)
    # Repo starts empty to pass pre-check, but then returns conflicting user on re-query
    repo = FakeUserRepository()

    def mock_create_user(username, email, password_hash, selected_level_id=None):
        # Simulate concurrent insert from another transaction
        conflict_user = FakeUser(
            username="other_user",
            email=email,
            password_hash=password_hash,
        )
        repo.users.append(conflict_user)
        return conflict_user

    repo.create_user = mock_create_user

    req = RegisterRequest(
        username="new_user",
        email="race@example.com",
        password="Passw0rd123",
    )

    with pytest.raises(AppError) as exc_info:
        register(session, req, repo=repo)

    assert exc_info.value.status_code == 409
    assert exc_info.value.type == ErrorType.EMAIL_TAKEN.value
    assert session.rollback_count == 1


def test_register_integrity_error_username_conflict_rollbacks():
    """Race condition: IntegrityError during commit rolls back and detects username conflict."""
    session = FakeSession(force_integrity_error=True)
    repo = FakeUserRepository()

    def mock_create_user(username, email, password_hash, selected_level_id=None):
        conflict_user = FakeUser(
            username=username,
            email="other_email@example.com",
            password_hash=password_hash,
        )
        repo.users.append(conflict_user)
        return conflict_user

    repo.create_user = mock_create_user

    req = RegisterRequest(
        username="race_user",
        email="unique@example.com",
        password="Passw0rd123",
    )

    with pytest.raises(AppError) as exc_info:
        register(session, req, repo=repo)

    assert exc_info.value.status_code == 409
    assert exc_info.value.type == ErrorType.USERNAME_TAKEN.value
    assert session.rollback_count == 1


def test_register_integrity_error_unknown_re_raises():
    """IntegrityError during commit without detectable email/username match is re-raised."""
    session = FakeSession(force_integrity_error=True)
    repo = FakeUserRepository()

    req = RegisterRequest(
        username="some_user",
        email="some@example.com",
        password="Passw0rd123",
    )

    with pytest.raises(IntegrityError):
        register(session, req, repo=repo)

    assert session.rollback_count == 1


def test_register_create_user_integrity_error_email_conflict_rollbacks():
    """Race condition: IntegrityError during create_user (flush) detects email conflict."""
    session = FakeSession()
    concurrent = FakeUser(
        username="other_u",
        email="race_create@example.com",
        password_hash=hash_password("Pass1234"),
    )
    repo = FakeUserRepository(
        force_create_integrity_error=True,
        concurrent_user=concurrent,
    )

    req = RegisterRequest(
        username="new_user",
        email="race_create@example.com",
        password="Passw0rd123",
    )

    with pytest.raises(AppError) as exc_info:
        register(session, req, repo=repo)

    assert exc_info.value.status_code == 409
    assert exc_info.value.type == ErrorType.EMAIL_TAKEN.value
    assert session.rollback_count == 1
    assert session.commit_count == 0


def test_register_create_user_integrity_error_username_conflict_rollbacks():
    """Race condition: IntegrityError during create_user (flush) detects username conflict."""
    session = FakeSession()
    concurrent = FakeUser(
        username="race_create_u",
        email="other@example.com",
        password_hash=hash_password("Pass1234"),
    )
    repo = FakeUserRepository(
        force_create_integrity_error=True,
        concurrent_user=concurrent,
    )

    req = RegisterRequest(
        username="race_create_u",
        email="unique@example.com",
        password="Passw0rd123",
    )

    with pytest.raises(AppError) as exc_info:
        register(session, req, repo=repo)

    assert exc_info.value.status_code == 409
    assert exc_info.value.type == ErrorType.USERNAME_TAKEN.value
    assert session.rollback_count == 1
    assert session.commit_count == 0


def test_register_create_user_integrity_error_unknown_re_raises():
    """IntegrityError during create_user without detectable email/username match is re-raised."""
    session = FakeSession()
    repo = FakeUserRepository(force_create_integrity_error=True)

    req = RegisterRequest(
        username="some_user",
        email="some@example.com",
        password="Passw0rd123",
    )

    with pytest.raises(IntegrityError):
        register(session, req, repo=repo)

    assert session.rollback_count == 1
    assert session.commit_count == 0


def test_register_other_commit_error_rollbacks():
    """Generic exception during commit triggers rollback and re-raises original error."""
    class CustomDbError(Exception):
        pass

    class FailingSession(FakeSession):
        def commit(self):
            raise CustomDbError("DB disconnect")

    session = FailingSession()
    repo = FakeUserRepository()

    req = RegisterRequest(
        username="test_user",
        email="test@example.com",
        password="Passw0rd123",
    )

    with pytest.raises(CustomDbError):
        register(session, req, repo=repo)

    assert session.rollback_count == 1


def test_register_invalid_username_with_at_symbol():
    """Direct invocation of register with username containing '@' raises 422 VALIDATION_ERROR."""
    session = FakeSession()
    repo = FakeUserRepository()

    req = RegisterRequest.model_construct(
        username="user@name",
        email="valid@example.com",
        password="Passw0rd123",
    )

    with pytest.raises(AppError) as exc_info:
        register(session, req, repo=repo)

    assert exc_info.value.status_code == 422
    assert exc_info.value.type == ErrorType.VALIDATION_ERROR.value


def test_register_password_with_whitespace_hashed_verbatim():
    """Password with leading/trailing whitespace must be hashed verbatim (D-04)."""
    session = FakeSession()
    repo = FakeUserRepository()
    padded_pw = "  SecretPass123  "

    req = RegisterRequest(
        username="spaced_user",
        email="spaced@example.com",
        password=padded_pw,
    )

    register(session, req, repo=repo)
    stored_hash = repo.users[0].password_hash

    assert verify_password(padded_pw, stored_hash) is True
    assert verify_password(padded_pw.strip(), stored_hash) is False


# ===========================================================================
# 2. Login Group
# ===========================================================================

def test_login_by_email_success():
    """Login with email succeeds, does not invoke commit, and verifies token expiration."""
    session = FakeSession()
    raw_pw = "MySecret123"
    user = FakeUser(
        username="john_doe",
        email="john@example.com",
        password_hash=hash_password(raw_pw),
    )
    repo = FakeUserRepository(users=[user])

    req = LoginRequest(identifier="  JOHN@EXAMPLE.COM  ", password=raw_pw)
    result = login(session, req, repo=repo)

    assert session.commit_count == 0
    assert result.user.email == "john@example.com"
    assert result.user.username == "john_doe"
    assert result.token_type == "Bearer"
    assert result.expires_in == 604800

    payload = decode_access_token(result.access_token)
    assert payload["sub"] == str(user.id)
    assert payload["exp"] - payload["iat"] == 604800 == result.expires_in


def test_login_by_username_success():
    """Login with username succeeds, does not invoke commit, and verifies token expiration."""
    session = FakeSession()
    raw_pw = "MySecret123"
    user = FakeUser(
        username="john_doe",
        email="john@example.com",
        password_hash=hash_password(raw_pw),
    )
    repo = FakeUserRepository(users=[user])

    req = LoginRequest(identifier="  JOHN_DOE  ", password=raw_pw)
    result = login(session, req, repo=repo)

    assert session.commit_count == 0
    assert result.user.username == "john_doe"
    assert result.token_type == "Bearer"
    assert result.expires_in == 604800

    payload = decode_access_token(result.access_token)
    assert payload["sub"] == str(user.id)
    assert payload["exp"] - payload["iat"] == 604800 == result.expires_in


def test_login_wrong_password_raises_invalid_credentials():
    """Wrong password raises 401 INVALID_CREDENTIALS with 'Invalid credentials' detail."""
    session = FakeSession()
    user = FakeUser(
        username="john_doe",
        email="john@example.com",
        password_hash=hash_password("CorrectPass1"),
    )
    repo = FakeUserRepository(users=[user])

    req = LoginRequest(identifier="john_doe", password="WrongPassword2")
    with pytest.raises(AppError) as exc_info:
        login(session, req, repo=repo)

    assert exc_info.value.status_code == 401
    assert exc_info.value.type == ErrorType.INVALID_CREDENTIALS.value
    assert exc_info.value.detail == "Invalid credentials"


def test_login_unknown_identifier_calls_dummy_verify():
    """Unknown identifier raises identical 401 and calls _dummy_verify once."""
    session = FakeSession()
    repo = FakeUserRepository()

    req = LoginRequest(identifier="nonexistent@example.com", password="Password123")

    with patch.object(auth_service, "_dummy_verify", wraps=auth_service._dummy_verify) as spy_dummy:
        with pytest.raises(AppError) as exc_info:
            login(session, req, repo=repo)

        assert exc_info.value.status_code == 401
        assert exc_info.value.type == ErrorType.INVALID_CREDENTIALS.value
        assert exc_info.value.detail == "Invalid credentials"
        assert spy_dummy.call_count == 1


def test_login_corrupted_hash_raises_invalid_credentials():
    """Corrupted/invalid password hash raises identical 401 invalid-credentials."""
    session = FakeSession()
    user = FakeUser(
        username="john_doe",
        email="john@example.com",
        password_hash="corrupted_garbage_hash",
    )
    repo = FakeUserRepository(users=[user])

    req = LoginRequest(identifier="john_doe", password="Password123")
    with pytest.raises(AppError) as exc_info:
        login(session, req, repo=repo)

    assert exc_info.value.status_code == 401
    assert exc_info.value.type == ErrorType.INVALID_CREDENTIALS.value
    assert exc_info.value.detail == "Invalid credentials"


def test_login_identifier_with_at_does_not_call_get_by_username():
    """Identifier containing '@' queries only get_by_email (D-05)."""
    session = FakeSession()
    repo = FakeUserRepository()

    req = LoginRequest(identifier="user@example.com", password="Password123")
    with pytest.raises(AppError):
        login(session, req, repo=repo)

    assert len(repo.get_by_email_calls) == 1
    assert len(repo.get_by_username_calls) == 0


def test_login_identifier_without_at_does_not_call_get_by_email():
    """Identifier not containing '@' queries only get_by_username (D-05)."""
    session = FakeSession()
    repo = FakeUserRepository()

    req = LoginRequest(identifier="plain_username", password="Password123")
    with pytest.raises(AppError):
        login(session, req, repo=repo)

    assert len(repo.get_by_username_calls) == 1
    assert len(repo.get_by_email_calls) == 0


def test_login_blank_identifier_runs_dummy_verify_without_query():
    """Direct call with blank identifier executes dummy_verify and raises 401 without querying."""
    session = FakeSession()
    repo = FakeUserRepository()

    req = LoginRequest.model_construct(identifier="   ", password="Password123")

    with patch.object(auth_service, "_dummy_verify", wraps=auth_service._dummy_verify) as spy_dummy:
        with pytest.raises(AppError) as exc_info:
            login(session, req, repo=repo)

        assert exc_info.value.status_code == 401
        assert exc_info.value.type == ErrorType.INVALID_CREDENTIALS.value
        assert spy_dummy.call_count == 1
        assert len(repo.get_by_email_calls) == 0
        assert len(repo.get_by_username_calls) == 0


# ===========================================================================
# 3. DTO Group
# ===========================================================================

def test_to_user_dto_exact_keys_no_password_hash():
    """to_user_dto produces exactly the contract 6.2 keys and never includes passwordHash."""
    now_dt = datetime(2026, 10, 4, 10, 30, 0, tzinfo=timezone.utc)
    user = FakeUser(
        username="test_user",
        email="test@example.com",
        password_hash="secret_hash",
        selected_level_id=None,
        current_streak=5,
        last_learning_date=date(2026, 10, 4),
        created_at=now_dt,
    )

    dto = to_user_dto(user, today=date(2026, 10, 4))
    dumped = dto.model_dump(mode="json", by_alias=True)

    expected_keys = {
        "id",
        "username",
        "email",
        "selectedLevelId",
        "currentStreak",
        "lastLearningDate",
        "createdAt",
    }
    assert set(dumped.keys()) == expected_keys
    assert "passwordHash" not in dumped
    assert "password_hash" not in dumped


def test_to_user_dto_read_adjusted_streak():
    """to_user_dto resets streak to 0 if last learning date is >= 2 days ago (contract 9.3)."""
    user = FakeUser(
        username="stale_user",
        email="stale@example.com",
        password_hash="hash",
        current_streak=10,
        last_learning_date=date(2026, 10, 1),
    )

    dto = to_user_dto(user, today=date(2026, 10, 4))
    assert dto.current_streak == 0


def test_get_me_returns_adjusted_dto():
    """get_me computes UserDto for the given user at the specified time."""
    now_dt = datetime(2026, 10, 4, 15, 0, 0, tzinfo=timezone.utc)
    user = FakeUser(
        username="me_user",
        email="me@example.com",
        password_hash="hash",
        current_streak=2,
        last_learning_date=date(2026, 10, 4),
    )

    dto = get_me(user, now=now_dt)
    assert dto.username == "me_user"
    assert dto.current_streak == 2


# ===========================================================================
# 4. Normalization Group
# ===========================================================================

def test_normalization_functions():
    """normalize_* strips whitespace and lowercases properly."""
    assert normalize_email("  User@Example.Com  ") == "user@example.com"
    assert normalize_username("  My_User.123  ") == "my_user.123"
    assert normalize_identifier("  Login_Id  ") == "login_id"


@pytest.mark.parametrize(
    "invalid_user",
    [
        "ab",          # len 2 (< 3)
        "a" * 31,      # len 31 (> 30)
        "user@name",   # contains @
        "has space",   # contains space
        "user!name",   # invalid char
    ],
)
def test_assert_valid_username_rejects(invalid_user):
    """assert_valid_username raises 422 VALIDATION_ERROR on any invalid pattern."""
    with pytest.raises(AppError) as exc_info:
        assert_valid_username(invalid_user)

    assert exc_info.value.status_code == 422
    assert exc_info.value.type == ErrorType.VALIDATION_ERROR.value


# ===========================================================================
# 5. Security & Privacy Group
# ===========================================================================

def test_caplog_debug_does_not_leak_secrets(caplog):
    """Execution does not log passwords, identifiers, tokens, or emails (D-17)."""
    session = FakeSession()
    repo = FakeUserRepository()

    caplog.set_level(logging.DEBUG)

    req = RegisterRequest(
        username="secret_user",
        email="secret@example.com",
        password="SuperSecret123",
    )
    result = register(session, req, repo=repo)

    login_req = LoginRequest(identifier="secret_user", password="SuperSecret123")
    login(session, login_req, repo=repo)

    all_logs = caplog.text
    assert "SuperSecret123" not in all_logs
    assert result.access_token not in all_logs


def test_repr_auth_result_dto_does_not_leak_token():
    """repr() of AuthResultDto does not leak the accessToken."""
    uid = uuid.uuid4()
    user_dto = UserDto(
        id=uid,
        username="u1",
        email="u1@example.com",
        selected_level_id=None,
        current_streak=0,
        last_learning_date=None,
        created_at=datetime.now(timezone.utc),
    )
    dto = AuthResultDto(
        access_token="eyJ_super_secret_jwt_token",
        token_type="Bearer",
        expires_in=604800,
        user=user_dto,
    )
    assert "eyJ_super_secret_jwt_token" not in repr(dto)
