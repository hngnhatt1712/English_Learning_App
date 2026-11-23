"""Change-password service (contract 4.1, 7.2)."""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.errors import AppError, ErrorType
from app.core.jwt import create_access_token
from app.core.security import hash_password, verify_password
from app.schemas.auth import AuthResultDto
from app.schemas.password import ChangePasswordRequest
from app.services.auth_service import to_user_dto
from app.services.streak_service import today_vn


def _utcnow() -> datetime:
    return datetime.now(timezone.utc)


def change_password(
    session: Session | Any,
    user: Any,
    payload: ChangePasswordRequest,
    *,
    now: datetime | None = None,
) -> AuthResultDto:
    """Verify the current password, set the new one and return a fresh token.

    `user` must be the ORM object attached to `session` (the CurrentUser of this request).
    Older tokens are invalidated through `password_changed_at` (contract 4.1).
    The result is built before the commit but only returned after it succeeds.
    """
    if not verify_password(payload.current_password, user.password_hash):
        raise AppError(400, ErrorType.WRONG_PASSWORD, "Current password is incorrect")

    new_hash = hash_password(payload.new_password)
    changed_at = now if now is not None else _utcnow()
    if changed_at.tzinfo is None:
        changed_at = changed_at.replace(tzinfo=timezone.utc)
    else:
        changed_at = changed_at.astimezone(timezone.utc)
    user_id = user.id

    try:
        # Token first: a failure here leaves the user object untouched.
        token = create_access_token(subject=str(user_id))
        user.password_hash = new_hash
        user.password_changed_at = changed_at
        result = AuthResultDto(
            access_token=token,
            token_type="Bearer",
            expires_in=settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60,
            user=to_user_dto(user, today_vn(now)),
        )
        session.commit()
    except Exception:
        session.rollback()
        raise

    return result

