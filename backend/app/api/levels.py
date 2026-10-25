"""Levels API router (contract 7.4)."""
from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.dependencies import CurrentUser
from app.core.schemas import ApiResponse, ok
from app.database.session import get_db
from app.schemas.level import LevelListDto
from app.services import level_service

router = APIRouter(prefix="/levels", tags=["levels"])

DbSession = Annotated[Session, Depends(get_db)]


@router.get(
    "",
    response_model=ApiResponse[LevelListDto],
    summary="List all levels with their status",
    responses={401: {"model": ApiResponse[None], "description": "unauthorized"}},
)
def get_levels(user: CurrentUser, db: DbSession):
    levels = level_service.list_levels(db, user.selected_level_id)
    return ok({"items": levels}, "OK")
