"""Tạo / verify JWT access token bằng PyJWT."""
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any

import jwt as pyjwt
from app.core.config import settings

ACCESS_TOKEN_TYPE = "access"
JWT_ALGORITHM = "HS256"


class TokenError(pyjwt.InvalidTokenError):
    """Token sai định dạng, sai chữ ký, hết hạn hoặc sai loại."""


def _get_secret() -> str:
    """Đọc JWT secret từ settings với lazy validation (D-08)."""
    secret = getattr(settings, "JWT_SECRET_KEY", "")
    if not secret or len(secret) < 32:
        raise RuntimeError("JWT secret key must be at least 32 characters long")
    return secret


def create_access_token(
    subject: str | int | uuid.UUID,
    expires_delta: timedelta | None = None,
    extra_claims: dict[str, Any] | None = None,
) -> str:
    """Sinh access token. `subject` thường là user id."""
    now = datetime.now(timezone.utc)
    expire = now + (expires_delta or timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES))

    payload: dict[str, Any] = {
        **(extra_claims or {}),
        "sub": str(subject),  # PyJWT yêu cầu sub là string
        "type": ACCESS_TOKEN_TYPE,
        "iat": now,
        "exp": expire,
    }
    return pyjwt.encode(payload, _get_secret(), algorithm=JWT_ALGORITHM)


def decode_access_token(token: str) -> dict[str, Any]:
    """Verify chữ ký + hạn dùng, trả về payload. Nổi pyjwt.InvalidTokenError nếu không hợp lệ."""
    payload = pyjwt.decode(
        token,
        _get_secret(),
        algorithms=[JWT_ALGORITHM],
        options={"require": ["sub", "exp", "iat"]},
    )

    if "type" in payload and payload["type"] != ACCESS_TOKEN_TYPE:
        raise TokenError("Sai loại token")
    return payload


def get_subject(token: str) -> str:
    """Tiện ích: verify token và trả về `sub` (user id dạng string)."""
    return decode_access_token(token)["sub"]