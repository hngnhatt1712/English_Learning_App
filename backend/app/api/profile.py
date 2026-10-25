"""Profile API router (contract 7.3, D-01 through D-12).

Routes:
    GET  /api/v1/profile  — return current user's ProfileDto
    PUT  /api/v1/profile  — partial update (username and/or selectedLevelId)

Design decisions enforced here:
    - D-02: routes use "" not "/", preventing 307 redirect on PUT.
    - D-04: CurrentUser dependency only; no userId from client (BOLA).
    - D-06: service imported as module for monkeypatching in tests.
    - D-07: router is thin — no business logic, no DB access, no try/except.
"""
from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.dependencies import CurrentUser
from app.core.schemas import ApiResponse, ok
from app.database.session import get_db
from app.schemas.profile import ProfileDto, ProfileUpdateRequest
from app.services import profile_service

router = APIRouter(prefix="/profile", tags=["profile"])

DbSession = Annotated[Session, Depends(get_db)]


@router.get(
    "",
    response_model=ApiResponse[ProfileDto],
    summary="Get the current user's profile",
    responses={401: {"model": ApiResponse[None], "description": "unauthorized"}},
)
def get_profile(user: CurrentUser, db: DbSession) -> dict:
    """Return ProfileDto for the authenticated user (contract 7.3)."""
    return ok(profile_service.get_profile(db, user), "OK")


@router.put(
    "",
    response_model=ApiResponse[ProfileDto],
    summary="Update username and/or selected level (partial update)",
    responses={
        401: {"model": ApiResponse[None], "description": "unauthorized"},
        403: {"model": ApiResponse[None], "description": "level-locked"},
        404: {"model": ApiResponse[None], "description": "not-found"},
        409: {"model": ApiResponse[None], "description": "username-taken"},
        422: {"model": ApiResponse[None], "description": "validation-error"},
    },
)
def update_profile(
    payload: ProfileUpdateRequest, user: CurrentUser, db: DbSession
) -> dict:
    """Partially update username and/or selectedLevelId (contract 7.3, 2.4 §5)."""
    return ok(profile_service.update_profile(db, user, payload), "Profile updated")
