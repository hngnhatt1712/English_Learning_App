"""Profile service: read and partially update the current user's profile (contract 7.3, D14)."""
from __future__ import annotations

from datetime import datetime
from typing import Any

from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.errors import AppError, ErrorType
from app.repositories.level_repository import LevelRepository
from app.repositories.user_repository import UserRepository
from app.schemas.profile import ProfileDto, ProfileLevelDto, ProfileUpdateRequest
from app.services.auth_service import assert_valid_username, normalize_username
from app.services.level_service import (
    MAX_STARS_PER_LEVEL,
    MAX_STARS_TOTAL,
    is_level_unlocked,
)
from app.services.streak_service import effective_streak, today_vn

# Sprint 3 placeholders: real values come from progress data in Sprint 4-7.
PLACEHOLDER_STARS: int = 0
PLACEHOLDER_PROGRESS_PERCENT: float = 0.0


def get_profile(
    session: Session | Any,
    user: Any,
    *,
    level_repo: LevelRepository | Any | None = None,
    now: datetime | None = None,
) -> ProfileDto:
    """Build the ProfileDto field by field (contract 7.3). Read-only: never writes or commits."""
    level_dto: ProfileLevelDto | None = None
    if user.selected_level_id is not None:
        repository = level_repo if level_repo is not None else LevelRepository(session)
        level = repository.get_by_id(user.selected_level_id)
        if level is not None:
            level_dto = ProfileLevelDto(
                id=level.id,
                name=level.name,
                stars=PLACEHOLDER_STARS,
                max_stars=MAX_STARS_PER_LEVEL,
                progress_percent=PLACEHOLDER_PROGRESS_PERCENT,
            )
    return ProfileDto(
        id=user.id,
        username=user.username,
        email=user.email,
        level=level_dto,
        total_stars=PLACEHOLDER_STARS,
        max_stars=MAX_STARS_TOTAL,
        current_streak=effective_streak(user.current_streak, user.last_learning_date, today_vn(now)),
        created_at=user.created_at,
    )


def update_profile(
    session: Session | Any,
    user: Any,
    payload: ProfileUpdateRequest,
    *,
    user_repo: UserRepository | Any | None = None,
    level_repo: LevelRepository | Any | None = None,
    now: datetime | None = None,
) -> ProfileDto:
    """Partially update username and/or selected level, all-or-nothing (contract 7.3, D14).

    `user` must be the ORM object attached to `session` (the CurrentUser of this request).
    Check order: 404 level -> 403 level-locked -> 409 username-taken; nothing is written before all pass.
    """
    users = user_repo if user_repo is not None else UserRepository(session)
    levels_repo = level_repo if level_repo is not None else LevelRepository(session)
    fields = payload.model_fields_set
    user_id = user.id

    new_level_id = None
    if "selected_level_id" in fields:
        levels = levels_repo.get_all()
        target = payload.selected_level_id
        assert target is not None  # Schema ensures non-null if in model_fields_set
        if target not in {level.id for level in levels}:
            raise AppError(404, ErrorType.NOT_FOUND, "Level not found")
        if not is_level_unlocked(target, levels, user.selected_level_id):
            raise AppError(403, ErrorType.LEVEL_LOCKED, "Level is locked")
        new_level_id = target

    new_username = None
    if "username" in fields:
        raw_username = payload.username
        assert raw_username is not None  # Schema ensures non-null if in model_fields_set
        new_username = normalize_username(raw_username)
        assert_valid_username(new_username)
        other = users.get_by_username(new_username)
        if other is not None and other.id != user_id:
            raise AppError(409, ErrorType.USERNAME_TAKEN, "Username already taken")

    try:
        if new_username is not None:
            user.username = new_username
            session.flush()
        if new_level_id is not None:
            users.update_selected_level(user_id, new_level_id)
        session.commit()
    except IntegrityError:
        session.rollback()
        if new_username is not None:
            other = users.get_by_username(new_username)
            if other is not None and other.id != user_id:
                raise AppError(409, ErrorType.USERNAME_TAKEN, "Username already taken")
        raise
    except Exception:
        session.rollback()
        raise

    return get_profile(session, user, level_repo=levels_repo, now=now)
