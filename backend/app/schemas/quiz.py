"""Practice quiz requests and results."""
from datetime import datetime
from typing import Literal
from uuid import UUID

from app.core.schemas import CamelModel
from app.schemas.question import AnswerBase, QuestionDto, QuestionResultDto
from app.schemas.round import SubmitRoundAnswer
from app.schemas.streak import StreakDto
from pydantic import Field


class VocabularyQuizDto(CamelModel):
    topic_id: UUID
    total_questions: int
    questions: list[QuestionDto]


class SubmitVocabularyQuizRequest(AnswerBase):
    topic_id: UUID
    answers: list[SubmitRoundAnswer] = Field(min_length=5, max_length=20)


class QuizResultDto(CamelModel):
    quiz_attempt_id: UUID
    kind: Literal["vocabulary_quiz", "grammar_quiz"]
    total_questions: int
    correct_count: int
    score_percent: float
    passed: bool
    results: list[QuestionResultDto]
    streak: StreakDto
    completed_at: datetime
