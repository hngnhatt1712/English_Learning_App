"""Stateless per-question feedback; no progress or streak writes."""
from typing import Annotated
from uuid import UUID

from app.core.dependencies import CurrentUser, RoundServiceDep
from app.core.schemas import ApiResponse, ok
from app.schemas.question import CheckAnswerRequest, CheckQuestionResultDto
from fastapi import APIRouter, Path

router = APIRouter(prefix="/questions", tags=["questions"])


@router.post("/{questionId}/check", response_model=ApiResponse[CheckQuestionResultDto], responses={
    401: {"model": ApiResponse[None], "description": "unauthorized"},
    403: {"model": ApiResponse[None], "description": "round-locked, topic-locked or forbidden"},
    404: {"model": ApiResponse[None], "description": "not-found"},
    422: {"model": ApiResponse[None], "description": "validation-error"},
    500: {"model": ApiResponse[None], "description": "internal-error"},
})
def check_answer(
    question_id: Annotated[UUID, Path(alias="questionId")], body: CheckAnswerRequest,
    user: CurrentUser, service: RoundServiceDep,
):
    return ok(service.check_answer(user, question_id, body))
