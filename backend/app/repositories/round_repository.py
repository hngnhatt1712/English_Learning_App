"""Fixed-batch content and user-filtered Round reads; no business rules here."""
from collections.abc import Sequence
from uuid import UUID

from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from app.models.activity import Activity
from app.models.lesson import Lesson
from app.models.progress import UserRoundProgress
from app.models.question import Question
from app.models.topic import Topic


class RoundRepository:
    def __init__(self, db: Session) -> None:
        self.db = db

    def get_context(self, round_id: UUID) -> tuple[Activity, Lesson, Topic] | None:
        row = self.db.execute(
            select(Activity, Lesson, Topic).join(Lesson, Activity.lesson_id == Lesson.id)
            .join(Topic, Lesson.topic_id == Topic.id).where(Activity.id == round_id)
        ).one_or_none()
        return (row[0], row[1], row[2]) if row is not None else None

    def get_by_lesson(self, lesson_id: UUID) -> list[Activity]:
        return list(self.db.scalars(
            select(Activity).where(Activity.lesson_id == lesson_id)
            .order_by(Activity.round_number, Activity.id)
        ))

    def question_counts(self, round_ids: Sequence[UUID]) -> dict[UUID, int]:
        if not round_ids:
            return {}
        rows = self.db.execute(
            select(Question.activity_id, func.count(Question.id))
            .where(Question.activity_id.in_(round_ids), Question.source == "round")
            .group_by(Question.activity_id)
        ).all()
        return {key: count for key, count in rows if key is not None}

    def progress_by_ids(self, user_id: UUID, round_ids: Sequence[UUID]) -> dict[UUID, UserRoundProgress]:
        if not round_ids:
            return {}
        return {row.round_id: row for row in self.db.scalars(
            select(UserRoundProgress).where(
                UserRoundProgress.user_id == user_id, UserRoundProgress.round_id.in_(round_ids),
            )
        )}

    def questions(self, round_id: UUID) -> list[Question]:
        return list(self.db.scalars(
            select(Question).where(Question.activity_id == round_id, Question.source == "round")
            .order_by(Question.id)
        ))

    def question_by_id(self, question_id: UUID) -> Question | None:
        return self.db.scalar(select(Question).where(Question.id == question_id))

    def first_incomplete(self, user_id: UUID, lesson_id: UUID) -> Activity | None:
        return self.db.scalar(
            select(Activity).outerjoin(UserRoundProgress, (
                (UserRoundProgress.round_id == Activity.id) & (UserRoundProgress.user_id == user_id)
            )).where(
                Activity.lesson_id == lesson_id,
                or_(UserRoundProgress.status.is_(None), UserRoundProgress.status != "completed"),
            ).order_by(Activity.round_number, Activity.id).limit(1)
        )
