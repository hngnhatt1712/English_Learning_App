"""Public vocabulary content and user-owned progress, contract 6.7."""
from typing import Literal
from uuid import UUID

from app.core.schemas import CamelModel

VocabularyStatus = Literal["new", "skipped", "in_flashcard", "learning", "review", "mastered"]
VocabularySort = Literal["word", "-word"]
SrsRating = Literal["again", "hard", "good", "easy"]
PartOfSpeech = Literal[
    "noun", "verb", "adjective", "adverb", "pronoun", "preposition",
    "conjunction", "interjection", "phrase", "other",
]


class VocabularyDto(CamelModel):
    id: UUID
    topic_id: UUID
    level_id: UUID
    word: str
    meaning: str
    part_of_speech: PartOfSpeech
    phonetic: str
    example: str | None
    image_url: str | None
    audio_url: str
    status: VocabularyStatus
    is_in_flashcard: bool
