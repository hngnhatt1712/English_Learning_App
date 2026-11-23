"""Round content, supported question grading, and vocabulary actions."""
import random
from dataclasses import dataclass
from datetime import UTC, datetime
from decimal import Decimal
from uuid import UUID

from sqlalchemy.orm import Session

from app.core.errors import AppError, ErrorType
from app.models.activity import Activity
from app.models.progress import UserRoundProgress
from app.models.question import Question
from app.models.user import User
from app.repositories.round_repository import RoundRepository
from app.repositories.vocabulary_repository import VocabularyRepository
from app.schemas.enums import ContentStatus
from app.schemas.lesson import RoundDto
from app.schemas.question import (
    CheckAnswerDto,
    CheckAnswerRequest,
    CheckQuestionResultDto,
    CorrectAnswerDto,
    FillBlankAnswerDto,
    FillBlankCorrectAnswerDto,
    MatchingCheckAnswerDto,
    MatchingCheckResultDto,
    MatchingPairResultDto,
    MatchingSubmitAnswerDto,
    MatchingSubmitResultDto,
    MultipleChoiceAnswerDto,
    PictureChoiceAnswerDto,
    QuestionResultDto,
    SentenceOrderAnswerDto,
    SubmitAnswerDto,
    SubmitQuestionResultDto,
    TranslateAnswerDto,
    TranslateTextAnswerDto,
)
from app.schemas.question_payload import (
    FillBlankPayload,
    LearnCardPayload,
    MatchingPayload,
    PictureChoicePayload,
    QuestionPayload,
    SentenceOrderPayload,
    TextChoicePayload,
    TokenSequencePayload,
    TranslatePayload,
)
from app.schemas.round import (
    RoundDetailDto,
    RoundResultDto,
    SubmitRoundRequest,
    UnlocksDto,
    VocabularyActionRequest,
    VocabularyActionResultDto,
)
from app.schemas.streak import StreakDto
from app.services.level_progress_provider import get_user_level_progress
from app.services.round_content import (
    content_error,
    question_dto,
    scoring_units,
    stored_payload,
    vocabulary_dto,
)
from app.services.streak_service import effective_streak, today_vn, touch
from app.services.topic_service import get_topic_states_for_level
from app.utils.answer_text import normalize_answer_text

GRADED_QUESTION_TYPES_BY_ROUND = {
    "recognition": frozenset({"multiple_choice", "picture_choice"}),
    "matching": frozenset({"matching"}),
    "application": frozenset({"fill_blank", "translate", "sentence_order"}),
}


def invalid(field: str, message: str, code: str = "INVALID_VALUE") -> AppError:
    return AppError(422, ErrorType.VALIDATION_ERROR, "One or more fields failed validation", errors=[
        {"field": field, "message": message, "code": code},
    ])


def round_dto(
    activity: Activity, progress: UserRoundProgress | None,
    previous: UserRoundProgress | None, topic_open: bool, total: int,
) -> RoundDto:
    status = ContentStatus.LOCKED
    if topic_open:
        if progress is not None and progress.status == "completed":
            status = ContentStatus.COMPLETED
        elif activity.round_number == 1 or (previous is not None and previous.status == "completed"):
            status = ContentStatus.UNLOCKED
    return RoundDto.model_validate({
        "id": activity.id, "lesson_id": activity.lesson_id,
        "round_number": activity.round_number, "type": activity.round_type, "title": activity.title,
        "status": status, "stars": progress.stars if progress else 0,
        "best_score_percent": float(progress.best_score) if progress and progress.best_score is not None else None,
        "attempts": progress.attempts if progress else 0, "total_questions": total,
    })


@dataclass
class AccessibleRound:
    activity: Activity
    topic_id: UUID
    metadata: RoundDto


def _matches_text(text: str, accepted_answers: list[str]) -> bool:
    normalized = normalize_answer_text(text)
    return any(normalized == normalize_answer_text(accepted) for accepted in accepted_answers)


def _matches_tokens(payload: TokenSequencePayload, token_ids: list[str], field: str) -> bool:
    allowed = {token.id for token in payload.tokens}
    if not allowed.issuperset(token_ids):
        raise invalid(field + ".tokenIds", "Token ID does not belong to this question")
    return token_ids in payload.accepted_sequences


def grade_question(
    question: Question, payload: QuestionPayload, answer: CheckAnswerDto | SubmitAnswerDto | None, field: str,
) -> QuestionResultDto:
    if answer is not None and answer.type != question.type:
        raise invalid(field + ".type", "Answer type must match question type")

    correct_answer: CorrectAnswerDto
    if isinstance(payload, (TextChoicePayload, PictureChoicePayload)):
        option = answer.option_id if isinstance(answer, (MultipleChoiceAnswerDto, PictureChoiceAnswerDto)) else None
        if option is not None and option not in {item.id for item in payload.options}:
            raise invalid(field + ".optionId", "Option does not belong to this question")
        is_correct = option == payload.correct_option_id
        if isinstance(payload, PictureChoicePayload):
            correct_answer = PictureChoiceAnswerDto(type="picture_choice", option_id=payload.correct_option_id)
        else:
            correct_answer = MultipleChoiceAnswerDto(type="multiple_choice", option_id=payload.correct_option_id)
    elif isinstance(payload, FillBlankPayload):
        is_correct = isinstance(answer, FillBlankAnswerDto) and _matches_text(answer.text, payload.accepted_answers)
        correct_answer = FillBlankCorrectAnswerDto(type="fill_blank", text=payload.accepted_answers[0])
    elif isinstance(payload, SentenceOrderPayload):
        is_correct = isinstance(answer, SentenceOrderAnswerDto) and _matches_tokens(payload, answer.token_ids, field)
        correct_answer = SentenceOrderAnswerDto(type="sentence_order", token_ids=payload.accepted_sequences[0])
    elif isinstance(payload, TranslatePayload):
        if isinstance(answer, TranslateTextAnswerDto):
            is_correct = _matches_text(answer.text, payload.accepted_answers)
        else:
            is_correct = isinstance(answer, TranslateAnswerDto) and _matches_tokens(payload, answer.token_ids, field)
        correct_answer = TranslateAnswerDto(type="translate", token_ids=payload.accepted_sequences[0])
    else:
        raise content_error()

    return QuestionResultDto(
        question_id=question.id,
        is_correct=is_correct,
        correct_answer=correct_answer, explanation=question.explanation,
    )


def _grade_matching(
    question_id: UUID, payload: MatchingPayload, answer: SubmitAnswerDto | None, field: str,
) -> MatchingSubmitResultDto:
    expected = {pair.left_id: pair.right_id for pair in payload.correct_pairs}
    first: dict[str, str] = {}
    if answer is not None:
        if not isinstance(answer, MatchingSubmitAnswerDto):
            raise invalid(field + ".type", "Answer type must match question type")
        # The request DTO already enforces distinct left and right IDs in final pairs.
        if len(answer.pairs) != len(payload.left):
            raise invalid(field + ".pairs", "Final pairs must cover every left item")
        right_ids = set(expected.values())
        # Validate even later attempts and final pairs that do not contribute a point.
        for name, pairs in (("attempts", answer.attempts), ("pairs", answer.pairs)):
            for index, pair in enumerate(pairs):
                if pair.left_id not in expected:
                    raise invalid(f"{field}.{name}.{index}.leftId", "Item does not belong to the left column")
                if pair.right_id not in right_ids:
                    raise invalid(f"{field}.{name}.{index}.rightId", "Item does not belong to the right column")
                first.setdefault(pair.left_id, pair.right_id)
    units = [
        MatchingPairResultDto(left_id=item.id, is_correct=first.get(item.id) == expected[item.id])
        for item in payload.left
    ]
    return MatchingSubmitResultDto(
        question_id=question_id, is_correct=all(unit.is_correct for unit in units),
        correct_answer=None, explanation=None, pair_results=units,
    )


def grade_submitted_questions(
    questions: list[Question], payloads: dict[UUID, QuestionPayload], body: SubmitRoundRequest,
) -> tuple[list[SubmitQuestionResultDto], int]:
    """Grade the fixed question set, including omitted answers, and count correct units."""
    received: dict[UUID, tuple[SubmitAnswerDto, str]] = {}
    for index, item in enumerate(body.answers):
        if item.question_id not in payloads or item.question_id in received:
            raise invalid(f"answers.{index}.questionId", "Question must belong to this round and be unique")
        received[item.question_id] = item.answer, f"answers.{index}.answer"

    results: list[SubmitQuestionResultDto] = []
    correct = 0
    for question in questions:
        answer, field = received.get(question.id, (None, "answers"))
        payload = payloads[question.id]
        if isinstance(payload, MatchingPayload):
            matching = _grade_matching(question.id, payload, answer, field)
            correct += sum(pair.is_correct for pair in matching.pair_results)
            results.append(matching)
        else:
            result = grade_question(question, payload, answer, field)
            correct += result.is_correct
            results.append(result)
    return results, correct


def score(correct: int, total: int) -> tuple[float, bool, int]:
    if total <= 0:
        raise content_error()
    percent = ((correct * 1000) // total) / 10
    passed = correct * 100 >= total * 70
    stars = 3 if correct == total else 2 if correct * 100 >= total * 80 else 1 if passed else 0
    return percent, passed, stars


class RoundService:
    """Round operations sharing the authenticated request's database session."""

    def __init__(self, db: Session, rounds: RoundRepository, vocabularies: VocabularyRepository) -> None:
        self.db = db
        self.rounds = rounds
        self.vocabularies = vocabularies

    def _accessible_round(self, user: User, round_id: UUID) -> AccessibleRound:
        context = self.rounds.get_context(round_id)
        if context is None:
            raise AppError(404, ErrorType.NOT_FOUND, "Round not found")
        activity, lesson, topic = context
        snapshot = get_user_level_progress(self.db, user)
        states = get_topic_states_for_level(self.db, user.id, topic.level_id, snapshot=snapshot)
        topic_open = any(state.topic.id == topic.id and state.status != ContentStatus.LOCKED for state in states)
        activities = self.rounds.get_by_lesson(lesson.id)
        progress = self.rounds.progress_by_ids(user.id, [item.id for item in activities])
        previous = next((item for item in activities if item.round_number == activity.round_number - 1), None)
        metadata = round_dto(
            activity, progress.get(activity.id), progress.get(previous.id) if previous else None, topic_open, 0,
        )
        if metadata.status == ContentStatus.LOCKED:
            raise AppError(403, ErrorType.ROUND_LOCKED, "Round is locked")
        return AccessibleRound(activity, topic.id, metadata)

    def _round_content(self, user: User, current: AccessibleRound):
        questions = self.rounds.questions(current.activity.id)
        payloads = {question.id: stored_payload(question) for question in questions}
        ids = list({payload.vocabulary_id for payload in payloads.values() if isinstance(payload, LearnCardPayload)})
        words = self.vocabularies.get_by_ids(ids)
        if len(words) != len(ids) or any(word.topic_id != current.topic_id for word in words.values()):
            raise content_error()
        progress = self.vocabularies.progress_by_ids(user.id, ids)
        return questions, payloads, words, progress

    def get_round(self, user: User, round_id: UUID) -> RoundDetailDto:
        current = self._accessible_round(user, round_id)
        questions, payloads, words, progress = self._round_content(user, current)
        vocabulary = {key: vocabulary_dto(word, progress.get(key)) for key, word in words.items()}
        random.shuffle(questions)
        return RoundDetailDto(
            **current.metadata.model_dump(exclude={"total_questions"}),
            total_questions=sum(scoring_units(payload) for payload in payloads.values()),
            questions=[question_dto(question, payloads[question.id], index, vocabulary)
                       for index, question in enumerate(questions, 1)],
        )

    def check_answer(self, user: User, question_id: UUID, body: CheckAnswerRequest) -> CheckQuestionResultDto:
        question = self.rounds.question_by_id(question_id)
        if question is None:
            raise AppError(404, ErrorType.NOT_FOUND, "Question not found")
        if question.source != "round" or question.activity_id is None:
            # Grammar/Vocabulary Quiz have no runtime access policy in this sprint.
            # Fail closed before parsing or returning their answers.
            raise AppError(403, ErrorType.FORBIDDEN, "Question source is not available")
        current = self._accessible_round(user, question.activity_id)
        payload = stored_payload(question)
        if isinstance(payload, MatchingPayload):
            answer = body.answer
            if not isinstance(answer, MatchingCheckAnswerDto):
                raise invalid("answer.type", "Answer type must match question type")
            if not any(item.id == answer.left_id for item in payload.left):
                raise invalid("answer.leftId", "Item does not belong to the left column")
            if not any(item.id == answer.right_id for item in payload.right):
                raise invalid("answer.rightId", "Item does not belong to the right column")
            return MatchingCheckResultDto(
                question_id=question.id, correct_answer=None, explanation=None,
                is_correct=any(pair.left_id == answer.left_id and pair.right_id == answer.right_id
                               for pair in payload.correct_pairs),
            )
        # Recognition questions retain their existing check behavior.
        if question.type not in GRADED_QUESTION_TYPES_BY_ROUND["recognition"] and (
            current.activity.round_type != "application"
            or question.type not in GRADED_QUESTION_TYPES_BY_ROUND["application"]
        ):
            raise invalid("answer.type", "Question checking is not available for this type")
        return grade_question(question, payload, body.answer, "answer")

    def submit_round(self, user: User, round_id: UUID, body: SubmitRoundRequest) -> RoundResultDto:
        try:
            self.rounds.lock_submitter(user)
            result = self._grade_and_record(user, round_id, body)
            self.db.commit()
            return result
        except Exception:
            self.db.rollback()
            raise

    def _grade_and_record(self, user: User, round_id: UUID, body: SubmitRoundRequest) -> RoundResultDto:
        current = self._accessible_round(user, round_id)
        questions, payloads, _words, progress = self._round_content(user, current)
        if not questions:
            raise content_error()
        results: list[SubmitQuestionResultDto] = []
        if current.activity.round_type == "learn":
            if body.answers:
                raise invalid("answers", "Learn rounds require an empty answers list")
            if any(not isinstance(payload, LearnCardPayload) for payload in payloads.values()):
                raise content_error()
            ids = [payload.vocabulary_id for payload in payloads.values() if isinstance(payload, LearnCardPayload)]
            if any(key not in progress or progress[key].status == "new" for key in ids):
                raise AppError(409, ErrorType.ROUND_INCOMPLETE, "Some vocabulary cards have not been learned")
            correct = len(questions)
        elif current.activity.round_type in GRADED_QUESTION_TYPES_BY_ROUND:
            allowed_types = GRADED_QUESTION_TYPES_BY_ROUND[current.activity.round_type]
            if any(question.type not in allowed_types for question in questions):
                raise content_error()
            results, correct = grade_submitted_questions(questions, payloads, body)
        else:
            raise invalid("roundId", "Round submission is not available for this type")
        total = sum(scoring_units(payload) for payload in payloads.values())
        percent, passed, stars = score(correct, total)
        if current.activity.round_type == "matching":
            # Round 3 grading currently reports the saved progress without recording this attempt.
            today = today_vn()
            return RoundResultDto(
                round_id=round_id, total_questions=total, correct_count=correct,
                score_percent=percent, passed=passed, stars=stars, results=results,
                best_stars=current.metadata.stars, best_score_percent=current.metadata.best_score_percent,
                attempts=current.metadata.attempts, is_new_best=False,
                unlocks=UnlocksDto(next_round_id=None, topic_completed=False, next_topic_id=None,
                                   level_completed=False, next_level_id=None),
                streak=StreakDto(
                    current=effective_streak(user.current_streak, user.last_learning_date, today),
                    last_learning_date=user.last_learning_date, learned_today=user.last_learning_date == today,
                ),
            )
        return self._record_result(user, current.activity, total, correct, percent, passed, stars, results)

    def _record_result(
        self, user: User, activity: Activity, total: int, correct: int, percent: float,
        passed: bool, stars: int, results: list[SubmitQuestionResultDto],
    ) -> RoundResultDto:
        row = self.rounds.progress_for_submit(user.id, activity.id)
        new_score = Decimal(str(percent))
        new_best = row.best_score is None or new_score > row.best_score
        first_pass = passed and row.status != "completed"
        if new_best:
            row.best_score = new_score
        row.stars = max(row.stars, stars)
        row.attempts += 1
        if first_pass:
            row.status = "completed"
            row.completed_at = datetime.now(UTC)
        elif row.status != "completed":
            row.status = "unlocked"

        next_id = None
        if first_pass:
            activities = self.rounds.get_by_lesson(activity.lesson_id)
            following = next((a for a in activities if a.round_number == activity.round_number + 1), None)
            if following is not None:
                following_progress = self.rounds.progress_for_submit(user.id, following.id)
                if following_progress.status == "locked":
                    following_progress.status = "unlocked"
                    next_id = following.id
        return RoundResultDto(
            round_id=activity.id, total_questions=total, correct_count=correct,
            score_percent=percent, passed=passed, stars=stars,
            best_stars=row.stars, best_score_percent=float(row.best_score) if row.best_score is not None else None,
            attempts=row.attempts, is_new_best=new_best, results=results,
            unlocks=UnlocksDto(next_round_id=next_id, topic_completed=False, next_topic_id=None,
                               level_completed=False, next_level_id=None),
            streak=touch(user),
        )

    def vocabulary_action(
        self, user: User, round_id: UUID, body: VocabularyActionRequest,
    ) -> VocabularyActionResultDto:
        """One request transaction, including access, insert, lock and transition.

        CurrentUser already autobegins the request Session. Commit that transaction
        on success; a failure always rolls back before the Session can be reused.
        ON CONFLICT + row locking serializes both first-insert and existing-row cases.
        """
        try:
            current = self._accessible_round(user, round_id)
            if current.activity.round_type != "learn":
                raise invalid("roundId", "Vocabulary actions require a learn round")
            _questions, _payloads, words, _progress = self._round_content(user, current)
            if body.vocabulary_id not in words:
                raise AppError(404, ErrorType.NOT_FOUND, "Vocabulary not found in this round")
            # words is built exclusively from validated learn_card references.
            row = self.vocabularies.get_or_create_locked(user.id, body.vocabulary_id)
            now = datetime.now(UTC)
            if body.action == "flashcard":
                if row.status in ("new", "skipped"):
                    row.status, row.is_in_flashcard = "in_flashcard", True
                    row.repetition_count, row.interval, row.ease_factor = 0, 0, Decimal("2.5")
                    row.last_reviewed, row.next_review = None, now
                if row.added_to_flashcard_at is None:
                    row.added_to_flashcard_at = now
            elif row.status == "new" or (
                row.status == "in_flashcard" and row.repetition_count == 0 and row.last_reviewed is None
            ):
                row.status, row.is_in_flashcard = "skipped", False
                row.repetition_count, row.interval, row.ease_factor = 0, 0, Decimal("2.5")
                row.last_reviewed, row.next_review, row.added_to_flashcard_at = None, None, None
            result = VocabularyActionResultDto.model_validate({
                "vocabulary_id": row.vocabulary_id, "status": row.status, "is_in_flashcard": row.is_in_flashcard,
            })
            self.db.commit()
            return result
        except Exception:
            self.db.rollback()
            raise


def get_round(db: Session, user: User, round_id: UUID) -> RoundDetailDto:
    """Keep the session-based entry point for callers outside FastAPI."""
    return RoundService(db, RoundRepository(db), VocabularyRepository(db)).get_round(user, round_id)


def vocabulary_action(
    db: Session, user: User, round_id: UUID, body: VocabularyActionRequest,
) -> VocabularyActionResultDto:
    """Keep the session-based entry point for callers outside FastAPI."""
    return RoundService(db, RoundRepository(db), VocabularyRepository(db)).vocabulary_action(user, round_id, body)
