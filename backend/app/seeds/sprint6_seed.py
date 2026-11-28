"""Seed official Round 3–4 questions. Run from backend with python -m app.seeds.sprint6_seed."""

import argparse
import csv
import json
import sys
from collections import Counter, defaultdict
from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any, cast
from uuid import UUID, uuid5

from alembic.runtime.migration import MigrationContext
from sqlalchemy import Table, or_, select, text
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.models.activity import Activity
from app.models.question import Question
from app.models.vocabulary import Vocabulary
from app.schemas.question_payload import (
    FillBlankPayload,
    MatchingPayload,
    SentenceOrderPayload,
    TranslatePayload,
    validate_payload,
)
from app.seeds import sprint5_seed
from app.seeds.content_data import LESSONS, TOPICS
from app.seeds.content_seed import (
    SchemaNotAtHeadError,
    SeedPostConditionError,
    TableCounts,
    apply_default_timeouts,
    get_head_revision,
    load_state,
    masked_url,
    verify_final_state,
)
from app.services.round_content import scoring_units
from app.utils.answer_text import normalize_answer_text

DATA_FILE = Path(__file__).with_name("sprint6_round4.v1.json")
TOPIC_BY_KEY = {topic.key: topic for topic in TOPICS}
LESSON_BY_KEY = {lesson.topic_key: lesson for lesson in LESSONS}
EXPECTED_TYPES = {"fill_blank": 3, "translate": 3, "sentence_order": 2}
REQUIRED_ACTIVITY_FIELDS = ("round_type", "lesson_id", "round_number")
QUESTION_IDENTITY = ("activity_id", "source", "type", "topic_id", "grammar_lesson_id", "purpose")
QUESTION_CONTENT = ("prompt", "payload", "explanation", "image_url", "audio_url")


class Sprint6SeedError(ValueError):
    """Invalid official content or a conflict with existing rows."""


@dataclass(frozen=True)
class SeedQuestion:
    topic_code: str
    round_number: int
    key: str
    kind: str
    prompt: str
    payload: dict[str, Any]

    def row(self, activity_id: UUID) -> dict[str, Any]:
        return {
            "id": uuid5(activity_id, f"question:{self.key}"),
            "activity_id": activity_id, "type": self.kind, "source": "round",
            "topic_id": None, "grammar_lesson_id": None, "purpose": None,
            "prompt": self.prompt, "payload": self.payload, "explanation": None,
            "image_url": None, "audio_url": None,
        }


@dataclass(frozen=True)
class SeedContent:
    legacy: sprint5_seed.SeedRows
    questions: tuple[SeedQuestion, ...]


def _matching(words: Sequence[dict[str, Any]]) -> dict[str, Any]:
    pairs = [{"leftId": f"l{i}", "rightId": f"r{i}"} for i in range(1, len(words) + 1)]
    return {
        "left": [{"id": pair["leftId"], "text": word["word"]} for pair, word in zip(pairs, words, strict=True)],
        "right": [{"id": pair["rightId"], "text": word["meaning"]}
                  for pair, word in reversed(list(zip(pairs, words, strict=True)))],
        "correctPairs": pairs,
    }


def _sequence(sentence: str) -> dict[str, Any]:
    tokens = [{"id": f"t{i}", "text": word} for i, word in enumerate(sentence.split(), 1)]
    if not 4 <= len(tokens) <= 8:
        raise Sprint6SeedError("Expected 4–8 tokens in the primary answer")
    return {"tokens": tokens[::-1], "acceptedSequences": [[token["id"] for token in tokens]]}


def _round_four(code: str, data: dict[str, Any]) -> list[SeedQuestion]:
    """Keep numbered keys stable when revising the content of an existing slot."""
    group_sizes = {"fill": 3, "translate": 3, "order": 2}
    if set(data) != group_sizes.keys() or any(len(data[key]) != n for key, n in group_sizes.items()):
        raise Sprint6SeedError("Expected three fills, three translations and two sentence orders")
    questions = []
    for i, ((sentence, accepted, bank), (source, token_answer, text_answers)) in enumerate(
        zip(data["fill"], data["translate"], strict=True), 1,
    ):
        questions.append(SeedQuestion(code, 4, f"fill-{i}", "fill_blank", "Complete the sentence.", {
            "sentence": sentence, "acceptedAnswers": accepted, "wordBank": bank,
        }))
        try:
            payload = _sequence(token_answer)
            payload.update(sourceText=source, direction="vi_to_en", acceptedAnswers=text_answers)
            questions.append(SeedQuestion(code, 4, f"translate-{i}", "translate", "Translate into English.", payload))
            if i <= 2:
                questions.append(SeedQuestion(code, 4, f"order-{i}", "sentence_order", "Put the words in order.",
                                              _sequence(data["order"][i - 1])))
        except Sprint6SeedError as exc:
            raise Sprint6SeedError(f"Round 4, slot {i}: {exc}") from exc
    return questions


def _validate_question(question: SeedQuestion) -> int:
    model = validate_payload(question.kind, question.payload)
    if isinstance(model, MatchingPayload):
        if len(model.left) != 5:
            raise Sprint6SeedError("Expected five pairs")
        for column in (model.left, model.right):
            labels = [normalize_answer_text(item.text or "") for item in column]
            if "" in labels or len(set(labels)) != 5:
                raise Sprint6SeedError("Matching column labels must be distinct and non-empty")
    if isinstance(model, (FillBlankPayload, TranslatePayload)):
        answers = {normalize_answer_text(answer) for answer in model.accepted_answers}
        if len(answers) != len(model.accepted_answers):
            raise Sprint6SeedError("Duplicate normalized accepted answer")
        if isinstance(model, FillBlankPayload):
            if model.sentence.count("___") != 1:
                raise Sprint6SeedError("Expected one blank")
            if model.word_bank is not None:
                bank = {normalize_answer_text(word) for word in model.word_bank}
                if "" in bank or len(bank) != len(model.word_bank) or not 1 <= len(bank - answers) <= 2:
                    raise Sprint6SeedError("Word bank must have unique terms and one or two distractors")
        else:
            tokens = {token.id: token.text for token in model.tokens}
            for sequence in model.accepted_sequences:
                if normalize_answer_text(" ".join(tokens[token] for token in sequence)) not in answers:
                    raise Sprint6SeedError("Token answer is missing from the explicitly reviewed keyboard answers")
    if isinstance(model, (TranslatePayload, SentenceOrderPayload)):
        sequences = {tuple(sequence) for sequence in model.accepted_sequences}
        if len(sequences) != len(model.accepted_sequences):
            raise Sprint6SeedError("Duplicate accepted sequence")
        if isinstance(model, SentenceOrderPayload) and tuple(t.id for t in model.tokens) in sequences:
            raise Sprint6SeedError("Stored token order must be different from the answer")
    return scoring_units(model)


def build_content(data_file: Path = DATA_FILE) -> SeedContent:
    """Validate official content for all four rounds without opening a database connection."""
    legacy = sprint5_seed.build_seed_rows()
    raw = json.loads(data_file.read_text(encoding="utf-8"))
    if not isinstance(raw, dict) or raw.keys() != sprint5_seed.TOPIC_KEYS.keys():
        raise Sprint6SeedError("Round 4 data must cover exactly the official Topic keys")
    vocabulary = defaultdict(list)
    for word in legacy.vocabulary:
        vocabulary[word["topic_id"]].append(word)
    questions = []
    problems = []
    for code, topic_key in sprint5_seed.TOPIC_KEYS.items():
        words = vocabulary[TOPIC_BY_KEY[topic_key].id]
        matching = [
            SeedQuestion(code, 3, f"matching-{i + 1}", "matching", "Match each word to its meaning.",
                         _matching(words[i * 5:(i + 1) * 5]))
            for i in range(2)
        ]
        try:
            application = _round_four(code, raw[code])
        except (ValueError, KeyError, TypeError) as exc:
            problems.append(f"Topic {code}, Round 4: {exc}")
            application = []
        units: Counter[int] = Counter()
        for question in matching + application:
            try:
                units[question.round_number] += _validate_question(question)
            except ValueError as exc:
                problems.append(f"Topic {code}, Round {question.round_number}, {question.key}: {exc}")
        if units != {3: 10, 4: 8} or Counter(q.kind for q in application) != EXPECTED_TYPES:
            problems.append(f"Topic {code}: incorrect Round 3–4 question types or scoring units")
        banks = {q.payload["wordBank"] is None for q in application if q.kind == "fill_blank"}
        if banks != {False, True}:
            problems.append(f"Topic {code}, Round 4: expected fills with and without a word bank")
        questions.extend(matching + application)
    if problems:
        raise Sprint6SeedError("\n".join(problems))
    return SeedContent(legacy, tuple(questions))


def render_preview(content: SeedContent) -> str:
    lines = ["# Round 3–4 Seed Preview", "", "Source: `sprint6_round4.v1.json` and the official Sprint 5 vocabulary.",
             "Matching uses two groups of five in CSV order. Translate keyboard answers are authored explicitly.",
             "The order below is for review. GET continues to shuffle questions and options.", ""]
    current = None
    for question in content.questions:
        section = (question.topic_code, question.round_number)
        if section != current:
            code, number = section
            label = TOPIC_BY_KEY[sprint5_seed.TOPIC_KEYS[code]].name
            lines.extend([f"## {code} {label} · Round {number}", ""])
            current = section
        p = question.payload
        lines.append(f"- **{question.key}**")
        if question.kind == "matching":
            for side in ("left", "right"):
                lines.append(f"  - {side.title()}: " + " · ".join(f"{item['id']} {item['text']}" for item in p[side]))
            lines.append("  - Correct pairs: " + ", ".join(f"{item['leftId']} → {item['rightId']}" for item in p["correctPairs"]))
        elif question.kind == "fill_blank":
            lines.extend([f"  - {p['sentence']}", f"  - Word bank: {p['wordBank'] if p['wordBank'] is not None else 'null'}",
                          f"  - Accepted answers: {', '.join(p['acceptedAnswers'])}"])
        else:
            if question.kind == "translate":
                lines.append(f"  - Source: {p['sourceText']}")
            lines.append("  - Tokens: " + " · ".join(f"{token['id']} {token['text']}" for token in p["tokens"]))
            lines.append(f"  - Accepted sequences: {p['acceptedSequences']}")
            if question.kind == "translate":
                lines.append(f"  - Accepted keyboard answers: {', '.join(p['acceptedAnswers'])}")
        lines.append("")
    return "\n".join(lines)


def _require_official_rows(
    session: Session, table: Table, desired: list[dict[str, Any]], scope: str, identity: tuple[str, ...],
) -> None:
    plan = sprint5_seed._plan_rows(session, table, desired, scope, identity)
    if plan.rows_to_upsert:
        labels = ", ".join(f"{row['id']} ({row[scope]})" for row in plan.rows_to_upsert)
        raise Sprint6SeedError(f"{table.name}: Sprint 5 rows missing or differ from the official seed: {labels}")


def _activity_targets(
    session: Session, legacy: sprint5_seed.SeedRows,
) -> tuple[dict[tuple[str, int], UUID], list[dict[str, Any]]]:
    desired = {(r["lesson_id"], r["round_number"]): r for r in legacy.activities}
    rows = session.scalars(select(Activity).where(Activity.lesson_id.in_({key[0] for key in desired})))
    existing = {(row.lesson_id, row.round_number): row for row in rows}
    targets, missing = {}, []
    for code, topic_key in sprint5_seed.TOPIC_KEYS.items():
        for number in (1, 2, 3, 4):
            key = (LESSON_BY_KEY[topic_key].id, number)
            expected = desired[key]
            row = existing.get(key)
            if row is not None and any(getattr(row, field) != expected[field] for field in REQUIRED_ACTIVITY_FIELDS):
                raise Sprint6SeedError(f"Topic {code}, Round {number}, Activity {row.id}: conflicting identity")
            if number <= 2:
                if row is None or row.id != expected["id"]:
                    raise Sprint6SeedError(f"Topic {code}, Round {number}: missing official Sprint 5 Activity")
            else:
                targets[code, number] = row.id if row is not None else expected["id"]
                if row is None:
                    missing.append(expected)
    if missing:
        collisions = session.scalars(select(Activity.id).where(Activity.id.in_([r["id"] for r in missing]))).all()
        if collisions:
            raise Sprint6SeedError(f"Activity UUID collision: {', '.join(map(str, collisions))}")
    return targets, missing


def _question_plan(
    session: Session, targets: Mapping[tuple[str, int], UUID], questions: Sequence[SeedQuestion],
) -> tuple[list[dict[str, Any]], TableCounts]:
    planned = [q.row(targets[q.topic_code, q.round_number]) for q in questions]
    desired = {row["id"]: row for row in planned}
    labels = {activity_id: f"Topic {code}, Round {number}" for (code, number), activity_id in targets.items()}
    table = cast(Table, Question.__table__)
    rows = session.execute(select(table).where(
        or_(table.c.activity_id.in_(labels), table.c.id.in_(desired)),
    ).order_by(table.c.activity_id, table.c.id)).mappings()
    updates, conflicts = [], []
    unchanged = 0
    for row in rows:
        expected = desired.pop(row["id"], None)
        if expected is None:
            conflicts.append(f"{labels[row['activity_id']]}, Question {row['id']}: outside official seed")
        elif any(row[key] != expected[key] for key in QUESTION_IDENTITY):
            conflicts.append(f"{labels[expected['activity_id']]}, Question {row['id']}: UUID identity conflict "
                             f"(existing Activity {row['activity_id']}, source {row['source']}, type {row['type']})")
        elif any(row[key] != expected[key] for key in QUESTION_CONTENT):
            updates.append(expected)
        else:
            unchanged += 1
    if conflicts:
        raise Sprint6SeedError("\n".join(conflicts))
    return [*desired.values(), *updates], TableCounts(len(desired), len(updates), unchanged)


def run(session: Session, *, apply: bool, data_file: Path = DATA_FILE) -> dict[str, TableCounts]:
    """Validate, plan and optionally apply within a single caller-owned transaction."""
    content = build_content(data_file)
    if MigrationContext.configure(session.connection()).get_current_heads() != (get_head_revision(),):
        raise SchemaNotAtHeadError("Expected Alembic head in the target schema")
    if apply:
        session.execute(text("LOCK TABLE levels, topics, lessons IN SHARE MODE"))
        session.execute(text("LOCK TABLE activities, vocabularies, questions IN SHARE ROW EXCLUSIVE MODE"))
    verify_final_state(load_state(session))
    legacy = content.legacy
    _require_official_rows(session, cast(Table, Vocabulary.__table__), legacy.vocabulary, "topic_id",
                           ("topic_id", "level_id", "word"))
    targets, missing = _activity_targets(session, legacy)
    question_table = cast(Table, Question.__table__)
    _require_official_rows(session, question_table, legacy.questions, "activity_id", ("activity_id", "source"))
    changes, counts = _question_plan(session, targets, content.questions)
    if apply and missing:
        session.execute(insert(Activity).values(missing))
    if apply and changes:
        statement = insert(question_table).values(changes)
        session.execute(statement.on_conflict_do_update(
            index_elements=[question_table.c.id], set_={key: statement.excluded[key] for key in QUESTION_CONTENT},
        ))
        if _question_plan(session, targets, content.questions)[0]:
            raise SeedPostConditionError("Round 3–4 database content differs after apply")
    return {"activities_round3_4": TableCounts(len(missing), 0, len(targets) - len(missing)),
            "questions_round3_4": counts}


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Write changes (defaults to dry-run)")
    parser.add_argument("--confirm-database", help="Required with --apply; match the configured DB name")
    parser.add_argument("--confirm-schema", help="Required with --apply; match current_schema()")
    args = parser.parse_args(argv)
    apply_default_timeouts()
    from app.database.session import SessionLocal, engine

    print(f"target: {masked_url(engine.url)}; mode: {'apply' if args.apply else 'dry-run'}")
    if args.apply and (not args.confirm_schema or args.confirm_database != engine.url.database or not args.confirm_database):
        parser.error("--apply requires matching --confirm-database and --confirm-schema")
    try:
        with SessionLocal.begin() as session:
            if not args.apply:
                session.execute(text("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY"))
            database, schema = session.execute(text("SELECT current_database(), current_schema()")).one()
            print(f"database={database} schema={schema}")
            if args.apply and (database != args.confirm_database or schema != args.confirm_schema):
                parser.error("Connected DB/schema does not match the confirmed target")
            counts = run(session, apply=args.apply)
    except (ValueError, RuntimeError, OSError, csv.Error) as exc:
        print(f"BLOCKED: {exc}", file=sys.stderr)
        return 2
    except SQLAlchemyError as exc:
        print(f"ERROR: {type(exc).__name__}", file=sys.stderr)
        return 1
    for name, count in counts.items():
        print(f"{name}: inserted={count.inserted} updated={count.updated} unchanged={count.unchanged}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
