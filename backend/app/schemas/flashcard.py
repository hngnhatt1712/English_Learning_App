"""Flashcard library and review schemas."""
from typing import Literal
from uuid import UUID

from pydantic import ConfigDict

from app.core.schemas import CamelModel
from app.schemas.streak import StreakDto
from app.schemas.types import UtcDatetime
from app.schemas.vocabulary import (
    PartOfSpeech,
    SrsRating,
    VocabularyDto,
    VocabularyStatus,
)

FlashcardLibrarySort = Literal["-addedAt", "addedAt", "word"]


class FlashcardLibraryItemDto(CamelModel):
    id: UUID
    topic_id: UUID
    topic_name: str
    word: str
    meaning: str
    part_of_speech: PartOfSpeech
    phonetic: str
    example: str | None
    image_url: str | None
    audio_url: str
    added_at: UtcDatetime | None
    status: VocabularyStatus
    next_review_at: UtcDatetime | None


class FlashcardDto(CamelModel):
    vocabulary: VocabularyDto
    repetition_count: int
    interval_days: int
    ease_factor: float
    last_reviewed_at: UtcDatetime | None
    next_review_at: UtcDatetime


class FlashcardReviewRequest(CamelModel):
    model_config = ConfigDict(extra="forbid", validate_by_name=False)

    vocabulary_id: UUID
    rating: SrsRating


class FlashcardReviewResultDto(CamelModel):
    vocabulary_id: UUID
    status: VocabularyStatus
    repetition_count: int
    interval_days: int
    ease_factor: float
    next_review_at: UtcDatetime
    streak: StreakDto
