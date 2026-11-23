"""Round detail and Sprint 5–6 request/result shapes (contract 7.5)."""
from typing import Literal
from uuid import UUID

from pydantic import Field

from app.core.schemas import CamelModel
from app.schemas.lesson import RoundDto
from app.schemas.question import (
    AnswerBase,
    QuestionDto,
    SubmitAnswerDto,
    SubmitQuestionResultDto,
)
from app.schemas.streak import StreakDto
from app.schemas.vocabulary import VocabularyStatus


class RoundDetailDto(RoundDto):
    questions: list[QuestionDto]


class SubmitRoundAnswer(AnswerBase):
    question_id: UUID
    answer: SubmitAnswerDto


class SubmitRoundRequest(AnswerBase):
    answers: list[SubmitRoundAnswer]


class UnlocksDto(CamelModel):
    next_round_id: UUID | None
    topic_completed: bool
    next_topic_id: UUID | None
    level_completed: bool
    next_level_id: UUID | None


class RoundResultDto(CamelModel):
    round_id: UUID
    total_questions: int = Field(ge=1)
    correct_count: int = Field(ge=0)
    score_percent: float = Field(ge=0, le=100)
    passed: bool
    pass_threshold_percent: Literal[70] = 70
    stars: int = Field(ge=0, le=3)
    best_stars: int = Field(ge=0, le=3)
    best_score_percent: float | None = Field(ge=0, le=100)
    is_new_best: bool
    attempts: int = Field(ge=0)
    results: list[SubmitQuestionResultDto]
    unlocks: UnlocksDto
    streak: StreakDto


class VocabularyActionRequest(AnswerBase):
    vocabulary_id: UUID
    action: Literal["skip", "flashcard"]


class VocabularyActionResultDto(CamelModel):
    vocabulary_id: UUID
    status: VocabularyStatus
    is_in_flashcard: bool
