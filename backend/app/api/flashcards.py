"""Authenticated Flashcard library and review."""
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Query

from app.core.dependencies import CurrentUser, VocabularyRepositoryDep
from app.core.schemas import ApiResponse, PageData, ok, paginated
from app.schemas.flashcard import (
    FlashcardDto,
    FlashcardLibraryItemDto,
    FlashcardLibrarySort,
    FlashcardReviewRequest,
    FlashcardReviewResultDto,
)
from app.services import flashcard_service

router = APIRouter(prefix="/flashcards", tags=["flashcards"])


@router.get(
    "",
    summary="List saved flashcards",
    response_model=ApiResponse[PageData[FlashcardLibraryItemDto]],
    responses={
        401: {"model": ApiResponse[None], "description": "unauthorized"},
        404: {"model": ApiResponse[None], "description": "not-found"},
        422: {"model": ApiResponse[None], "description": "validation-error"},
        500: {"model": ApiResponse[None], "description": "internal-error"},
    },
)
def list_flashcard_library(
    user: CurrentUser,
    repository: VocabularyRepositoryDep,
    topic_id: Annotated[UUID | None, Query(alias="topicId")] = None,
    search: Annotated[str | None, Query(max_length=50)] = None,
    sort: Annotated[FlashcardLibrarySort | None, Query()] = None,
    page: Annotated[int, Query(ge=1)] = 1,
    limit: Annotated[int, Query(ge=1, le=100)] = 20,
):
    items, total = flashcard_service.list_flashcard_library(
        repository, user.id, topic_id=topic_id, search=search, sort=sort, page=page, limit=limit,
    )
    return paginated(items, total, page, limit)


@router.get(
    "/review",
    summary="List flashcards due for review",
    response_model=ApiResponse[PageData[FlashcardDto]],
    responses={
        401: {"model": ApiResponse[None], "description": "unauthorized"},
        422: {"model": ApiResponse[None], "description": "validation-error"},
        500: {"model": ApiResponse[None], "description": "internal-error"},
    },
)
def list_due_flashcards(
    user: CurrentUser,
    repository: VocabularyRepositoryDep,
    page: Annotated[int, Query(ge=1)] = 1,
    limit: Annotated[int, Query(ge=1, le=100)] = 20,
):
    items, total = flashcard_service.list_due_flashcards(repository, user.id, page=page, limit=limit)
    return paginated(items, total, page, limit)


@router.post(
    "/review",
    summary="Rate a flashcard and update its review schedule",
    response_model=ApiResponse[FlashcardReviewResultDto],
    responses={
        401: {"model": ApiResponse[None], "description": "unauthorized"},
        404: {"model": ApiResponse[None], "description": "not-found"},
        422: {"model": ApiResponse[None], "description": "validation-error"},
        500: {"model": ApiResponse[None], "description": "internal-error"},
    },
)
def rate_flashcard(body: FlashcardReviewRequest, user: CurrentUser, repository: VocabularyRepositoryDep):
    return ok(flashcard_service.rate_flashcard(repository, user, body))
