"""Authenticated Vocabulary catalog."""
from typing import Annotated
from uuid import UUID

from app.core.dependencies import CurrentUser, DbSession, VocabularyRepositoryDep
from app.core.schemas import ApiResponse, PageData, ok, paginated
from app.schemas.quiz import (
    QuizResultDto,
    SubmitVocabularyQuizRequest,
    VocabularyQuizDto,
)
from app.schemas.vocabulary import VocabularyDto, VocabularySort, VocabularyStatus
from app.services import quiz_service, vocabulary_service
from app.services.quiz_submission_service import submit_vocabulary_quiz
from fastapi import APIRouter, Query

router = APIRouter(prefix="/vocabulary", tags=["vocabulary"])


@router.get(
    "",
    summary="List vocabulary from accessible topics",
    response_model=ApiResponse[PageData[VocabularyDto]],
    responses={
        401: {"model": ApiResponse[None], "description": "unauthorized"},
        403: {"model": ApiResponse[None], "description": "topic-locked"},
        404: {"model": ApiResponse[None], "description": "not-found"},
        422: {"model": ApiResponse[None], "description": "validation-error"},
        500: {"model": ApiResponse[None], "description": "internal-error"},
    },
)
def list_vocabulary(
    user: CurrentUser,
    repository: VocabularyRepositoryDep,
    topic_id: Annotated[UUID | None, Query(alias="topicId")] = None,
    level_id: Annotated[UUID | None, Query(alias="levelId")] = None,
    status: Annotated[VocabularyStatus | None, Query()] = None,
    search: Annotated[str | None, Query(max_length=50)] = None,
    sort: Annotated[VocabularySort, Query()] = "word",
    page: Annotated[int, Query(ge=1)] = 1,
    limit: Annotated[int, Query(ge=1, le=100)] = 20,
):
    items, total = vocabulary_service.list_vocabulary(
        repository, user, topic_id=topic_id, level_id=level_id, status=status,
        search=search, sort=sort, page=page, limit=limit,
    )
    return paginated(items, total, page, limit)


@router.get(
    "/quiz",
    summary="Get a Vocabulary Quiz for an accessible topic",
    response_model=ApiResponse[VocabularyQuizDto],
    responses={
        401: {"model": ApiResponse[None], "description": "unauthorized"},
        403: {"model": ApiResponse[None], "description": "topic-locked"},
        404: {"model": ApiResponse[None], "description": "not-found"},
        422: {"model": ApiResponse[None], "description": "validation-error"},
        500: {"model": ApiResponse[None], "description": "internal-error"},
    },
)
def get_vocabulary_quiz(
    user: CurrentUser,
    db: DbSession,
    topic_id: Annotated[UUID, Query(alias="topicId")],
    count: Annotated[int, Query(ge=5, le=20)] = 10,
):
    return ok(quiz_service.get_vocabulary_quiz(db, user, topic_id, count))


@router.post(
    "/quiz/submit",
    summary="Submit a Vocabulary Quiz for an accessible topic",
    response_model=ApiResponse[QuizResultDto],
    responses={
        401: {"model": ApiResponse[None], "description": "unauthorized"},
        403: {"model": ApiResponse[None], "description": "topic-locked"},
        404: {"model": ApiResponse[None], "description": "not-found"},
        422: {"model": ApiResponse[None], "description": "validation-error"},
        500: {"model": ApiResponse[None], "description": "internal-error"},
    },
)
def submit_quiz(body: SubmitVocabularyQuizRequest, user: CurrentUser, db: DbSession):
    return ok(submit_vocabulary_quiz(db, user, body))
