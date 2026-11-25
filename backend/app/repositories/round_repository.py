"""Round content and user-owned progress storage."""
from collections.abc import Sequence
from uuid import UUID

from sqlalchemy import case, func, or_, select
from sqlalchemy.orm import Session

from app.models.activity import Activity
from app.models.lesson import Lesson
from app.models.progress import UserRoundProgress
from app.models.question import Question
from app.models.topic import Topic
from app.models.user import User


class RoundRepository:
    def __init__(self, db: Session) -> None:
        self.db = db

    def lock_submitter(self, user: User) -> None:
        # Serialize submissions for this user, including first inserts and streak writes.
        self.db.refresh(user, with_for_update=True)

    def progress_for_submit(self, user_id: UUID, round_id: UUID) -> UserRoundProgress:
        row = self.db.get(UserRoundProgress, (user_id, round_id))
        if row is None:
            row = UserRoundProgress(user_id=user_id, round_id=round_id, status="locked", attempts=0, stars=0)
            self.db.add(row)
        return row

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

    def scoring_unit_counts(self, round_ids: Sequence[UUID]) -> dict[UUID, int | None]:
        """Sum units per round; a missing, non-array or empty Matching left column invalidates its total."""
        if not round_ids:
            return {}
        left = Question.payload["left"]
        units = case(
            (Question.type != "matching", 1),
            (func.jsonb_typeof(left) == "array", func.jsonb_array_length(left)),
            else_=0,
        )
        total = case((func.min(units) > 0, func.sum(units)))
        rows = self.db.execute(
            select(Question.activity_id, total)
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
