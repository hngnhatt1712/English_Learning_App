"""Password reset service: OTP generation, verification, and atomic password reset (contract 7.2)."""
from __future__ import annotations

import hashlib
import hmac
import secrets
import uuid
from datetime import datetime, timedelta, timezone

from app.core.config import settings
from app.core.errors import AppError, ErrorType
from app.core.mailer import EmailSender, get_email_sender
from app.core.security import hash_password
from app.repositories.password_reset_repository import PasswordResetRepository
from app.repositories.user_repository import UserRepository
from app.schemas.password import (
    ForgotPasswordRequest,
    ForgotPasswordResultDto,
    ResetPasswordRequest,
    VerifyOtpRequest,
    VerifyOtpResultDto,
)
from app.services.auth_service import normalize_email
from fastapi import BackgroundTasks
from sqlalchemy.orm import Session

INVALID_OTP_DETAIL = "Invalid or expired OTP"
INVALID_RESET_TOKEN_DETAIL = "Invalid or expired reset token"

_DUMMY_REQUEST_ID = uuid.UUID("00000000-0000-0000-0000-000000000000")
_DUMMY_OTP_HASH = "0" * 64


def _now() -> datetime:
    """Return current UTC datetime (monkeypatchable in tests)."""
    return datetime.now(timezone.utc)


def _ensure_utc(dt: datetime) -> datetime:
    """Normalize a datetime to timezone-aware UTC."""
    if dt.tzinfo is None:
        return dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc)


def _get_pepper() -> str:
    """Lazily validate and return `PASSWORD_RESET_PEPPER` (must be >= 32 chars, D-09)."""
    pepper = getattr(settings, "PASSWORD_RESET_PEPPER", "")
    if not isinstance(pepper, str) or len(pepper) < 32:
        raise RuntimeError("PASSWORD_RESET_PEPPER must be configured with at least 32 characters")
    return pepper


def generate_otp() -> str:
    """Generate a cryptographically random 6-digit zero-padded OTP string (D-02)."""
    return f"{secrets.randbelow(10**6):06d}"


def compute_otp_hash(
    request_id: uuid.UUID | str,
    otp: str,
    pepper: str | None = None,
) -> str:
    """Compute HMAC-SHA256 of `<request_id>:<otp>` using `PASSWORD_RESET_PEPPER` (D-03)."""
    secret_pepper = pepper if pepper is not None else _get_pepper()
    if len(secret_pepper) < 32:
        raise RuntimeError("PASSWORD_RESET_PEPPER must be configured with at least 32 characters")
    message = f"{request_id}:{otp}".encode()
    return hmac.new(secret_pepper.encode("utf-8"), message, hashlib.sha256).hexdigest()


def verify_otp_hash(
    request_id: uuid.UUID | str,
    otp: str,
    expected_hash: str,
    pepper: str | None = None,
) -> bool:
    """Constant-time verification of an OTP against `expected_hash` (D-03)."""
    actual_hash = compute_otp_hash(request_id, otp, pepper=pepper)
    return hmac.compare_digest(actual_hash, expected_hash)


def generate_reset_token() -> str:
    """Generate an opaque URL-safe one-time reset token (~256 bits entropy, D-03)."""
    return secrets.token_urlsafe(32)


def hash_reset_token(token: str) -> str:
    """Compute SHA-256 hex digest of an opaque reset token (D-03)."""
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


def request_reset(
    session: Session,
    payload: ForgotPasswordRequest,
    *,
    background_tasks: BackgroundTasks | None = None,
    email_sender: EmailSender | None = None,
    user_repo: UserRepository | None = None,
    reset_repo: PasswordResetRepository | None = None,
    now: datetime | None = None,
) -> ForgotPasswordResultDto:
    """Create a password reset OTP request and dispatch email if eligible (D-05, D-06).

    Always returns `ForgotPasswordResultDto(retry_after_seconds=...)` regardless of
    whether the email exists or rate limits are reached (anti-enumeration).
    """
    _get_pepper()
    current_now = _ensure_utc(now if now is not None else _now())
    cooldown = int(getattr(settings, "OTP_RESEND_COOLDOWN_SECONDS", 60))

    u_repo = user_repo if user_repo is not None else UserRepository(session)
    r_repo = reset_repo if reset_repo is not None else PasswordResetRepository(session)

    norm_email = normalize_email(str(payload.email))
    user = u_repo.get_by_email(norm_email)
    if user is None:
        return ForgotPasswordResultDto(retry_after_seconds=cooldown)

    latest = r_repo.get_latest_by_user(user.id)
    if latest is not None:
        elapsed_seconds = (current_now - _ensure_utc(latest.created_at)).total_seconds()
        if elapsed_seconds < cooldown:
            return ForgotPasswordResultDto(retry_after_seconds=cooldown)

    max_per_hour = int(getattr(settings, "OTP_MAX_REQUESTS_PER_HOUR", 5))
    recent_count = r_repo.count_recent_by_user(user.id, current_now - timedelta(hours=1))
    if recent_count >= max_per_hour:
        return ForgotPasswordResultDto(retry_after_seconds=cooldown)

    otp = generate_otp()
    request_id = uuid.uuid4()
    otp_hash = compute_otp_hash(request_id, otp)
    ttl_seconds = int(getattr(settings, "OTP_TTL_SECONDS", 600))
    otp_expires_at = current_now + timedelta(seconds=ttl_seconds)

    try:
        r_repo.consume_all_open_for_user(user.id, current_now)
        r_repo.create_request(
            request_id=request_id,
            user_id=user.id,
            otp_hash=otp_hash,
            otp_expires_at=otp_expires_at,
            created_at=current_now,
        )
        session.commit()
    except BaseException:
        session.rollback()
        raise

    sender = email_sender if email_sender is not None else get_email_sender()
    if background_tasks is not None:
        background_tasks.add_task(sender.send_otp_email, user.email, otp)
    else:
        sender.send_otp_email(user.email, otp)

    return ForgotPasswordResultDto(retry_after_seconds=cooldown)


def verify_otp(
    session: Session,
    payload: VerifyOtpRequest,
    *,
    user_repo: UserRepository | None = None,
    reset_repo: PasswordResetRepository | None = None,
    now: datetime | None = None,
) -> VerifyOtpResultDto:
    """Verify a 6-digit OTP and issue a one-time reset token (D-07).

    All business failure branches raise identical `400 invalid_otp`.
    """
    _get_pepper()
    current_now = _ensure_utc(now if now is not None else _now())

    u_repo = user_repo if user_repo is not None else UserRepository(session)
    r_repo = reset_repo if reset_repo is not None else PasswordResetRepository(session)

    norm_email = normalize_email(str(payload.email))
    user = u_repo.get_by_email(norm_email)
    if user is None:
        verify_otp_hash(_DUMMY_REQUEST_ID, payload.otp, _DUMMY_OTP_HASH)
        raise AppError(400, ErrorType.INVALID_OTP, INVALID_OTP_DETAIL)

    req = r_repo.get_latest_open_for_update(user.id, current_now)
    if (
        req is None
        or req.consumed_at is not None
        or req.reset_token_hash is not None
        or _ensure_utc(req.otp_expires_at) <= current_now
    ):
        verify_otp_hash(_DUMMY_REQUEST_ID, payload.otp, _DUMMY_OTP_HASH)
        raise AppError(400, ErrorType.INVALID_OTP, INVALID_OTP_DETAIL)

    max_attempts = int(getattr(settings, "OTP_MAX_ATTEMPTS", 5))
    if req.attempts >= max_attempts:
        verify_otp_hash(req.id, payload.otp, req.otp_hash)
        raise AppError(400, ErrorType.INVALID_OTP, INVALID_OTP_DETAIL)

    is_valid = verify_otp_hash(req.id, payload.otp, req.otp_hash)
    if not is_valid:
        try:
            req.attempts += 1
            session.commit()
        except BaseException:
            session.rollback()
            raise
        raise AppError(400, ErrorType.INVALID_OTP, INVALID_OTP_DETAIL)

    raw_token = generate_reset_token()
    token_ttl = int(getattr(settings, "RESET_TOKEN_TTL_SECONDS", 600))
    try:
        req.reset_token_hash = hash_reset_token(raw_token)
        req.reset_token_expires_at = current_now + timedelta(seconds=token_ttl)
        req.otp_expires_at = current_now
        session.commit()
    except BaseException:
        session.rollback()
        raise

    return VerifyOtpResultDto(
        reset_token=raw_token,
        expires_in_seconds=token_ttl,
    )


def reset_password(
    session: Session,
    payload: ResetPasswordRequest,
    *,
    user_repo: UserRepository | None = None,
    reset_repo: PasswordResetRepository | None = None,
    now: datetime | None = None,
) -> None:
    """Atomically consume `resetToken`, update user password, and invalidate older sessions (D-13)."""
    current_now = _ensure_utc(now if now is not None else _now())
    raw_token = payload.reset_token.strip()
    if not raw_token:
        raise AppError(400, ErrorType.INVALID_RESET_TOKEN, INVALID_RESET_TOKEN_DETAIL)

    token_hash = hash_reset_token(raw_token)
    u_repo = user_repo if user_repo is not None else UserRepository(session)
    r_repo = reset_repo if reset_repo is not None else PasswordResetRepository(session)

    user = None
    old_password_hash: str | None = None
    old_password_changed_at: datetime | None = None

    try:
        user_id = r_repo.consume_reset_token_atomic(token_hash, current_now)
        if user_id is None:
            session.rollback()
            raise AppError(400, ErrorType.INVALID_RESET_TOKEN, INVALID_RESET_TOKEN_DETAIL)

        user = u_repo.get_by_id(user_id)
        if user is None:
            session.rollback()
            raise AppError(400, ErrorType.INVALID_RESET_TOKEN, INVALID_RESET_TOKEN_DETAIL)

        old_password_hash = user.password_hash
        old_password_changed_at = user.password_changed_at

        new_hash = hash_password(payload.new_password)
        user.password_hash = new_hash
        user.password_changed_at = current_now
        r_repo.consume_all_open_for_user(user_id, current_now)
        session.commit()
    except AppError:
        raise
    except BaseException:
        if user is not None and old_password_hash is not None:
            user.password_hash = old_password_hash
            user.password_changed_at = old_password_changed_at
        session.rollback()
        raise
