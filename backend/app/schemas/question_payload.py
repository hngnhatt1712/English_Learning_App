"""Server-only JSONB schemas, selected by Question.type (never stored in payload)."""
from typing import Annotated, Literal, Self
from uuid import UUID

from pydantic import AfterValidator, ConfigDict, Field, model_validator

from app.core.schemas import CamelModel
from app.utils.answer_text import normalize_answer_text
from app.utils.media import validate_media_path

Identifier = Annotated[str, Field(min_length=1)]
NonEmptyText = Annotated[str, Field(min_length=1)]
MediaPath = Annotated[str, AfterValidator(validate_media_path)]


def non_blank_answer(text: str) -> str:
    if not normalize_answer_text(text):
        raise ValueError("Accepted answers must contain non-whitespace text")
    return text


AcceptedAnswers = Annotated[list[Annotated[str, AfterValidator(non_blank_answer)]], Field(min_length=1)]
TokenSequence = Annotated[list[Identifier], Field(min_length=1)]


class PayloadModel(CamelModel):
    model_config = ConfigDict(extra="forbid")


class TextOption(PayloadModel):
    id: Identifier
    text: NonEmptyText


class PictureOption(PayloadModel):
    id: Identifier
    image_url: MediaPath


class MatchingItem(PayloadModel):
    id: Identifier
    text: str | None = None
    image_url: MediaPath | None = None

    @model_validator(mode="after")
    def content_present(self) -> Self:
        if not self.text and not self.image_url:
            raise ValueError("Matching item needs text or an image")
        return self


class Pair(PayloadModel):
    left_id: Identifier
    right_id: Identifier


def unique_ids(items: list[TextOption] | list[PictureOption] | list[MatchingItem]) -> set[str]:
    ids = {item.id for item in items}
    if len(ids) != len(items):
        raise ValueError("Item IDs must be unique")
    return ids


class LearnCardPayload(PayloadModel):
    vocabulary_id: UUID


class TextChoicePayload(PayloadModel):
    options: list[TextOption] = Field(min_length=1)
    correct_option_id: Identifier

    @model_validator(mode="after")
    def valid_answer(self) -> Self:
        if self.correct_option_id not in unique_ids(self.options):
            raise ValueError("Correct option must belong to options")
        return self


class PictureChoicePayload(PayloadModel):
    options: list[PictureOption] = Field(min_length=1)
    correct_option_id: Identifier

    @model_validator(mode="after")
    def valid_answer(self) -> Self:
        if self.correct_option_id not in unique_ids(self.options):
            raise ValueError("Correct option must belong to options")
        return self


class SentenceCompletionPayload(TextChoicePayload):
    sentence: NonEmptyText

    @model_validator(mode="after")
    def has_blank(self) -> Self:
        if "___" not in self.sentence:
            raise ValueError("Sentence must contain ___")
        return self


class MatchingPayload(PayloadModel):
    left: list[MatchingItem] = Field(min_length=1)
    right: list[MatchingItem] = Field(min_length=1)
    correct_pairs: list[Pair] = Field(min_length=1)

    @model_validator(mode="after")
    def valid_pairs(self) -> Self:
        left, right = unique_ids(self.left), unique_ids(self.right)
        if len(left) != len(right):
            raise ValueError("Matching columns must have equal length")
        if (
            len(self.correct_pairs) != len(left)
            or {pair.left_id for pair in self.correct_pairs} != left
            or {pair.right_id for pair in self.correct_pairs} != right
        ):
            raise ValueError("Correct pairs must use every ID in each column exactly once")
        return self


class FillBlankPayload(PayloadModel):
    sentence: NonEmptyText
    word_bank: list[str] | None
    accepted_answers: AcceptedAnswers

    @model_validator(mode="after")
    def valid_blank(self) -> Self:
        if "___" not in self.sentence:
            raise ValueError("Sentence must contain ___")
        if self.word_bank is not None:
            accepted = set(map(normalize_answer_text, self.accepted_answers))
            if accepted.isdisjoint(map(normalize_answer_text, self.word_bank)):
                raise ValueError("Word bank must include at least one accepted answer")
        return self


class TokenSequencePayload(PayloadModel):
    tokens: list[TextOption] = Field(min_length=1)
    accepted_sequences: list[TokenSequence] = Field(min_length=1)

    @model_validator(mode="after")
    def valid_sequences(self) -> Self:
        ids = unique_ids(self.tokens)
        for sequence in self.accepted_sequences:
            sequence_ids = set(sequence)
            if len(sequence_ids) != len(sequence) or not sequence_ids.issubset(ids):
                raise ValueError("Accepted sequences must use unique token IDs belonging to the question")
        return self


class SentenceOrderPayload(TokenSequencePayload):
    @model_validator(mode="after")
    def uses_all_tokens(self) -> Self:
        if any(len(sequence) != len(self.tokens) for sequence in self.accepted_sequences):
            raise ValueError("Every accepted sentence order must use all token IDs")
        return self


class TranslatePayload(TokenSequencePayload):
    source_text: NonEmptyText
    direction: Literal["en_to_vi", "vi_to_en"]
    accepted_answers: AcceptedAnswers


QuestionPayload = (
    LearnCardPayload | TextChoicePayload | PictureChoicePayload | MatchingPayload
    | FillBlankPayload | TranslatePayload | SentenceOrderPayload | SentenceCompletionPayload
)
PAYLOAD_SCHEMAS: dict[str, type[PayloadModel]] = {
    "learn_card": LearnCardPayload,
    "multiple_choice": TextChoicePayload,
    "picture_choice": PictureChoicePayload,
    "matching": MatchingPayload,
    "fill_blank": FillBlankPayload,
    "translate": TranslatePayload,
    "sentence_order": SentenceOrderPayload,
    "sentence_correction": TextChoicePayload,
    "sentence_completion": SentenceCompletionPayload,
}


def validate_payload(question_type: str, payload: object) -> QuestionPayload:
    """Validate seed/input payload; runtime translates validation failure into a safe 500."""
    schema = PAYLOAD_SCHEMAS.get(question_type)
    if schema is None:
        raise ValueError("Unknown question type")
    # Each concrete model is typed above; no unvalidated dict escapes this boundary.
    from typing import cast

    return cast(QuestionPayload, schema.model_validate(payload))
