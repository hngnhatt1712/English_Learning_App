"""Build stateless Vocabulary Quiz sets from accessible Topic content."""
import logging
import random
from uuid import UUID

from app.core.errors import AppError, ErrorType
from app.models.question import Question
from app.models.user import User
from app.repositories.quiz_repository import get_vocabulary_quiz_pool
from app.schemas.enums import ContentStatus
from app.schemas.question import FillBlankQuestionDto
from app.schemas.question_payload import (
    PictureChoicePayload,
    QuestionPayload,
    validate_payload,
)
from app.schemas.quiz import VocabularyQuizDto
from app.services.level_progress_provider import get_user_level_progress
from app.services.round_content import question_dto
from app.services.topic_service import get_topic_state
from app.utils.media import validate_media_path
from sqlalchemy.orm import Session

logger = logging.getLogger(__name__)


def require_vocabulary_quiz_topic(db: Session, user: User, topic_id: UUID) -> None:
    """Require the same Topic access for drawing a quiz and checking a question."""
    snapshot = get_user_level_progress(db, user)
    topic = get_topic_state(db, user.id, topic_id, snapshot=snapshot)
    if topic.status == ContentStatus.LOCKED:
        raise AppError(403, ErrorType.TOPIC_LOCKED, "Topic is locked")


def vocabulary_quiz_payload(question: Question) -> QuestionPayload:
    """Validate stored content before a question can be drawn or graded."""
    payload = validate_payload(question.type, question.payload)
    if not question.prompt.strip():
        raise ValueError("Prompt must not be blank")
    paths = [question.image_url, question.audio_url]
    if isinstance(payload, PictureChoicePayload):
        paths.extend(option.image_url for option in payload.options)
    for path in paths:
        if path is not None:
            validate_media_path(path)
    return payload


def get_vocabulary_quiz(db: Session, user: User, topic_id: UUID, count: int) -> VocabularyQuizDto:
    require_vocabulary_quiz_topic(db, user, topic_id)
    pool = []
    for question in get_vocabulary_quiz_pool(db, topic_id):
        try:
            payload = vocabulary_quiz_payload(question)
        except ValueError:
            logger.warning("Skipping invalid Vocabulary Quiz question %s", question.id)
        else:
            pool.append((question, payload))

    if len(pool) < count:
        raise AppError(422, ErrorType.VALIDATION_ERROR, "One or more fields failed validation", errors=[
            {"field": "count", "message": "Not enough valid questions for this topic", "code": "INVALID_VALUE"},
        ])

    questions = []
    for order, (question, payload) in enumerate(random.sample(pool, count), 1):
        dto = question_dto(question, payload, order, {})
        if isinstance(dto, FillBlankQuestionDto) and dto.word_bank is not None:
            bank = dto.word_bank.copy()
            random.shuffle(bank)
            dto = dto.model_copy(update={"word_bank": bank})
        questions.append(dto)
    return VocabularyQuizDto(topic_id=topic_id, total_questions=len(questions), questions=questions)
