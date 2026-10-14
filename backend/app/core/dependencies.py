"""Authentication dependency for FastAPI endpoints (contract 4.1, 10.2)."""
import logging
import uuid
from datetime import timezone
from typing import Annotated

import jwt
from app.core.errors import AppError, ErrorType
from app.core.jwt import decode_access_token
from app.database.session import get_db
from app.models.user import User
from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

logger = logging.getLogger("app.auth")

AUTH_ERROR_DETAIL = "Invalid or expired access token"
_MAX_TOKEN_LENGTH = 4096

bearer_scheme = HTTPBearer(auto_error=False, scheme_name="BearerAuth")


def get_current_user(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)],
    db: Annotated[Session, Depends(get_db)],
) -> User:
    """Validate Bearer JWT access token and return authenticated User ORM model.

    Rejection order:
    1. missing / invalid scheme / empty authorization header
    2. empty or oversized token (> 4096 characters)
    3. invalid / expired / untrusted JWT signature
    4. invalid subject format (must be a valid UUID string)
    5. invalid iat timestamp
    6. user not found in database
    7. token issued before password_changed_at
    """
    if credentials is None:
        logger.debug("missing_header")
        raise AppError(401, ErrorType.UNAUTHORIZED, AUTH_ERROR_DETAIL) from None

    token = credentials.credentials.strip()
    if not token or len(token) > _MAX_TOKEN_LENGTH:
        logger.debug("bad_length")
        raise AppError(401, ErrorType.UNAUTHORIZED, AUTH_ERROR_DETAIL) from None

    try:
        payload = decode_access_token(token)
    except jwt.InvalidTokenError:
        logger.debug("invalid_token")
        raise AppError(401, ErrorType.UNAUTHORIZED, AUTH_ERROR_DETAIL) from None

    sub = payload.get("sub")
    if not isinstance(sub, str):
        logger.debug("bad_sub")
        raise AppError(401, ErrorType.UNAUTHORIZED, AUTH_ERROR_DETAIL) from None

    try:
        user_id = uuid.UUID(sub)
    except ValueError:
        logger.debug("bad_sub")
        raise AppError(401, ErrorType.UNAUTHORIZED, AUTH_ERROR_DETAIL) from None

    iat = payload.get("iat")
    if iat is None or isinstance(iat, bool) or not isinstance(iat, (int, float)):
        logger.debug("bad_iat")
        raise AppError(401, ErrorType.UNAUTHORIZED, AUTH_ERROR_DETAIL) from None

    user = db.get(User, user_id)
    if user is None:
        logger.debug("user_not_found")
        raise AppError(401, ErrorType.UNAUTHORIZED, AUTH_ERROR_DETAIL) from None

    if user.password_changed_at is not None:
        changed_at = user.password_changed_at
        if changed_at.tzinfo is None:
            changed_at = changed_at.replace(tzinfo=timezone.utc)
        if int(iat) < int(changed_at.timestamp()):
            logger.debug("password_changed")
            raise AppError(401, ErrorType.UNAUTHORIZED, AUTH_ERROR_DETAIL) from None

    return user


CurrentUser = Annotated[User, Depends(get_current_user)]
