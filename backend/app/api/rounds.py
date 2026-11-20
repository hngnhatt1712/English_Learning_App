"""Authenticated Round content, submit and vocabulary actions."""
from typing import Annotated, Any
from uuid import UUID

from fastapi import APIRouter, Path

from app.core.dependencies import CurrentUser, RoundServiceDep
from app.core.schemas import ApiResponse, ok
from app.schemas.round import (
    RoundDetailDto,
    RoundResultDto,
    SubmitRoundRequest,
    VocabularyActionRequest,
    VocabularyActionResultDto,
)

router = APIRouter(prefix="/rounds", tags=["rounds"])
RoundId = Annotated[UUID, Path(alias="roundId")]
ERRORS: dict[int | str, dict[str, Any]] = {
    401: {"model": ApiResponse[None], "description": "unauthorized"},
    403: {"model": ApiResponse[None], "description": "round-locked"},
    404: {"model": ApiResponse[None], "description": "not-found"},
    422: {"model": ApiResponse[None], "description": "validation-error"},
    500: {"model": ApiResponse[None], "description": "internal-error"},
}


@router.get("/{roundId}", response_model=ApiResponse[RoundDetailDto], responses=ERRORS)
def get_round(round_id: RoundId, user: CurrentUser, service: RoundServiceDep):
    return ok(service.get_round(user, round_id))


@router.post("/{roundId}/submit", response_model=ApiResponse[RoundResultDto], responses={
    **ERRORS, 409: {"model": ApiResponse[None], "description": "round-incomplete"},
})
def submit_round(round_id: RoundId, body: SubmitRoundRequest, user: CurrentUser, service: RoundServiceDep):
    return ok(service.submit_round(user, round_id, body))


@router.post("/{roundId}/vocabulary-action", response_model=ApiResponse[VocabularyActionResultDto], responses=ERRORS)
def vocabulary_action(round_id: RoundId, body: VocabularyActionRequest, user: CurrentUser, service: RoundServiceDep):
    return ok(service.vocabulary_action(user, round_id, body))
