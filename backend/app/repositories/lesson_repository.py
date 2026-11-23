"""Read-only lesson retrieval."""
import uuid

from app.models.lesson import Lesson
from sqlalchemy import select
from sqlalchemy.orm import Session


class LessonRepository:
    def __init__(self, db: Session) -> None:
        self.db = db

    def get_by_id(self, lesson_id: uuid.UUID) -> Lesson | None:
        stmt = select(Lesson).where(Lesson.id == lesson_id)
        return self.db.scalars(stmt).one_or_none()

    def get_by_topic_id(self, topic_id: uuid.UUID) -> list[Lesson]:
        stmt = (
            select(Lesson).where(Lesson.topic_id == topic_id)
            .order_by(Lesson.order_index.asc(), Lesson.id.asc())
        )
        return list(self.db.scalars(stmt).all())
