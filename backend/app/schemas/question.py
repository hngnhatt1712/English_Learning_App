"""Public question allowlist and typed answers. No server payload in GET models."""
from typing import Annotated, Literal
from uuid import UUID

from pydantic import (
    AfterValidator,
    ConfigDict,
    Discriminator,
    Field,
    Tag,
    field_validator,
)
from typing_extensions import TypeAliasType

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


class MatchingCheckAnswerDto(Pair):
    type: Literal["matching"]


class MatchingSubmitAnswerDto(AnswerBase):
    type: Literal["matching"]
    pairs: list[Pair] = Field(min_length=1)
    attempts: list[Pair] = Field(default_factory=list)

    @field_validator("pairs")
    @classmethod
    def unique_final_pairs(cls, pairs: list[Pair]) -> list[Pair]:
        if len({pair.left_id for pair in pairs}) != len(pairs):
            raise ValueError("Final pairs must use each left ID at most once")
        if len({pair.right_id for pair in pairs}) != len(pairs):
            raise ValueError("Final pairs must use each right ID at most once")
        return pairs


class FillBlankCorrectAnswerDto(AnswerBase):
    type: Literal["fill_blank"]
    text: str


class FillBlankAnswerDto(FillBlankCorrectAnswerDto):
    text: str = Field(max_length=100)


def unique_token_ids(token_ids: list[str]) -> list[str]:
    if len(set(token_ids)) != len(token_ids):
        raise ValueError("Token IDs must be unique")
    return token_ids


TokenIds = Annotated[
    list[Identifier], AfterValidator(unique_token_ids), Field(json_schema_extra={"uniqueItems": True})
]


class TranslateAnswerDto(AnswerBase):
    type: Literal["translate"]
    token_ids: TokenIds


class TranslateTextAnswerDto(AnswerBase):
    type: Literal["translate"]
    text: str


def translate_input_mode(value: object) -> str:
    # Presence of tokenIds selects that branch even if its value is null.
    if isinstance(value, TranslateAnswerDto) or (
        isinstance(value, dict) and ("tokenIds" in value or "token_ids" in value)
    ):
        return "tokenIds"
    return "text"


# A named nested union lets OpenAPI map the outer "translate" tag to one schema.
TranslateRequestAnswerDto = TypeAliasType(
    "TranslateRequestAnswerDto",
    Annotated[
        Annotated[TranslateAnswerDto, Tag("tokenIds")] | Annotated[TranslateTextAnswerDto, Tag("text")],
        Discriminator(translate_input_mode),
    ],
)


class SentenceOrderAnswerDto(AnswerBase):
    type: Literal["sentence_order"]
    token_ids: TokenIds


class SentenceCorrectionAnswerDto(AnswerBase):
    type: Literal["sentence_correction"]
    option_id: Identifier


class SentenceCompletionAnswerDto(AnswerBase):
    type: Literal["sentence_completion"]
    option_id: Identifier


CommonAnswerDto = (
    MultipleChoiceAnswerDto | PictureChoiceAnswerDto | SentenceOrderAnswerDto
    | SentenceCorrectionAnswerDto | SentenceCompletionAnswerDto
)
CheckAnswerDto = Annotated[
    CommonAnswerDto | MatchingCheckAnswerDto | FillBlankAnswerDto | TranslateRequestAnswerDto,
    Field(discriminator="type"),
]
SubmitAnswerDto = Annotated[
    CommonAnswerDto | MatchingSubmitAnswerDto | FillBlankAnswerDto | TranslateRequestAnswerDto,
    Field(discriminator="type"),
]
CorrectAnswerDto = Annotated[
    CommonAnswerDto | FillBlankCorrectAnswerDto | TranslateAnswerDto,
    Field(discriminator="type"),
]


class CheckAnswerRequest(AnswerBase):
    answer: CheckAnswerDto


class QuestionResultBase(CamelModel):
    question_id: UUID
    is_correct: bool


class QuestionResultDto(QuestionResultBase):
    correct_answer: CorrectAnswerDto
    explanation: str | None


class MatchingCheckResultDto(QuestionResultBase):
    model_config = ConfigDict(extra="forbid")

    correct_answer: None
    explanation: None


class MatchingPairResultDto(CamelModel):
    left_id: Identifier
    is_correct: bool


class MatchingSubmitResultDto(MatchingCheckResultDto):
    pair_results: list[MatchingPairResultDto] = Field(min_length=1)


CheckQuestionResultDto = QuestionResultDto | MatchingCheckResultDto
SubmitQuestionResultDto = QuestionResultDto | MatchingSubmitResultDto
