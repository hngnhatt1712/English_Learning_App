"""Read saved flashcards and record ratings for the authenticated user."""
from datetime import datetime, timezone
from uuid import UUID

from app.core.errors import AppError, ErrorType
from app.models.user import User
from app.repositories.topic_repository import TopicRepository
from app.repositories.vocabulary_repository import VocabularyRepository
from app.schemas.flashcard import (
    FlashcardDto,
    FlashcardLibraryItemDto,
    FlashcardLibrarySort,
    FlashcardReviewRequest,
    FlashcardReviewResultDto,
)
from app.services import streak_service
from app.services.round_content import content_error, vocabulary_dto
from app.services.spaced_repetition_service import calculate_review


def list_flashcard_library(
    repository: VocabularyRepository, user_id: UUID, *, topic_id: UUID | None,
    search: str | None, sort: FlashcardLibrarySort | None, page: int, limit: int,
) -> tuple[list[FlashcardLibraryItemDto], int]:
    if topic_id is not None and TopicRepository(repository.db).get_by_id(topic_id) is None:
        raise AppError(404, ErrorType.NOT_FOUND, "Topic not found")
    cards, total = repository.list_flashcard_library(
        user_id, topic_id=topic_id, search=(search or "").strip(),
        sort=sort, offset=(page - 1) * limit, limit=limit,
    )
    items = []
    for word, progress, topic_name in cards:
        vocabulary = vocabulary_dto(word, progress)
        items.append(FlashcardLibraryItemDto(
            id=vocabulary.id,
            topic_id=vocabulary.topic_id,
            topic_name=topic_name,
            word=vocabulary.word,
            meaning=vocabulary.meaning,
            part_of_speech=vocabulary.part_of_speech,
            phonetic=vocabulary.phonetic,
            example=vocabulary.example,
            image_url=vocabulary.image_url,
            audio_url=vocabulary.audio_url,
            added_at=progress.added_to_flashcard_at,
            status=vocabulary.status,
            next_review_at=progress.next_review,
        ))
    return items, total


def list_due_flashcards(
    repository: VocabularyRepository, user_id: UUID, *, page: int, limit: int,
) -> tuple[list[FlashcardDto], int]:
    cards, total = repository.list_due_flashcards(
        user_id, datetime.now(timezone.utc), offset=(page - 1) * limit, limit=limit,
    )
    items = []
    for word, progress in cards:
        next_review = progress.next_review
        if next_review is None:
            raise content_error()
        items.append(FlashcardDto(
            vocabulary=vocabulary_dto(word, progress),
            repetition_count=progress.repetition_count,
            interval_days=progress.interval,
            ease_factor=float(progress.ease_factor),
            last_reviewed_at=progress.last_reviewed,
            next_review_at=next_review,
        ))
    return items, total


def rate_flashcard(
    repository: VocabularyRepository, user: User, body: FlashcardReviewRequest,
) -> FlashcardReviewResultDto:
    # Authentication already opened this request's transaction.
    try:
        card = repository.lock_flashcard_for_review(user, body.vocabulary_id)
        if card is None:
            raise AppError(404, ErrorType.NOT_FOUND, "Flashcard not found")

        now = datetime.now(timezone.utc)
        schedule = calculate_review(
            repetition_count=card.repetition_count, interval_days=card.interval,
            ease_factor=card.ease_factor, rating=body.rating, reviewed_at=now,
        )
        card.status = schedule.status
        card.repetition_count = schedule.repetition_count
        card.interval = schedule.interval_days
        card.ease_factor = schedule.ease_factor
        card.next_review = schedule.next_review_at
        card.last_reviewed = schedule.last_reviewed_at
        streak = streak_service.touch(user, today=streak_service.today_vn(now))
        result = FlashcardReviewResultDto(
            vocabulary_id=card.vocabulary_id, status=schedule.status, repetition_count=card.repetition_count,
            interval_days=card.interval, ease_factor=float(card.ease_factor), next_review_at=schedule.next_review_at,
            streak=streak,
        )
        repository.db.commit()
        return result
    except Exception:
        repository.db.rollback()
        raise
