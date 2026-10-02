"""Tạo / verify JWT access token bằng PyJWT (chưa gắn vào route nào)."""
from datetime import datetime, timedelta, timezone
from typing import Any

import jwt as pyjwt 

from app.core.config import settings

ACCESS_TOKEN_TYPE = "access"


class TokenError(Exception):
    """Token sai định dạng, sai chữ ký, hết hạn hoặc sai loại."""


def create_access_token(
    subject: str | int,
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
    return pyjwt.encode(payload, settings.JWT_SECRET_KEY, algorithm=settings.JWT_ALGORITHM)


def decode_access_token(token: str) -> dict[str, Any]:
    """Verify chữ ký + hạn dùng, trả về payload. Raise TokenError nếu không hợp lệ."""
    try:
        payload = pyjwt.decode(
            token,
            settings.JWT_SECRET_KEY,
            algorithms=[settings.JWT_ALGORITHM],
            options={"require": ["sub", "exp", "iat"]},
        )
    except pyjwt.PyJWTError as exc:  # gồm ExpiredSignatureError, InvalidSignatureError, ...
        raise TokenError(str(exc)) from exc

    if payload.get("type") != ACCESS_TOKEN_TYPE:
        raise TokenError("Sai loại token")
    return payload


def get_subject(token: str) -> str:
    """Tiện ích: verify token và trả về `sub` (user id dạng string)."""
    return decode_access_token(token)["sub"]