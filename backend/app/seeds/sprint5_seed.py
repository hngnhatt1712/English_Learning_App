"""Seed the nine Sprint 5 topics. Run from backend with `python -m app.seeds.sprint5_seed`."""

import argparse
import csv
import sys
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any, cast
from uuid import UUID, uuid5

from alembic.runtime.migration import MigrationContext
from app.models.activity import ROUND_TYPE_VALUES, Activity
from app.models.question import Question
from app.models.vocabulary import PART_OF_SPEECH_VALUES, Vocabulary
from app.schemas.question_payload import validate_payload
from app.seeds.content_data import LESSONS, LEVEL_ID_BY_ORDER, TOPICS
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
from app.utils.media import validate_media_path
from sqlalchemy import Table, or_, select, text
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.exc import DBAPIError, SQLAlchemyError
from sqlalchemy.orm import Session

DATA_FILE = Path(__file__).with_name("sprint5_vocabulary.csv")
STATIC_ROOT = Path(__file__).resolve().parents[1] / "static"
TOPIC_KEYS = {
    "1.1": "l1_self_family",
    "1.2": "l1_daily_hobbies",
    "1.3": "l1_food_shopping",
    "2.1": "l2_travel_directions",
    "2.2": "l2_work_study_past",
    "2.3": "l2_health_lifestyle",
    "3.1": "l3_entertainment_culture",
    "3.2": "l3_technology_career",
    "3.3": "l3_environment_society",
}
ROUND_TITLES = ("Learn", "Recognition", "Matching", "Application", "Final Review")
OPTION_COUNT = 4
CSV_FIELDS = (
    "topic",
    "word",
    "part_of_speech",
    "phonetic",
    "meaning",
    "image_filename",
    "audio_filename",
    "picture_distractors",
)


class Sprint5SeedError(ValueError):
    """Invalid seed input or a conflict with existing content."""


@dataclass(frozen=True)
class VocabularySeed:
    topic: str
    word: str
    part_of_speech: str
    phonetic: str
    meaning: str
    image_filename: str
    audio_filename: str
    picture_distractors: str

    @property
    def label(self) -> str:
        return f"Topic {self.topic}, word {self.word!r}"


def _load_vocabulary(data_file: Path) -> dict[str, list[VocabularySeed]]:
    groups: dict[str, list[VocabularySeed]] = {code: [] for code in TOPIC_KEYS}
    with data_file.open(encoding="utf-8", newline="") as source:
        reader = csv.DictReader(source, strict=True)
        if tuple(reader.fieldnames or ()) != CSV_FIELDS:
            raise Sprint5SeedError(f"{data_file}: unexpected CSV columns")
        for row in reader:
            if None in row or any(value is None for value in row.values()):
                raise Sprint5SeedError(f"{data_file}:{reader.line_num}: invalid CSV row")
            item = VocabularySeed(**row)
            if item.topic not in groups:
                raise Sprint5SeedError(f"{item.label}: unknown topic code")
            if (
                any(not value or value != value.strip() for value in (item.word, item.phonetic, item.meaning))
                or item.part_of_speech not in PART_OF_SPEECH_VALUES
                or item.part_of_speech == "other"
            ):
                raise Sprint5SeedError(f"{item.label}: invalid word, meaning, phonetic or part of speech")
            groups[item.topic].append(item)

    for code, items in groups.items():
        if len(items) != 10 or len({item.word.casefold() for item in items}) != 10:
            raise Sprint5SeedError(f"Topic {code}: expected 10 distinct vocabulary entries")
        if sum(bool(item.picture_distractors) for item in items) != 5:
            raise Sprint5SeedError(f"Topic {code}: expected exactly 5 picture-choice targets")
    return groups


def _has_media_content(path: Path, suffix: str) -> bool:
    with path.open("rb") as file:
        size = file.seek(0, 2)
        file.seek(0)
        if suffix == ".png":
            header = file.read(24)
            if size < 57 or not header.startswith(b"\x89PNG\r\n\x1a\n\0\0\0\x0dIHDR"):
                return False
            if not (int.from_bytes(header[16:20], "big") and int.from_bytes(header[20:24], "big")):
                return False
            file.seek(-12, 2)
            return file.read() == b"\0\0\0\0IEND\xaeB`\x82"

        header = file.read(10)
        audio_start = 0
        if header.startswith(b"ID3"):
            if len(header) != 10 or any(byte >= 128 for byte in header[6:10]):
                return False
            tag_size = 0
            for byte in header[6:10]:
                tag_size = (tag_size << 7) | byte
            audio_start = 10 + tag_size + (10 if header[3] == 4 and header[5] & 0x10 else 0)
        if size < audio_start + 24:
            return False
        file.seek(audio_start)
        first, second, third, _ = file.read(4)
        # MPEG audio sync, a valid version, Layer III, bitrate and sample-rate indices.
        return (
            first == 0xFF
            and second & 0xE0 == 0xE0
            and second & 0x18 != 0x08
            and second & 0x06 == 0x02
            and third >> 4 not in (0, 15)
            and third & 0x0C != 0x0C
        )


def _media_path(root: Path, item: VocabularySeed, kind: str, filename: str, suffix: str) -> str:
    relative = f"media/{kind}/{item.topic}/{filename}"
    try:
        validate_media_path(relative)
        if "/" in filename or not filename.casefold().endswith(suffix):
            raise ValueError(f"expected a single {suffix} filename")
        path = root
        for part in relative.split("/"):
            # Resolve exact directory-entry spelling even on case-insensitive Windows.
            matches = [entry.name for entry in path.iterdir() if entry.name.casefold() == part.casefold()]
            if part not in matches:
                raise ValueError(f"missing exact filename/component {part!r}; case matches: {matches}")
            path /= part
        if not path.resolve(strict=True).is_relative_to(root) or not path.is_file():
            raise ValueError("expected a regular file inside the static root")
        if not _has_media_content(path, suffix):
            raise ValueError(f"empty, invalid or truncated {suffix} content")
    except (OSError, ValueError) as exc:
        raise Sprint5SeedError(f"{item.label}, {kind} {relative!r}: {exc}") from exc
    return relative


def _question(
    activity_id: UUID,
    vocabulary_id: UUID,
    question_type: str,
    prompt: str,
    payload: dict[str, Any],
    item: VocabularySeed,
    explanation: str | None = None,
) -> dict[str, Any]:
    question_id = uuid5(activity_id, f"question:{vocabulary_id}")
    try:
        validated_payload = validate_payload(question_type, payload).model_dump(mode="json", by_alias=True)
    except ValueError as exc:
        raise Sprint5SeedError(f"{item.label}, {question_type} question {question_id}: {exc}") from exc
    return {
        "id": question_id,
        "activity_id": activity_id,
        "type": question_type,
        "source": "round",
        "topic_id": None,
        "grammar_lesson_id": None,
        "purpose": None,
        "prompt": prompt,
        "explanation": explanation,
        "image_url": None,
        "audio_url": None,
        "payload": validated_payload,
    }


def _recognition_question(
    activity_id: UUID,
    item: VocabularySeed,
    index: int,
    words: list[str],
    vocabulary: dict[str, dict[str, Any]],
) -> dict[str, Any]:
    if item.picture_distractors:
        distractors = item.picture_distractors.split("|")
        question_type, option_key, value_key = "picture_choice", "imageUrl", "image_url"
        prompt = f"Chọn hình minh họa cho từ «{item.word}»."
    else:
        distractors = [words[(index + step) % len(words)] for step in range(1, OPTION_COUNT)]
        question_type, option_key, value_key = "multiple_choice", "text", "meaning"
        prompt = f"Từ «{item.word}» có nghĩa là gì?"

    choices = list(distractors)
    position = index % OPTION_COUNT
    choices.insert(position, item.word)
    if len(choices) != OPTION_COUNT or len(set(choices)) != OPTION_COUNT or set(choices) - vocabulary.keys():
        raise Sprint5SeedError(f"{item.label}, {question_type}: invalid option words {choices!r}")

    target_id = vocabulary[item.word]["id"]
    # Option identity follows the vocabulary association, independent of its slot or media filename.
    options = [
        {
            "id": str(uuid5(activity_id, f"option:{target_id}:{vocabulary[word]['id']}")),
            option_key: vocabulary[word][value_key],
        }
        for word in choices
    ]
    if len({option[option_key].casefold() for option in options}) != OPTION_COUNT:
        raise Sprint5SeedError(f"{item.label}, {question_type}: duplicate option content")
    return _question(
        activity_id,
        target_id,
        question_type,
        prompt,
        {"options": options, "correctOptionId": options[position]["id"]},
        item,
        f"«{item.word}» nghĩa là «{item.meaning}».",
    )


@dataclass
class SeedRows:
    activities: list[dict[str, Any]]
    vocabulary: list[dict[str, Any]]
    questions: list[dict[str, Any]]


def build_seed_rows(data_file: Path = DATA_FILE, static_root: Path = STATIC_ROOT) -> SeedRows:
    """Validate all media and payloads before planning any database writes."""
    root = static_root.resolve()
    topics = {topic.key: topic for topic in TOPICS}
    lessons = {lesson.topic_key: lesson for lesson in LESSONS}
    rows = SeedRows([], [], [])
    for code, items in _load_vocabulary(data_file).items():
        topic = topics[TOPIC_KEYS[code]]
        lesson = lessons[topic.key]
        level_id = LEVEL_ID_BY_ORDER[topic.level_order]
        activities = {
            number: uuid5(lesson.id, f"activity:{number}") for number in range(1, len(ROUND_TITLES) + 1)
        }
        rows.activities.extend(
            {
                "id": activities[number],
                "lesson_id": lesson.id,
                "round_number": number,
                "round_type": round_type,
                "title": title,
            }
            for number, (round_type, title) in enumerate(zip(ROUND_TYPE_VALUES, ROUND_TITLES, strict=True), 1)
        )
        vocabulary = {}
        for item in items:
            vocabulary[item.word] = {
                "id": uuid5(topic.id, f"vocabulary:{item.word}"),
                "topic_id": topic.id,
                "level_id": level_id,
                "word": item.word,
                "meaning": item.meaning,
                "part_of_speech": item.part_of_speech,
                "phonetic": item.phonetic,
                "image_url": _media_path(root, item, "image", item.image_filename, ".png"),
                "audio_url": _media_path(root, item, "audio", item.audio_filename, ".mp3"),
            }
        words = list(vocabulary)
        rows.vocabulary.extend(vocabulary.values())
        for index, item in enumerate(items):
            vocabulary_id = vocabulary[item.word]["id"]
            rows.questions.append(
                _question(
                    activities[1],
                    vocabulary_id,
                    "learn_card",
                    item.word,
                    {"vocabularyId": str(vocabulary_id)},
                    item,
                )
            )
            # Every picture option comes from the already validated same-topic vocabulary above.
            rows.questions.append(_recognition_question(activities[2], item, index, words, vocabulary))
    return rows


@dataclass
class TablePlan:
    table: Table
    rows_to_upsert: list[dict[str, Any]]
    mutable_fields: tuple[str, ...]
    counts: TableCounts


def _plan_rows(
    session: Session,
    table: Table,
    desired: list[dict[str, Any]],
    scope_field: str,
    identity_fields: tuple[str, ...],
) -> TablePlan:
    expected = {row["id"]: row for row in desired}
    scope = {row[scope_field] for row in desired}
    mutable_fields = tuple(key for key in desired[0] if key not in ("id", *identity_fields))
    existing = (
        session.execute(
            select(table).where(
                or_(table.c.id.in_(expected), table.c[scope_field].in_(scope)),
            )
        )
        .mappings()
        .all()
    )
    changes = []
    for row in existing:
        row_identity = {key: row[key] for key in identity_fields}
        candidate = expected.pop(row["id"], None)
        if candidate is None:
            raise Sprint5SeedError(
                f"{table.name}: unexpected UUID {row['id']} for {row_identity} "
                f"({row.get('word') or row.get('prompt') or row.get('title')!r}); resolve the existing row first",
            )
        if any(row[key] != candidate[key] for key in identity_fields):
            raise Sprint5SeedError(f"{table.name}: UUID {row['id']} has conflicting identity {row_identity}")
        if any(row[key] != candidate[key] for key in mutable_fields):
            changes.append(candidate)
    counts = TableCounts(len(expected), len(changes), len(existing) - len(changes))
    return TablePlan(table, [*expected.values(), *changes], mutable_fields, counts)


def _plan(session: Session, rows: SeedRows) -> list[TablePlan]:
    return [
        _plan_rows(
            session,
            cast(Table, Activity.__table__),
            rows.activities,
            "lesson_id",
            ("lesson_id", "round_number"),
        ),
        _plan_rows(
            session,
            cast(Table, Vocabulary.__table__),
            rows.vocabulary,
            "topic_id",
            ("topic_id", "level_id", "word"),
        ),
        _plan_rows(
            session, cast(Table, Question.__table__), rows.questions, "activity_id", ("activity_id", "source")
        ),
    ]


def run(
    session: Session,
    *,
    apply: bool,
    data_file: Path = DATA_FILE,
    static_root: Path = STATIC_ROOT,
) -> dict[str, TableCounts]:
    """Run inside a caller-owned transaction, with no writes on dry-run."""
    rows = build_seed_rows(data_file, static_root)
    head = get_head_revision()
    if MigrationContext.configure(session.connection()).get_current_heads() != (head,):
        raise SchemaNotAtHeadError("Run 'alembic upgrade head' before this seed")
    if apply:
        # Serialize writers while checking natural keys that lack unique constraints.
        session.execute(text("LOCK TABLE levels, topics, lessons IN SHARE MODE"))
        session.execute(text("LOCK TABLE activities, vocabularies, questions IN SHARE ROW EXCLUSIVE MODE"))
    verify_final_state(load_state(session))
    plans = _plan(session, rows)
    if apply and any(plan.rows_to_upsert for plan in plans):
        for plan in plans:
            if plan.rows_to_upsert:
                statement = insert(plan.table).values(plan.rows_to_upsert)
                session.execute(
                    statement.on_conflict_do_update(
                        index_elements=[plan.table.c.id],
                        set_={key: statement.excluded[key] for key in plan.mutable_fields},
                    )
                )
        if any(plan.rows_to_upsert for plan in _plan(session, rows)):
            raise SeedPostConditionError("Sprint 5 content does not match the dataset after writing")
    return {plan.table.name: plan.counts for plan in plans}


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Write changes (defaults to dry-run)")
    parser.add_argument("--confirm-database", help="With --apply, must match the configured database name")
    args = parser.parse_args(argv)
    apply_default_timeouts()
    from app.database.session import SessionLocal, engine

    print(f"target: {masked_url(engine.url)}")
    print(f"mode: {'apply' if args.apply else 'dry-run'}")
    if args.apply and (not args.confirm_database or args.confirm_database != engine.url.database):
        parser.error("--apply requires --confirm-database matching the configured database name")
    try:
        with SessionLocal.begin() as session:
            counts = run(session, apply=args.apply)
    except (Sprint5SeedError, SeedPostConditionError, SchemaNotAtHeadError, OSError, csv.Error) as exc:
        print(f"BLOCKED: {exc}", file=sys.stderr)
        return 2
    except DBAPIError as exc:
        constraint = getattr(getattr(exc.orig, "diag", None), "constraint_name", None)
        print(f"ERROR: {type(exc).__name__}; constraint={constraint}", file=sys.stderr)
        return 1
    except SQLAlchemyError as exc:
        print(f"ERROR: {type(exc).__name__}", file=sys.stderr)
        return 1
    for table, count in counts.items():
        print(f"{table}: inserted={count.inserted} updated={count.updated} unchanged={count.unchanged}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
