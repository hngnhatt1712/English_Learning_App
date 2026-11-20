"""Public question allowlist and typed answers. No server payload in GET models."""
from typing import Annotated, Literal
from uuid import UUID

from pydantic import ConfigDict, Field

from app.core.schemas import CamelModel
from app.schemas.question_payload import Identifier, Pair
from app.schemas.vocabulary import VocabularyDto


class TextOptionDto(CamelModel):
    id: str
    text: str


class PictureOptionDto(CamelModel):
    id: str
    image_url: str


class MatchingItemDto(CamelModel):
    id: str
    text: str | None
    image_url: str | None


class QuestionBaseDto(CamelModel):
    id: UUID
    order: int = Field(ge=1)
    prompt: str
    image_url: str | None
    audio_url: str | None


class LearnCardQuestionDto(QuestionBaseDto):
    type: Literal["learn_card"]
    vocabulary: VocabularyDto


class MultipleChoiceQuestionDto(QuestionBaseDto):
    type: Literal["multiple_choice"]
    options: list[TextOptionDto]


class PictureChoiceQuestionDto(QuestionBaseDto):
    type: Literal["picture_choice"]
    options: list[PictureOptionDto]


class MatchingQuestionDto(QuestionBaseDto):
    type: Literal["matching"]
    left: list[MatchingItemDto]
    right: list[MatchingItemDto]


class FillBlankQuestionDto(QuestionBaseDto):
    type: Literal["fill_blank"]
    sentence: str
    word_bank: list[str] | None


class TranslateQuestionDto(QuestionBaseDto):
    type: Literal["translate"]
    source_text: str
    direction: Literal["en_to_vi", "vi_to_en"]
    tokens: list[TextOptionDto]


class SentenceOrderQuestionDto(QuestionBaseDto):
    type: Literal["sentence_order"]
    tokens: list[TextOptionDto]


class SentenceCorrectionQuestionDto(QuestionBaseDto):
    type: Literal["sentence_correction"]
    options: list[TextOptionDto]


class SentenceCompletionQuestionDto(QuestionBaseDto):
    type: Literal["sentence_completion"]
    sentence: str
    options: list[TextOptionDto]


QuestionDto = Annotated[
    LearnCardQuestionDto | MultipleChoiceQuestionDto | PictureChoiceQuestionDto
    | MatchingQuestionDto | FillBlankQuestionDto | TranslateQuestionDto
    | SentenceOrderQuestionDto | SentenceCorrectionQuestionDto | SentenceCompletionQuestionDto,
    Field(discriminator="type"),
]


class AnswerBase(CamelModel):
    model_config = ConfigDict(extra="forbid")


class MultipleChoiceAnswerDto(AnswerBase):
    type: Literal["multiple_choice"]
    option_id: Identifier


class PictureChoiceAnswerDto(AnswerBase):
    type: Literal["picture_choice"]
    option_id: Identifier


class MatchingAnswerDto(AnswerBase):
    type: Literal["matching"]
    pairs: list[Pair]


class FillBlankAnswerDto(AnswerBase):
    type: Literal["fill_blank"]
    text: str = Field(max_length=100)


class TranslateAnswerDto(AnswerBase):
    type: Literal["translate"]
    token_ids: list[Identifier]


class SentenceOrderAnswerDto(AnswerBase):
    type: Literal["sentence_order"]
    token_ids: list[Identifier]


class SentenceCorrectionAnswerDto(AnswerBase):
    type: Literal["sentence_correction"]
    option_id: Identifier


class SentenceCompletionAnswerDto(AnswerBase):
    type: Literal["sentence_completion"]
    option_id: Identifier


AnswerDto = Annotated[
    MultipleChoiceAnswerDto | PictureChoiceAnswerDto | MatchingAnswerDto
    | FillBlankAnswerDto | TranslateAnswerDto | SentenceOrderAnswerDto
    | SentenceCorrectionAnswerDto | SentenceCompletionAnswerDto,
    Field(discriminator="type"),
]


class CheckAnswerRequest(AnswerBase):
    answer: AnswerDto


class QuestionResultDto(CamelModel):
    question_id: UUID
    is_correct: bool
    correct_answer: AnswerDto
    explanation: str | None
