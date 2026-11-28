"""Seed Vocabulary Quiz questions with python -m app.seeds.sprint8_seed."""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from collections.abc import Callable, Iterable, Iterator, Mapping, Sequence
from contextlib import AbstractContextManager, contextmanager
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol, cast
from uuid import UUID, uuid5

from alembic.runtime.migration import MigrationContext
from app.models.question import Question
from app.models.topic import Topic
from app.models.vocabulary import PART_OF_SPEECH_VALUES, Vocabulary
from app.schemas.question_payload import (
    FillBlankPayload,
    PictureChoicePayload,
    validate_payload,
)
from app.seeds import sprint5_seed, sprint6_seed
from app.seeds.content_data import LESSONS, LEVEL_ID_BY_ORDER, TOPICS
from app.seeds.content_seed import apply_default_timeouts
from app.utils.answer_text import normalize_answer_text
from app.utils.media import validate_media_path
from pydantic import ValidationError
from sqlalchemy import Table, inspect, or_, select, text
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

QUIZ_NAMESPACE = UUID("c6cdb713-281f-473f-8ee6-7f315bc34e38")
SOURCE = "vocabulary_quiz"
KINDS = ("multiple_choice", "picture_choice", "fill_blank", "translate")
WORD_COLUMNS = ("id", "topic_id", "word", "meaning", "part_of_speech", "phonetic", "audio_url", "image_url", "example")
MIN_POOL = 20
IDENTITY = ("source", "topic_id", "type", "activity_id", "grammar_lesson_id", "purpose")
CONTENT = ("prompt", "payload", "explanation", "image_url", "audio_url")


class SeedError(ValueError):
    """Invalid seed data, schema or database state."""


@dataclass(frozen=True)
class Word:
    id: UUID
    topic_id: UUID
    word: str
    meaning: str
    part_of_speech: str
    phonetic: str
    audio_url: str
    image_url: str | None = None
    example: str | None = None


@dataclass
class Generated:
    rows: list[dict[str, Any]]
    skipped: Counter[tuple[UUID, str, str]]


def question_id(word_id: UUID, kind: str) -> UUID:
    return uuid5(QUIZ_NAMESPACE, f"{word_id}:{kind}:v1")


def canonical(value: object) -> str:
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"))


def _distinct(values: Iterable[str], excluded: Iterable[str], limit: int) -> list[str]:
    seen = {normalize_answer_text(value) for value in excluded}
    result = []
    for value in values:
        key = normalize_answer_text(value)
        if key and key not in seen:
            seen.add(key)
            result.append(value)
            if len(result) == limit:
                break
    return result


def _choice(correct: str, others: list[str], key: str, index: int) -> dict[str, Any]:
    position = index % sprint5_seed.OPTION_COUNT
    values = others.copy()
    values.insert(position, correct)
    return {
        "options": [{"id": f"o{i}", key: value} for i, value in enumerate(values, 1)],
        "correctOptionId": f"o{position + 1}",
    }


def generate(words: Sequence[Word], available_images: frozenset[str]) -> Generated:
    """Generate stable questions from already validated input and a media inventory."""
    result = Generated([], Counter())
    groups: dict[UUID, list[Word]] = {}
    for word in sorted(words, key=lambda word: (word.topic_id, word.id)):
        groups.setdefault(word.topic_id, []).append(word)
    for group in groups.values():
        for index, word in enumerate(group):
            peers = group[index + 1:] + group[:index]
            for kind in KINDS:
                payload, reason = _payload(kind, word, peers, index, available_images)
                if payload is None:
                    result.skipped[word.topic_id, kind, reason] += 1
                    continue
                prompts = {
                    "multiple_choice": f'What does "{word.word}" mean?',
                    "picture_choice": f'Choose the picture for "{word.word}".',
                    "fill_blank": "Complete the sentence.",
                    "translate": "Translate into English.",
                }
                explanation = f'"{word.word}" means "{word.meaning}".'
                result.rows.append({
                    "id": question_id(word.id, kind),
                    "type": kind, "source": SOURCE, "topic_id": word.topic_id,
                    "activity_id": None, "grammar_lesson_id": None, "purpose": None,
                    "image_url": None, "audio_url": None, "prompt": prompts[kind],
                    "explanation": explanation if kind in KINDS[:2] else None,
                    "payload": json.loads(canonical(payload)),
                })
    return result


def _payload(
    kind: str, word: Word, peers: list[Word], index: int, images: frozenset[str],
) -> tuple[dict[str, Any] | None, str]:
    if kind == "multiple_choice":
        meanings = _distinct((peer.meaning for peer in peers), [word.meaning], 3)
        if len(meanings) < 3:
            return None, "fewer than three distinct meanings"
        return _choice(word.meaning, meanings, "text", index), ""

    if kind == "picture_choice":
        if word.image_url is None:
            return None, "no image URL"
        if word.image_url not in images:
            return None, "image file missing"
        other_images = list(dict.fromkeys(
            path for peer in peers
            if (path := peer.image_url) is not None and path in images and path != word.image_url
        ))[:3]
        if len(other_images) < 3:
            return None, "fewer than three distinct available image distractors"
        return _choice(word.image_url, other_images, "imageUrl", index), ""

    if kind == "fill_blank":
        if not word.example or not word.example.strip():
            return None, "no example"
        if "___" in word.example:
            return None, "example already contains a blank"
        pattern = rf"(?<!\w){re.escape(word.word)}(?!\w)"
        sentence, replacements = re.subn(pattern, "___", word.example, count=1, flags=re.IGNORECASE)
        if not replacements:
            return None, "example does not contain the whole word"
        same_pos_first = sorted(peers, key=lambda peer: peer.part_of_speech != word.part_of_speech)
        bank = _distinct((peer.word for peer in same_pos_first), [word.word], 3)
        if len(bank) != 3:
            return None, "fewer than three distinct word bank distractors"
        bank.insert(index % 4, word.word)
        if sentence.count("___") != 1:
            return None, "replacement does not produce one blank"
        return {"sentence": sentence, "acceptedAnswers": [word.word], "wordBank": bank}, ""

    answer = word.word.split()
    noise = _distinct((token for peer in peers for token in peer.word.split()), answer, 2)
    tagged = [(token, order) for order, token in enumerate(answer)]
    tagged.extend((token, -1) for token in noise)
    offset = (index + 1) % len(tagged)
    tagged = tagged[offset:] + tagged[:offset]
    tokens = [{"id": f"t{i}", "text": token} for i, (token, _) in enumerate(tagged, 1)]
    original_order = {order: token["id"] for token, (_, order) in zip(tokens, tagged, strict=True) if order >= 0}
    return {
        "direction": "vi_to_en", "sourceText": word.meaning, "acceptedAnswers": [word.word],
        "tokens": tokens, "acceptedSequences": [[original_order[i] for i in range(len(answer))]],
    }, ""


def _media_exists(root: Path, value: str) -> bool:
    relative = validate_media_path(value)
    path = (root / relative).resolve()
    if not path.is_relative_to(root / "media"):
        raise SeedError(f"Media resolves outside the media directory: {value!r}")
    return path.is_file()


def validate_words(
    words: Sequence[Word], topics: Mapping[UUID, str], static_root: Path,
) -> frozenset[str]:
    """Check the database vocabulary against the Sprint 5 D23 rules and D24."""
    root = static_root.resolve()
    seen: set[UUID] = set()
    spellings: dict[UUID, list[str]] = {topic_id: [] for topic_id in topics}
    images: set[str] = set()
    for word in words:
        if word.id in seen or word.topic_id not in topics:
            raise SeedError(f"Invalid Vocabulary identity: {word.id}, Topic {word.topic_id}")
        seen.add(word.id)
        if (
            any(not isinstance(value, str) or not value or value != value.strip()
                for value in (word.word, word.meaning, word.phonetic))
            or word.part_of_speech not in PART_OF_SPEECH_VALUES
            or word.part_of_speech == "other"
        ):
            raise SeedError(f"D23: missing or invalid word, meaning, phonetic or part of speech for {word.id}")
        spellings[word.topic_id].append(normalize_answer_text(word.word))
        if not word.audio_url or not _media_exists(root, word.audio_url):
            raise SeedError(f"D23/D24: required audio file missing for {word.id}: {word.audio_url!r}")
        if word.image_url is not None and _media_exists(root, word.image_url):
            images.add(word.image_url)
    for topic_id, labels in spellings.items():
        if len(labels) != 10 or len(set(labels)) != 10:
            raise SeedError(f"D23: {topics[topic_id]} has {len(labels)} Vocabulary rows and "
                            f"{len(set(labels))} distinct words; expected 10 distinct words")
    return frozenset(images)


def validate_question(row: Mapping[str, Any], static_root: Path) -> None:
    if row["type"] not in KINDS or not str(row["prompt"] or "").strip():
        raise SeedError("Unsupported Vocabulary Quiz type or empty prompt")
    if row["source"] != SOURCE or row["topic_id"] is None or any(
        row[field] is not None for field in ("activity_id", "grammar_lesson_id", "purpose")
    ):
        raise SeedError("Invalid Vocabulary Quiz owner")
    payload = validate_payload(row["type"], row["payload"])
    if isinstance(payload, FillBlankPayload) and payload.sentence.count("___") != 1:
        raise SeedError("Fill blank requires exactly one blank")
    paths = [row["image_url"], row["audio_url"]]
    if isinstance(payload, PictureChoicePayload):
        paths.extend(option.image_url for option in payload.options)
    for path in paths:
        if path is not None and not _media_exists(static_root, path):
            raise SeedError(f"Referenced media file missing: {path}")


class Repository(Protocol):
    def transaction(self) -> AbstractContextManager[Any]: ...
    def read_words(self, topics: Mapping[UUID, str]) -> list[Word]: ...
    def read_questions(self, ids: Sequence[UUID]) -> list[dict[str, Any]]: ...
    def insert(self, rows: Sequence[dict[str, Any]]) -> set[UUID]: ...


@dataclass
class Plan:
    new: list[dict[str, Any]]
    unchanged: list[UUID]
    drift: list[UUID]
    unmatched: list[UUID]


def plan_questions(generated: Generated, existing: Sequence[dict[str, Any]]) -> Plan:
    by_id = {row["id"]: row for row in existing}
    plan = Plan([], [], [], [])
    for row in generated.rows:
        current = by_id.pop(row["id"], None)
        if current is None:
            plan.new.append(row)
        elif any(row[field] != current[field] for field in IDENTITY):
            raise SeedError(f"UUID conflict: {row['id']} already has a different source, owner or type")
        elif any(canonical(row[field]) != canonical(current[field]) for field in CONTENT):
            plan.drift.append(row["id"])
        else:
            plan.unchanged.append(row["id"])
    plan.unmatched = sorted(row["id"] for row in by_id.values() if row["source"] == SOURCE)
    return plan


def _require_pool(rows: Sequence[dict[str, Any]], topics: Mapping[UUID, str], stage: str) -> None:
    counts = Counter(row["topic_id"] for row in rows)
    short = [f"{name}: {counts[topic_id]}" for topic_id, name in topics.items() if counts[topic_id] < MIN_POOL]
    if short:
        raise SeedError(f"O12 {stage}: expected at least {MIN_POOL} valid questions per Topic. " + "; ".join(short))


def _valid_pool(
    rows: Sequence[dict[str, Any]], static_root: Path, emit: Callable[[str], None],
) -> list[dict[str, Any]]:
    valid = []
    for row in rows:
        if row["source"] != SOURCE:
            continue
        try:
            validate_question(row, static_root)
        except ValueError:
            emit(f"Invalid existing question excluded from O12: {row['id']}")
        else:
            valid.append(row)
    return valid


def _report(
    rows: Sequence[dict[str, Any]], topics: Mapping[UUID, str],
    emit: Callable[[str], None], label: str, skipped: Counter[tuple[UUID, str, str]] | None = None,
) -> None:
    counts = Counter((row["topic_id"], row["type"]) for row in rows)
    emit(f"{label}: Topic | " + " | ".join(KINDS) + " | Total")
    for topic_id, name in topics.items():
        totals = [counts[topic_id, kind] for kind in KINDS]
        emit(f"{name} | " + " | ".join(map(str, (*totals, sum(totals)))))
    for (topic_id, kind, reason), count in sorted((skipped or {}).items()):
        emit(f"Skipped {topics[topic_id]} | {kind} | {count} | {reason}")


def run(
    repo: Repository, *, apply: bool = False,
    topics: Mapping[UUID, str], static_root: Path,
    reserved_ids: frozenset[UUID] = frozenset(), emit: Callable[[str], None] = print,
) -> Plan:
    static_root = static_root.resolve()
    with repo.transaction():
        words = repo.read_words(topics)
        images = validate_words(words, topics, static_root)
        generated = generate(words, images)
        ids = [row["id"] for row in generated.rows]
        if len(ids) != len(set(ids)) or set(ids) & reserved_ids:
            raise SeedError("Generated UUIDs collide with one another or earlier seed IDs")
        for row in generated.rows:
            validate_question(row, static_root)
        _report(generated.rows, topics, emit, "Generated", generated.skipped)
        _require_pool(generated.rows, topics, "generated")
        existing = repo.read_questions(ids)
        plan = plan_questions(generated, existing)
        valid_existing = _valid_pool(existing, static_root, emit)
        _require_pool([*valid_existing, *plan.new], topics, "projected database")
        for question_uuid in plan.drift:
            emit(f"Drift, retained: {question_uuid}")
        for question_uuid in plan.unmatched:
            emit(f"Unmatched Quiz question, retained (manual or no longer generated): {question_uuid}")
        if apply:
            expected_new = {row["id"] for row in plan.new}
            if repo.insert(plan.new) != expected_new:
                raise SeedError("Concurrent change during insert; rollback and rerun")
            stored = repo.read_questions(ids)
            checked = plan_questions(generated, stored)
            if checked.new or checked.drift != plan.drift:
                raise SeedError("Stored questions differ from the planned insert or retained rows")
            pool = _valid_pool(stored, static_root, emit)
            _require_pool(pool, topics, "stored database")
            _report(plan.new, topics, emit, "Inserted")
            _report(pool, topics, emit, "Stored valid pool")
        else:
            _report([*valid_existing, *plan.new], topics, emit, "Projected valid pool")
    label = "inserted" if apply else "would_insert"
    emit(f"{label}={len(plan.new)} unchanged={len(plan.unchanged)} drift={len(plan.drift)} "
         f"skipped={sum(generated.skipped.values())} unmatched={len(plan.unmatched)}")
    return plan


class PostgresRepository:
    def __init__(self, session: Session, topics: Mapping[UUID, str], *, apply: bool) -> None:
        self.session = session
        self.topics = topics
        self.apply = apply

    @contextmanager
    def transaction(self) -> Iterator[None]:
        with self.session.begin():
            mode = "READ WRITE" if self.apply else "READ ONLY"
            self.session.execute(text(f"SET TRANSACTION ISOLATION LEVEL REPEATABLE READ {mode}"))
            _preflight(self.session, self.topics)
            yield

    def read_words(self, topics: Mapping[UUID, str]) -> list[Word]:
        table = cast(Table, Vocabulary.__table__)
        statement = select(*(table.c[name] for name in WORD_COLUMNS)).where(Vocabulary.topic_id.in_(topics)).order_by(
            Vocabulary.topic_id, Vocabulary.id,
        )
        return [Word(**row) for row in self.session.execute(statement).mappings()]

    def read_questions(self, ids: Sequence[UUID]) -> list[dict[str, Any]]:
        table = cast(Table, Question.__table__)
        statement = select(table).where(or_(table.c.id.in_(ids), table.c.source == SOURCE)).order_by(table.c.id)
        return [dict(row) for row in self.session.execute(statement).mappings()]

    def insert(self, rows: Sequence[dict[str, Any]]) -> set[UUID]:
        if not rows:
            return set()
        table = cast(Table, Question.__table__)
        statement = insert(table).values(list(rows)).on_conflict_do_nothing(
            index_elements=[table.c.id],
        ).returning(table.c.id)
        return set(self.session.scalars(statement))


def _preflight(session: Session, topics: Mapping[UUID, str]) -> None:
    connection = session.connection()
    if connection.dialect.name != "postgresql":
        raise SeedError("This seed requires PostgreSQL")
    database, server, port, schema = session.execute(text(
        "SELECT current_database(), inet_server_addr(), inet_server_port(), current_schema()",
    )).one()
    url = connection.engine.url
    print(f"Configured host={url.host} port={url.port or 5432}; "
          f"connected server={server} port={port} database={database} schema={schema}")
    if not schema or database != url.database:
        raise SeedError("Connected database or schema does not match the backend connection")
    inspector = inspect(connection)
    print("Alembic revision:", ", ".join(MigrationContext.configure(connection).get_current_heads()) or "(missing)")
    required = (
        (cast(Table, Topic.__table__), ("id",)),
        (cast(Table, Vocabulary.__table__), WORD_COLUMNS),
        (cast(Table, Question.__table__), ("id", *IDENTITY, *CONTENT)),
    )
    for table, names in required:
        if not inspector.has_table(table.name, schema=schema):
            raise SeedError(f"Missing required table {schema}.{table.name}")
        actual = {column["name"]: column for column in inspector.get_columns(table.name, schema=schema)}
        for name in names:
            column = table.c[name]
            stored = actual.get(column.name)
            if stored is None:
                raise SeedError(f"Missing required column {schema}.{table.name}.{column.name}")
            expected = column.type.compile(dialect=connection.dialect)
            if stored["type"].compile(dialect=connection.dialect) != expected or stored["nullable"] != column.nullable:
                raise SeedError(f"Column type or nullability differs for {schema}.{table.name}.{column.name}")
    if inspector.get_pk_constraint("questions", schema=schema)["constrained_columns"] != ["id"]:
        raise SeedError("Missing questions primary key on id")
    checks = {entry["name"]: entry["sqltext"]
              for entry in inspector.get_check_constraints("questions", schema=schema)}
    for name, fragments in {
        "ck_questions_source_valid": (SOURCE,),
        "ck_questions_source_owner_match": (
            SOURCE, "topic_id IS NOT NULL", "activity_id IS NULL",
            "grammar_lesson_id IS NULL", "purpose IS NULL",
        ),
        "ck_questions_payload_object": ("jsonb_typeof(payload)", "'object'"),
        "ck_questions_type_valid": KINDS,
        "ck_questions_purpose_valid": ("purpose IS NULL",),
    }.items():
        if any(fragment not in checks.get(name, "") for fragment in fragments):
            raise SeedError(f"Missing or incompatible {schema}.questions constraint {name}")
    foreign_keys = inspector.get_foreign_keys("questions", schema=schema)
    if not any(
        key["constrained_columns"] == ["topic_id"] and key["referred_table"] == "topics"
        and key["referred_schema"] in (None, schema) and key["referred_columns"] == ["id"]
        for key in foreign_keys
    ):
        raise SeedError(f"Missing {schema}.questions.topic_id foreign key to topics.id")
    existing = set(session.scalars(select(Topic.id).where(Topic.id.in_(topics))))
    missing = [name for topic_id, name in topics.items() if topic_id not in existing]
    if missing:
        raise SeedError("Expected Topics missing from the content seed: " + ", ".join(missing))


def prior_seed_ids() -> frozenset[UUID]:
    """Collect official seed IDs independently of optional media availability."""
    ids = {*(topic.id for topic in TOPICS), *(lesson.id for lesson in LESSONS), *LEVEL_ID_BY_ORDER.values()}
    topics = {topic.key: topic.id for topic in TOPICS}
    lessons = {lesson.topic_key: lesson.id for lesson in LESSONS}
    round4 = json.loads(sprint6_seed.DATA_FILE.read_text(encoding="utf-8"))
    for code, items in sprint5_seed._load_vocabulary(sprint5_seed.DATA_FILE).items():
        key = sprint5_seed.TOPIC_KEYS[code]
        activities = [uuid5(lessons[key], f"activity:{n}") for n in range(1, 6)]
        ids.update(activities)
        words = [uuid5(topics[key], f"vocabulary:{item.word}") for item in items]
        ids.update(words)
        for activity_id in activities[:2]:
            ids.update(uuid5(activity_id, f"question:{word}") for word in words)
        ids.update(uuid5(activities[1], f"option:{word}:{peer}") for word in words for peer in words)
        ids.update(uuid5(activities[2], f"question:matching-{n}") for n in (1, 2))
        ids.update(question.row(activities[3])["id"] for question in sprint6_seed._round_four(code, round4[code]))
    return frozenset(ids)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--apply", action="store_true", help="Insert new Quiz questions")
    modes.add_argument("--dry-run", action="store_true", help="Validate and report without inserting (default)")
    args = parser.parse_args(argv)
    topics = {topic.id: topic.name for topic in TOPICS}
    try:
        apply_default_timeouts()
        try:
            from app.database.session import SessionLocal
        except ValidationError:
            raise SeedError("Backend settings failed validation") from None

        print("APPLY" if args.apply else "DRY-RUN")
        with SessionLocal() as session:
            repo = PostgresRepository(session, topics, apply=args.apply)
            run(repo, apply=args.apply, topics=topics,
                static_root=sprint5_seed.STATIC_ROOT, reserved_ids=prior_seed_ids())
    except SQLAlchemyError as exc:
        code = getattr(getattr(exc, "orig", None), "pgcode", None)
        print(f"Seed failed: {type(exc).__name__}. PostgreSQL SQLSTATE: {code or 'unavailable'}")
        return 1
    except (ValueError, OSError) as exc:
        print(f"Seed failed: {exc}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
