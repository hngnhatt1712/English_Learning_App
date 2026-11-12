"""Lesson detail access, with Round content deferred to Sprint 5."""
import uuid

from app.core.errors import AppError, ErrorType
from app.repositories.lesson_repository import LessonRepository
from app.schemas.enums import ContentStatus
from app.schemas.lesson import LessonDetailDto, LessonSummaryDto
from app.services.topic_service import get_topic_state
from sqlalchemy.orm import Session


def get_lesson_detail(db: Session, user_id: uuid.UUID, lesson_id: uuid.UUID) -> LessonDetailDto:
    """Confirm existence before checking access to the lesson's topic."""
    lesson = LessonRepository(db).get_by_id(lesson_id)
    if lesson is None:
        raise AppError(404, ErrorType.NOT_FOUND, "Lesson not found")
    topic_state = get_topic_state(db, user_id, lesson.topic_id)
    if topic_state.status == ContentStatus.LOCKED:
        raise AppError(403, ErrorType.TOPIC_LOCKED, "Topic is locked")
    return LessonDetailDto(**LessonSummaryDto.model_validate(lesson).model_dump(), rounds=[])
