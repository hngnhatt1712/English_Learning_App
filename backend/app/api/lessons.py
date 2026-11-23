"""Authenticated lesson details with topic access enforcement (contract 7.4)."""
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Path

from app.core.dependencies import CurrentUser, LessonServiceDep
from app.core.schemas import ApiResponse, ok
from app.schemas.lesson import LessonDetailDto

router = APIRouter(prefix="/lessons", tags=["lessons"])


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
def get_lesson(
    lesson_id: Annotated[UUID, Path(alias="lessonId")], user: CurrentUser, service: LessonServiceDep,
) -> dict[str, object]:
    return ok(service.get_lesson_detail(user.id, lesson_id))
