"""Validate stored content and construct separate, allowlisted public values."""
import random
from collections.abc import Mapping
from uuid import UUID

from pydantic import TypeAdapter, ValidationError

from app.core.errors import AppError, ErrorType
from app.models.progress import UserVocabularyProgress
from app.models.question import Question
from app.models.vocabulary import Vocabulary
from app.schemas.question import QuestionDto
from app.schemas.question_payload import (
    FillBlankPayload,
    LearnCardPayload,
    MatchingPayload,
    PictureChoicePayload,
    QuestionPayload,
    SentenceCompletionPayload,
    SentenceOrderPayload,
    TextChoicePayload,
    TranslatePayload,
    validate_payload,
)
from app.schemas.vocabulary import VocabularyDto
from app.utils.media import media_url

_QUESTION_ADAPTER = TypeAdapter(QuestionDto)


def content_error() -> AppError:
    return AppError(500, ErrorType.INTERNAL_ERROR, "Learning content is unavailable")


def stored_payload(question: Question) -> QuestionPayload:
    try:
        return validate_payload(question.type, question.payload)
    except (ValueError, ValidationError):
        # Neither Pydantic's input dump nor the internal answer reaches clients/logs.
        raise content_error() from None


def vocabulary_dto(word: Vocabulary, progress: UserVocabularyProgress | None) -> VocabularyDto:
    try:
        if not word.phonetic.strip() or not word.audio_url:
            raise ValueError("Required vocabulary media is missing")
        return VocabularyDto.model_validate({
            "id": word.id, "topic_id": word.topic_id, "level_id": word.level_id,
            "word": word.word, "meaning": word.meaning, "part_of_speech": word.part_of_speech,
            "phonetic": word.phonetic, "example": word.example,
            "image_url": media_url(word.image_url), "audio_url": media_url(word.audio_url),
            "status": progress.status if progress else "new",
            "is_in_flashcard": progress.is_in_flashcard if progress else False,
        })
    except (ValueError, ValidationError):
        raise content_error() from None


def question_dto(
    question: Question, payload: QuestionPayload, order: int,
    vocabulary: Mapping[UUID, VocabularyDto],
) -> QuestionDto:
    """Create every nested output anew; shuffle never touches JSONB/ORM collections."""
    try:
        data: dict[str, object] = {
            "id": question.id, "type": question.type, "order": order, "prompt": question.prompt,
            "image_url": media_url(question.image_url), "audio_url": media_url(question.audio_url),
        }
        if isinstance(payload, LearnCardPayload):
            data["vocabulary"] = vocabulary[payload.vocabulary_id]
        elif isinstance(payload, PictureChoicePayload):
            options = [{"id": item.id, "image_url": media_url(item.image_url)} for item in payload.options]
            random.shuffle(options)
            data["options"] = options
        elif isinstance(payload, TextChoicePayload):
            text_options = [{"id": item.id, "text": item.text} for item in payload.options]
            random.shuffle(text_options)
            data["options"] = text_options
            if isinstance(payload, SentenceCompletionPayload):
                data["sentence"] = payload.sentence
        elif isinstance(payload, MatchingPayload):
            data["left"] = [
                {"id": item.id, "text": item.text, "image_url": media_url(item.image_url)}
                for item in payload.left
            ]
            right = [
                {"id": item.id, "text": item.text, "image_url": media_url(item.image_url)}
                for item in payload.right
            ]
            random.shuffle(right)
            data["right"] = right
        elif isinstance(payload, FillBlankPayload):
            data.update(sentence=payload.sentence, word_bank=payload.word_bank)
        elif isinstance(payload, SentenceOrderPayload):
            tokens = [{"id": item.id, "text": item.text} for item in payload.tokens]
            random.shuffle(tokens)
            data["tokens"] = tokens
            if isinstance(payload, TranslatePayload):
                data.update(source_text=payload.source_text, direction=payload.direction)
        return _QUESTION_ADAPTER.validate_python(data)
    except (ValueError, KeyError, ValidationError):
        raise content_error() from None
