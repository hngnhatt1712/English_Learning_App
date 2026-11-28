"""Read question pools for Practice quizzes."""
from uuid import UUID

from app.models.question import Question
from sqlalchemy import select
from sqlalchemy.orm import Session

VOCABULARY_QUIZ_TYPES = ("multiple_choice", "picture_choice", "fill_blank", "translate")


def get_vocabulary_quiz_pool(
    db: Session, topic_id: UUID, *, question_ids: list[UUID] | None = None,
) -> list[Question]:
    statement = select(Question).where(
        Question.source == "vocabulary_quiz",
        Question.topic_id == topic_id,
        Question.type.in_(VOCABULARY_QUIZ_TYPES),
    ).order_by(Question.id)
    if question_ids is not None:
        statement = statement.where(Question.id.in_(question_ids))
    return list(db.scalars(statement))
