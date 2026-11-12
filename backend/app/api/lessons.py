"""Authenticated lesson details with topic access enforcement (contract 7.4)."""
from typing import Annotated
from uuid import UUID

from app.core.dependencies import CurrentUser
from app.core.schemas import ApiResponse, ok
from app.database.session import get_db
from app.schemas.lesson import LessonDetailDto
from app.services import lesson_service
from fastapi import APIRouter, Depends, Path
from sqlalchemy.orm import Session

router = APIRouter(prefix="/lessons", tags=["lessons"])
DbSession = Annotated[Session, Depends(get_db)]


@router.get(
    "/{lessonId}", response_model=ApiResponse[LessonDetailDto],
    summary="Get an accessible lesson with its rounds",
    responses={
        401: {"model": ApiResponse[None], "description": "unauthorized"},
        403: {"model": ApiResponse[None], "description": "topic-locked"},
        404: {"model": ApiResponse[None], "description": "not-found"},
        422: {"model": ApiResponse[None], "description": "validation-error"},
    },
)
def get_lesson(lesson_id: Annotated[UUID, Path(alias="lessonId")], user: CurrentUser, db: DbSession) -> dict[str, object]:
    return ok(lesson_service.get_lesson_detail(db, user.id, lesson_id))
