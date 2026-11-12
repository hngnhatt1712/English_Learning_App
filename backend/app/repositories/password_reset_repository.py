"""Repository for password_reset_requests table (contract 7.2, 16)."""
from __future__ import annotations

import uuid
from datetime import datetime

from app.models.password_reset_request import PasswordResetRequest
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session


class PasswordResetRepository:
    """Data access layer for OTP and one-time reset token lifecycle."""

    def __init__(self, db: Session) -> None:
        self.db = db
        self.session: Session = db

    def count_recent_by_user(self, user_id: uuid.UUID, since: datetime) -> int:
        """Count requests created by `user_id` at or after `since`."""
        stmt = (
            select(func.count())
            .select_from(PasswordResetRequest)
            .where(
                PasswordResetRequest.user_id == user_id,
                PasswordResetRequest.created_at >= since,
            )
        )
        return int(self.db.scalar(stmt) or 0)

    def get_latest_by_user(self, user_id: uuid.UUID) -> PasswordResetRequest | None:
        """Return the most recently created request for `user_id`, if any."""
        stmt = (
            select(PasswordResetRequest)
            .where(PasswordResetRequest.user_id == user_id)
            .order_by(PasswordResetRequest.created_at.desc(), PasswordResetRequest.id.desc())
            .limit(1)
        )
        return self.db.scalars(stmt).first()

    def consume_all_open_for_user(self, user_id: uuid.UUID, now: datetime) -> int:
        """Set `consumed_at = now` for all unconsumed requests belonging to `user_id`."""
        stmt = (
            update(PasswordResetRequest)
            .where(
                PasswordResetRequest.user_id == user_id,
                PasswordResetRequest.consumed_at.is_(None),
            )
            .values(consumed_at=now)
        )
        result = self.db.execute(stmt)
        return int(getattr(result, "rowcount", 0) or 0)

    def create_request(
        self,
        *,
        request_id: uuid.UUID,
        user_id: uuid.UUID,
        otp_hash: str,
        otp_expires_at: datetime,
        created_at: datetime,
    ) -> PasswordResetRequest:
        """Insert a new password reset request and flush it inside the active transaction."""
        req = PasswordResetRequest(
            id=request_id,
            user_id=user_id,
            otp_hash=otp_hash,
            otp_expires_at=otp_expires_at,
            attempts=0,
            reset_token_hash=None,
            reset_token_expires_at=None,
            consumed_at=None,
            created_at=created_at,
        )
        self.db.add(req)
        self.db.flush()
        return req

    def get_latest_open_for_update(
        self,
        user_id: uuid.UUID,
        now: datetime,
    ) -> PasswordResetRequest | None:
        """Lock and return the newest unconsumed, non-expired OTP request for `user_id`."""
        stmt = (
            select(PasswordResetRequest)
            .where(
                PasswordResetRequest.user_id == user_id,
                PasswordResetRequest.consumed_at.is_(None),
                PasswordResetRequest.otp_expires_at > now,
            )
            .order_by(PasswordResetRequest.created_at.desc(), PasswordResetRequest.id.desc())
            .limit(1)
            .with_for_update()
        )
        return self.db.scalars(stmt).first()

    def consume_reset_token_atomic(
        self,
        reset_token_hash: str,
        now: datetime,
    ) -> uuid.UUID | None:
        """Atomically mark a valid reset token as consumed and return its `user_id`."""
        stmt = (
            update(PasswordResetRequest)
            .where(
                PasswordResetRequest.reset_token_hash == reset_token_hash,
                PasswordResetRequest.consumed_at.is_(None),
                PasswordResetRequest.reset_token_expires_at.is_not(None),
                PasswordResetRequest.reset_token_expires_at > now,
            )
            .values(consumed_at=now)
            .returning(PasswordResetRequest.user_id)
        )
        return self.db.scalars(stmt).first()
