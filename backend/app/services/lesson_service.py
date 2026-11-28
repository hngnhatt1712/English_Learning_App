"""Lesson details with actual Activity content and user-owned Round metadata."""
import uuid

from sqlalchemy.orm import Session

from app.core.errors import AppError, ErrorType
from app.repositories.lesson_repository import LessonRepository
from app.repositories.round_repository import RoundRepository
from app.schemas.enums import ContentStatus
from app.schemas.lesson import LessonDetailDto, LessonSummaryDto
from app.services.round_content import content_error
from app.services.round_service import round_dto
from app.services.topic_service import get_topic_state


class LessonService:
    def __init__(self, db: Session, lessons: LessonRepository, rounds: RoundRepository) -> None:
        self.db = db
        self.lessons = lessons
        self.rounds = rounds

    def get_lesson_detail(self, user_id: uuid.UUID, lesson_id: uuid.UUID) -> LessonDetailDto:
        """Confirm existence before checking access to the lesson's topic."""
        lesson = self.lessons.get_by_id(lesson_id)
        if lesson is None:
            raise AppError(404, ErrorType.NOT_FOUND, "Lesson not found")
        topic_state = get_topic_state(self.db, user_id, lesson.topic_id)
        if topic_state.status == ContentStatus.LOCKED:
            raise AppError(403, ErrorType.TOPIC_LOCKED, "Topic is locked")
        activities = self.rounds.get_by_lesson(lesson.id)
        ids = [activity.id for activity in activities]
        counts = self.rounds.scoring_unit_counts(ids)
        progress = self.rounds.progress_by_ids(user_id, ids)
        by_number = {activity.round_number: activity for activity in activities}
        rounds = []
        for activity in activities:
            total = counts.get(activity.id, 0)
            if total is None:
                raise content_error()
            previous = by_number.get(activity.round_number - 1)
            rounds.append(round_dto(
                activity, progress.get(activity.id), progress.get(previous.id) if previous else None,
                True, total,
            ))
        return LessonDetailDto(**LessonSummaryDto.model_validate(lesson).model_dump(), rounds=rounds)


def get_lesson_detail(db: Session, user_id: uuid.UUID, lesson_id: uuid.UUID) -> LessonDetailDto:
    """Keep the session-based entry point for callers outside FastAPI."""
    return LessonService(db, LessonRepository(db), RoundRepository(db)).get_lesson_detail(user_id, lesson_id)
