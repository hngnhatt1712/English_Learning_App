"""Authentication service layer for register, login, and user profile resolution.

Strictly follows:
- api-contract.md (D7, D8, 4.1, 4.4, 6.2, 7.2, 9.3)
- plan_sprint3_services_auth_profile_level.md (D-01, D-02, D-04 to D-11, D-17, D-18)
"""
from __future__ import annotations

import functools
import re
import secrets
from datetime import date, datetime
from typing import Any

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.errors import AppError, ErrorType
from app.core.jwt import create_access_token
from app.core.security import hash_password, verify_password
from app.repositories.user_repository import UserRepository
from app.schemas.auth import AuthResultDto, LoginRequest, RegisterRequest, UserDto
from app.schemas.types import USERNAME_MAX, USERNAME_MIN, USERNAME_PATTERN
from app.services.streak_service import effective_streak, today_vn

_USERNAME_RE = re.compile(USERNAME_PATTERN)


def normalize_email(email: str) -> str:
    """Normalize email: strip outer whitespace and convert to lowercase."""
    return email.strip().lower()


def normalize_username(username: str) -> str:
    """Normalize username: strip outer whitespace and convert to lowercase."""
    return username.strip().lower()


def normalize_identifier(identifier: str) -> str:
    """Normalize login identifier: strip outer whitespace and convert to lowercase."""
    return identifier.strip().lower()


def assert_valid_username(username: str) -> None:
    """Validate username rules.

    Must be 3-30 chars, match USERNAME_PATTERN, and must not contain '@'.
    Raises AppError(422, VALIDATION_ERROR) on failure.
    """
    if (
        "@" in username
        or not (USERNAME_MIN <= len(username) <= USERNAME_MAX)
        or not _USERNAME_RE.fullmatch(username)
    ):
        raise AppError(
            status_code=422,
            type_=ErrorType.VALIDATION_ERROR,
            detail="Invalid username: must be 3-30 alphanumeric characters, periods, or underscores, with no @",
        )


def to_user_dto(user: Any, today: date) -> UserDto:
    """Construct UserDto manually field-by-field (contract 6.2).

    Applies read-time effective streak adjustment (contract 9.3).
    Never includes password hash.
    """
    adjusted_streak = effective_streak(user.current_streak, user.last_learning_date, today)
    return UserDto(
        id=user.id,
        username=user.username,
        email=user.email,
        selected_level_id=user.selected_level_id,
        current_streak=adjusted_streak,
        last_learning_date=user.last_learning_date,
        created_at=user.created_at,
    )


@functools.lru_cache(maxsize=1)
def _get_dummy_hash() -> str:
    """Generate a lazily-computed Argon2 hash for dummy verification (D-07)."""
    return hash_password(secrets.token_urlsafe(32))


def _dummy_verify() -> None:
    """Perform one Argon2 verification with a dummy hash to prevent timing attacks."""
    verify_password("dummy_password_for_timing_safety", _get_dummy_hash())


def register(
    session: Session | Any,
    payload: RegisterRequest,
    *,
    repo: UserRepository | Any | None = None,
    now: datetime | None = None,
) -> AuthResultDto:
    """Register a new user account (contract 7.2).

    1. Normalizes email and username to lowercase.
    2. Enforces defensive username validation (no '@', valid pattern).
    3. Pre-checks uniqueness (email first, then username).
    4. Hashes password verbatim (no stripping).
    5. Persists user within a managed database transaction.
    6. Returns AuthResultDto with 7-day JWT access token.
    """
    norm_email = normalize_email(payload.email)
    norm_username = normalize_username(payload.username)
    assert_valid_username(norm_username)

    repository = repo if repo is not None else UserRepository(session)
    if hasattr(repository, "session") and getattr(repository, "session", None) is None:
        repository.session = session

    if repository.get_by_email(norm_email) is not None:
        raise AppError(
            status_code=409,
            type_=ErrorType.EMAIL_TAKEN,
            detail="Email already registered",
        )

    if repository.get_by_username(norm_username) is not None:
        raise AppError(
            status_code=409,
            type_=ErrorType.USERNAME_TAKEN,
            detail="Username already taken",
        )

    hashed_password = hash_password(payload.password)

    try:
        user = repository.create_user(
            username=norm_username,
            email=norm_email,
            password_hash=hashed_password,
        )
        session.commit()
    except IntegrityError:
        session.rollback()
        # D-09: Race condition resolution - check email first, then username
        if repository.get_by_email(norm_email) is not None:
            raise AppError(
                status_code=409,
                type_=ErrorType.EMAIL_TAKEN,
                detail="Email already registered",
            )
        if repository.get_by_username(norm_username) is not None:
            raise AppError(
                status_code=409,
                type_=ErrorType.USERNAME_TAKEN,
                detail="Username already taken",
            )
        raise
    except Exception:
        session.rollback()
        raise

    current_today = today_vn(now)
    token = create_access_token(subject=str(user.id))
    expires_in_seconds = settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60

    return AuthResultDto(
        access_token=token,
        token_type="Bearer",
        expires_in=expires_in_seconds,
        user=to_user_dto(user, current_today),
    )


def login(
    session: Session | Any,
    payload: LoginRequest,
    *,
    repo: UserRepository | Any | None = None,
    now: datetime | None = None,
) -> AuthResultDto:
    """Authenticate user with email or username (contract 7.2).

    1. Normalizes identifier; if blank, executes dummy verify and raises 401.
    2. If identifier contains '@', look up exclusively by email; else by username.
    3. If user not found, executes dummy verify and raises 401.
    4. If password mismatch or corrupted hash, raises identical 401 error.
    5. On success, returns AuthResultDto without DB mutation or commit.
    """
    norm_identifier = normalize_identifier(payload.identifier)
    if not norm_identifier:
        _dummy_verify()
        raise AppError(
            status_code=401,
            type_=ErrorType.INVALID_CREDENTIALS,
            detail="Invalid credentials",
        )

    repository = repo if repo is not None else UserRepository(session)

    if "@" in norm_identifier:
        user = repository.get_by_email(norm_identifier)
    else:
        user = repository.get_by_username(norm_identifier)

    if user is None:
        _dummy_verify()
        raise AppError(
            status_code=401,
            type_=ErrorType.INVALID_CREDENTIALS,
            detail="Invalid credentials",
        )

    is_valid = verify_password(payload.password, user.password_hash)
    if not is_valid:
        raise AppError(
            status_code=401,
            type_=ErrorType.INVALID_CREDENTIALS,
            detail="Invalid credentials",
        )

    current_today = today_vn(now)
    token = create_access_token(subject=str(user.id))
    expires_in_seconds = settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60

    return AuthResultDto(
        access_token=token,
        token_type="Bearer",
        expires_in=expires_in_seconds,
        user=to_user_dto(user, current_today),
    )


def get_me(user: Any, *, now: datetime | None = None) -> UserDto:
    """Return the UserDto for the currently authenticated user (contract 7.2)."""
    return to_user_dto(user, today_vn(now))
