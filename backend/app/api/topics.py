"""Authenticated topic list and detail endpoints (contract 7.4)."""
from typing import Annotated
from uuid import UUID

from app.core.dependencies import CurrentUser
from app.core.schemas import ApiResponse, ListData, ok
from app.database.session import get_db
from app.schemas.topic import TopicDetailDto, TopicDto
from app.services import topic_service
from fastapi import APIRouter, Depends, Path, Query
from sqlalchemy.orm import Session

router = APIRouter(prefix="/topics", tags=["topics"])
DbSession = Annotated[Session, Depends(get_db)]
ERROR_RESPONSES: dict[int | str, dict[str, object]] = {
    401: {"model": ApiResponse[None], "description": "unauthorized"},
    404: {"model": ApiResponse[None], "description": "not-found"},
    422: {"model": ApiResponse[None], "description": "validation-error"},
}


@router.get(
    "", response_model=ApiResponse[ListData[TopicDto]],
    summary="List topics in a level with current user progress", responses=ERROR_RESPONSES,
)
def get_topics(
    user: CurrentUser, db: DbSession, level_id: Annotated[UUID, Query(alias="levelId")],
) -> dict[str, object]:
    return ok({"items": topic_service.list_topics(db, user.id, level_id)})


@router.get(
    "/{topicId}", response_model=ApiResponse[TopicDetailDto],
    summary="Get a topic and its lesson summaries", responses=ERROR_RESPONSES,
)
def get_topic(topic_id: Annotated[UUID, Path(alias="topicId")], user: CurrentUser, db: DbSession) -> dict[str, object]:
    return ok(topic_service.get_topic_detail(db, user.id, topic_id))
