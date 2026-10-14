"""Logout service: unregister the caller's own FCM device token (contract 7.2)."""
from __future__ import annotations

from typing import Any
from uuid import UUID

from app.repositories.device_token_repository import DeviceTokenRepository
from sqlalchemy.orm import Session


def logout(
    session: Session | Any,
    user_id: UUID,
    fcm_token: str | None,
    *,
    repo: Any | None = None,
) -> None:
    """Delete the caller's own device token if provided. Idempotent, never raises for missing rows."""
    if fcm_token is None:
        return
    repository = repo if repo is not None else DeviceTokenRepository(session)
    try:
        repository.delete_by_user_and_token(user_id=user_id, fcm_token=fcm_token)
        session.commit()
    except Exception:
        session.rollback()
        raise
