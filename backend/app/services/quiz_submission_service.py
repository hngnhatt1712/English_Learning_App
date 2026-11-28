"""Grade a stateless Vocabulary Quiz and persist its history and streak together."""
from datetime import UTC, datetime
from decimal import Decimal
from uuid import UUID

from app.models.quiz import QuizAnswer, QuizAttempt
from app.models.user import User
from app.repositories.quiz_repository import get_vocabulary_quiz_pool
from app.repositories.round_repository import RoundRepository
from app.schemas.question import QuestionResultDto
from app.schemas.quiz import QuizResultDto, SubmitVocabularyQuizRequest
from app.services import streak_service
from app.services.quiz_service import (
    require_vocabulary_quiz_topic,
    vocabulary_quiz_payload,
)
from app.services.round_service import grade_question, invalid, score
from sqlalchemy.orm import Session


def _grade_answers(db: Session, request: SubmitVocabularyQuizRequest) -> list[QuestionResultDto]:
    ids = [item.question_id for item in request.answers]
    questions = {q.id: q for q in get_vocabulary_quiz_pool(db, request.topic_id, question_ids=ids)}
    results: list[QuestionResultDto] = []
    seen: set[UUID] = set()
    for index, item in enumerate(request.answers):
        field = f"answers.{index}"
        if item.question_id in seen:
            raise invalid(field + ".questionId", "Question appears more than once")
        seen.add(item.question_id)
        question = questions.get(item.question_id)
        if question is None:
            raise invalid(field + ".questionId", "Question is not in this topic's quiz pool")
        try:
            payload = vocabulary_quiz_payload(question)
        except ValueError as exc:
            raise invalid(field + ".questionId", "Question is not in this topic's quiz pool") from exc
        results.append(grade_question(question, payload, item.answer, field + ".answer"))
    return results


def submit_vocabulary_quiz(db: Session, user: User, request: SubmitVocabularyQuizRequest) -> QuizResultDto:
    try:
        # Authentication has already opened this transaction. Serialize streak writers on the same user row.
        RoundRepository(db).lock_submitter(user)
        require_vocabulary_quiz_topic(db, user, request.topic_id)
        results = _grade_answers(db, request)
        total = len(results)
        correct = sum(result.is_correct for result in results)
        percent, passed, _ = score(correct, total)
        completed_at = datetime.now(UTC)
        attempt = QuizAttempt(
            user_id=user.id, topic_id=request.topic_id, kind="vocabulary_quiz",
            total_questions=total, correct_count=correct, score_percent=Decimal(str(percent)),
            completed_at=completed_at,
            answers=[
                QuizAnswer(question_id=item.question_id, is_correct=result.is_correct,
                           answer=item.answer.model_dump(mode="json", by_alias=True))
                for item, result in zip(request.answers, results, strict=True)
            ],
        )
        db.add(attempt)
        db.flush()
        streak = streak_service.touch(user, today=streak_service.today_vn(completed_at))
        response = QuizResultDto(
            quiz_attempt_id=attempt.id, kind="vocabulary_quiz", total_questions=total,
            correct_count=correct, score_percent=percent, passed=passed, results=results,
            streak=streak, completed_at=completed_at,
        )
        db.commit()
        return response
    except Exception:
        db.rollback()
        raise
