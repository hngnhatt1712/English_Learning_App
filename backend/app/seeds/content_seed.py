"""Idempotent planner, runner and CLI for seeding topics and lessons (branch L-existing).

Levels are owned by ``app.database.seeds.levels`` and referenced via ``LEVEL_ID_BY_ORDER``.
This runner verifies that the three expected levels exist before planning changes to ``topics``
and ``lessons``, detects natural-key conflicts and unexpected rows prior to writing, and
executes PostgreSQL ``ON CONFLICT (id) DO UPDATE`` statements only for inserted/updated rows.
"""
from __future__ import annotations

import argparse
import os
import sys
import uuid
from collections.abc import Callable, Mapping, Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from alembic.config import Config
from alembic.script import ScriptDirectory
from sqlalchemy import Table, or_, select, text
from sqlalchemy.dialects.postgresql import Insert
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.engine import URL, make_url

from app.models.lesson import Lesson
from app.models.level import Level
from app.models.topic import Topic
from app.seeds.content_data import (
    LESSONS_PER_TOPIC,
    SEED_DATA,
    TOPICS_PER_LEVEL,
    SeedDataset,
    validate_seed_data,
)

BACKEND_DIR = Path(__file__).resolve().parents[2]
ALEMBIC_INI_PATH = BACKEND_DIR / "alembic.ini"
ALEMBIC_SCRIPT_DIR = BACKEND_DIR / "alembic"

_SQL_ALEMBIC_VERSION = text("SELECT version_num FROM alembic_version")
_TOPIC_COLUMNS = ("level_id", "name", "description", "order_index", "theme_key")
_LESSON_COLUMNS = ("topic_id", "title", "description", "order_index")


class SeedConflictError(RuntimeError):
    """Raised when an existing row with another id collides on a natural unique key."""


class UnexpectedRowsError(RuntimeError):
    """Raised when extra non-seed rows exist under the seeded levels or topics."""


class LevelMissingError(RuntimeError):
    """Raised when any of the three required levels is missing or has an unexpected order_index."""


class SchemaNotAtHeadError(RuntimeError):
    """Raised when the database alembic_version does not match the current script head."""


class SeedPostConditionError(RuntimeError):
    """Raised when the database state after seeding violates expected invariants."""


@dataclass(frozen=True)
class LevelRecord:
    """Snapshot of an existing Level row in the database."""

    id: uuid.UUID
    order_index: int


@dataclass(frozen=True)
class TopicRecord:
    """Snapshot of an existing Topic row in the database."""

    id: uuid.UUID
    level_id: uuid.UUID
    name: str
    description: str | None
    order_index: int
    theme_key: str


@dataclass(frozen=True)
class LessonRecord:
    """Snapshot of an existing Lesson row in the database."""

    id: uuid.UUID
    topic_id: uuid.UUID
    title: str
    description: str | None
    order_index: int


@dataclass(frozen=True)
class ExistingState:
    """In-memory snapshot of existing database rows relevant to the content seed."""

    levels: dict[uuid.UUID, LevelRecord]
    topics: dict[uuid.UUID, TopicRecord]
    lessons: dict[uuid.UUID, LessonRecord]


@dataclass(frozen=True)
class TableCounts:
    """Row action counters for a single table."""

    inserted: int = 0
    updated: int = 0
    unchanged: int = 0


@dataclass(frozen=True)
class SeedPlan:
    """Pure execution plan produced by comparing ExistingState against SeedDataset."""

    topics_to_upsert: tuple[dict[str, Any], ...]
    lessons_to_upsert: tuple[dict[str, Any], ...]
    topic_counts: TableCounts
    lesson_counts: TableCounts

    @property
    def has_changes(self) -> bool:
        return bool(self.topics_to_upsert or self.lessons_to_upsert)


@dataclass(frozen=True)
class SeedReport:
    """Summary returned by ``run`` for CLI reporting and tests."""

    applied: bool
    topic_counts: TableCounts
    lesson_counts: TableCounts

    @property
    def has_changes(self) -> bool:
        return (
            self.topic_counts.inserted
            + self.topic_counts.updated
            + self.lesson_counts.inserted
            + self.lesson_counts.updated
        ) > 0


def _table(model: type[Any]) -> Table:
    tbl = model.__table__
    assert isinstance(tbl, Table)
    return tbl


def masked_url(url: URL) -> str:
    """Format a database URL with credentials redacted."""
    return f"{url.drivername}://***@{url.host}:{url.port}/{url.database}"


def apply_default_timeouts() -> None:
    """Set default libpq timeout environment variables if not already configured."""
    os.environ.setdefault("PGCONNECT_TIMEOUT", "5")
    os.environ.setdefault("PGOPTIONS", "-c lock_timeout=5000 -c statement_timeout=60000")


def get_head_revision() -> str:
    """Return the current single Alembic head revision offline via ScriptDirectory."""
    cfg = Config(str(ALEMBIC_INI_PATH))
    cfg.set_main_option("script_location", str(ALEMBIC_SCRIPT_DIR))
    head = ScriptDirectory.from_config(cfg).get_current_head()
    if not head:
        raise SchemaNotAtHeadError("Alembic script directory has no current head revision.")
    return head


def load_state(session: Any, data: SeedDataset = SEED_DATA) -> ExistingState:
    """Read existing levels, topics, and lessons relevant to the seed dataset (SELECT only)."""
    level_ids = list(data.level_id_by_order.values())
    level_orders = list(data.level_id_by_order.keys())
    topic_ids = [t.id for t in data.topics]
    lesson_ids = [les.id for les in data.lessons]

    level_stmt = select(Level.id, Level.order_index).where(
        or_(Level.id.in_(level_ids), Level.order_index.in_(level_orders))
    )
    levels = {
        row.id: LevelRecord(id=row.id, order_index=row.order_index)
        for row in session.execute(level_stmt)
    }

    topic_stmt = select(
        Topic.id,
        Topic.level_id,
        Topic.name,
        Topic.description,
        Topic.order_index,
        Topic.theme_key,
    ).where(or_(Topic.level_id.in_(level_ids), Topic.id.in_(topic_ids)))
    topics = {
        row.id: TopicRecord(
            id=row.id,
            level_id=row.level_id,
            name=row.name,
            description=row.description,
            order_index=row.order_index,
            theme_key=row.theme_key,
        )
        for row in session.execute(topic_stmt)
    }

    all_topic_ids = list(set(topic_ids) | set(topics.keys()))
    lesson_stmt = select(
        Lesson.id,
        Lesson.topic_id,
        Lesson.title,
        Lesson.description,
        Lesson.order_index,
    ).where(or_(Lesson.topic_id.in_(all_topic_ids), Lesson.id.in_(lesson_ids)))
    lessons = {
        row.id: LessonRecord(
            id=row.id,
            topic_id=row.topic_id,
            title=row.title,
            description=row.description,
            order_index=row.order_index,
        )
        for row in session.execute(lesson_stmt)
    }

    return ExistingState(levels=levels, topics=topics, lessons=lessons)


def _check_levels_exist(state: ExistingState, data: SeedDataset) -> None:
    for expected_order, expected_id in data.level_id_by_order.items():
        record = state.levels.get(expected_id)
        if record is None:
            raise LevelMissingError(
                f"Required level for order_index={expected_order} is missing; seed levels first."
            )
        if record.order_index != expected_order:
            raise LevelMissingError(
                f"Level {expected_id} has order_index={record.order_index}, expected {expected_order}."
            )
    for record in state.levels.values():
        expected_id = data.level_id_by_order.get(record.order_index)
        if expected_id is not None and record.id != expected_id:
            raise LevelMissingError(
                f"Another level row occupies order_index={record.order_index}."
            )


def plan_changes(state: ExistingState, data: SeedDataset = SEED_DATA) -> SeedPlan:
    """Pure planner: validate preconditions, detect conflicts/unexpected rows, compute diffs."""
    validate_seed_data(data)
    _check_levels_exist(state, data)

    seed_level_ids = set(data.level_id_by_order.values())
    seed_topic_ids = {t.id for t in data.topics}
    seed_lesson_ids = {les.id for les in data.lessons}
    topic_id_by_key = {t.key: t.id for t in data.topics}

    expected_topic_by_natural_key: dict[tuple[uuid.UUID, int], uuid.UUID] = {
        (data.level_id_by_order[t.level_order], t.order_index): t.id for t in data.topics
    }
    expected_lesson_by_natural_key: dict[tuple[uuid.UUID, int], uuid.UUID] = {
        (topic_id_by_key[les.topic_key], les.order_index): les.id for les in data.lessons
    }

    for existing_topic in state.topics.values():
        nat_key = (existing_topic.level_id, existing_topic.order_index)
        expected_topic_id = expected_topic_by_natural_key.get(nat_key)
        if expected_topic_id is not None and existing_topic.id != expected_topic_id:
            raise SeedConflictError(
                f"Natural key conflict on topics(level_id, order_index={existing_topic.order_index})."
            )
        if existing_topic.id not in seed_topic_ids and existing_topic.level_id in seed_level_ids:
            raise UnexpectedRowsError(
                "Unexpected non-seed topic row found under a seeded level."
            )

    topic_nat_owners: dict[tuple[uuid.UUID, int], uuid.UUID] = {}
    for existing_topic in state.topics.values():
        nat_key = (existing_topic.level_id, existing_topic.order_index)
        prev = topic_nat_owners.get(nat_key)
        if prev is not None and prev != existing_topic.id:
            raise SeedConflictError("Duplicate natural key in existing topic state.")
        topic_nat_owners[nat_key] = existing_topic.id

    for existing_topic in state.topics.values():
        if existing_topic.id in seed_topic_ids:
            for t in data.topics:
                if t.id == existing_topic.id:
                    target_nat = (data.level_id_by_order[t.level_order], t.order_index)
                    owner = topic_nat_owners.get(target_nat)
                    if owner is not None and owner != t.id:
                        raise SeedConflictError(
                            "Target topic natural key is currently occupied by another topic row."
                        )

    for existing_lesson in state.lessons.values():
        nat_key = (existing_lesson.topic_id, existing_lesson.order_index)
        expected_lesson_id = expected_lesson_by_natural_key.get(nat_key)
        if expected_lesson_id is not None and existing_lesson.id != expected_lesson_id:
            raise SeedConflictError(
                f"Natural key conflict on lessons(topic_id, order_index={existing_lesson.order_index})."
            )
        if existing_lesson.id not in seed_lesson_ids and existing_lesson.topic_id in seed_topic_ids:
            raise UnexpectedRowsError(
                "Unexpected non-seed lesson row found under a seeded topic."
            )

    lesson_nat_owners: dict[tuple[uuid.UUID, int], uuid.UUID] = {}
    for existing_lesson in state.lessons.values():
        nat_key = (existing_lesson.topic_id, existing_lesson.order_index)
        prev = lesson_nat_owners.get(nat_key)
        if prev is not None and prev != existing_lesson.id:
            raise SeedConflictError("Duplicate natural key in existing lesson state.")
        lesson_nat_owners[nat_key] = existing_lesson.id

    for existing_lesson in state.lessons.values():
        if existing_lesson.id in seed_lesson_ids:
            for les in data.lessons:
                if les.id == existing_lesson.id:
                    target_nat = (topic_id_by_key[les.topic_key], les.order_index)
                    owner = lesson_nat_owners.get(target_nat)
                    if owner is not None and owner != les.id:
                        raise SeedConflictError(
                            "Target lesson natural key is currently occupied by another lesson row."
                        )

    topics_to_upsert: list[dict[str, Any]] = []
    t_inserted = 0
    t_updated = 0
    t_unchanged = 0

    for t in data.topics:
        level_id = data.level_id_by_order[t.level_order]
        row_dict: dict[str, Any] = {
            "id": t.id,
            "level_id": level_id,
            "name": t.name,
            "description": t.description,
            "order_index": t.order_index,
            "theme_key": t.theme_key,
        }
        current = state.topics.get(t.id)
        if current is None:
            t_inserted += 1
            topics_to_upsert.append(row_dict)
        elif (
            current.level_id == level_id
            and current.name == t.name
            and current.description == t.description
            and current.order_index == t.order_index
            and current.theme_key == t.theme_key
        ):
            t_unchanged += 1
        else:
            t_updated += 1
            topics_to_upsert.append(row_dict)

    lessons_to_upsert: list[dict[str, Any]] = []
    l_inserted = 0
    l_updated = 0
    l_unchanged = 0

    for les in data.lessons:
        topic_id = topic_id_by_key[les.topic_key]
        row_dict = {
            "id": les.id,
            "topic_id": topic_id,
            "title": les.title,
            "description": les.description,
            "order_index": les.order_index,
        }
        current = state.lessons.get(les.id)
        if current is None:
            l_inserted += 1
            lessons_to_upsert.append(row_dict)
        elif (
            current.topic_id == topic_id
            and current.title == les.title
            and current.description == les.description
            and current.order_index == les.order_index
        ):
            l_unchanged += 1
        else:
            l_updated += 1
            lessons_to_upsert.append(row_dict)

    return SeedPlan(
        topics_to_upsert=tuple(topics_to_upsert),
        lessons_to_upsert=tuple(lessons_to_upsert),
        topic_counts=TableCounts(inserted=t_inserted, updated=t_updated, unchanged=t_unchanged),
        lesson_counts=TableCounts(inserted=l_inserted, updated=l_updated, unchanged=l_unchanged),
    )


def verify_final_state(state: ExistingState, data: SeedDataset = SEED_DATA) -> None:
    """Pure post-condition check: 3 topics per level, 1 lesson per topic, exact field match."""
    for expected_order, expected_id in data.level_id_by_order.items():
        lvl = state.levels.get(expected_id)
        if lvl is None or lvl.order_index != expected_order:
            raise SeedPostConditionError(
                f"Post-condition failed: level order {expected_order} is missing or mismatched."
            )

    if len(state.topics) != len(data.topics):
        raise SeedPostConditionError(
            f"Post-condition failed: expected {len(data.topics)} topics, found {len(state.topics)}."
        )

    topics_per_level: dict[uuid.UUID, int] = {lvl_id: 0 for lvl_id in data.level_id_by_order.values()}
    for existing_topic in state.topics.values():
        if existing_topic.level_id not in topics_per_level:
            raise SeedPostConditionError("Post-condition failed: topic belongs to unknown level.")
        topics_per_level[existing_topic.level_id] += 1

    for lvl_id, count in topics_per_level.items():
        if count != TOPICS_PER_LEVEL:
            raise SeedPostConditionError(
                f"Post-condition failed: level {lvl_id} has {count} topics, expected {TOPICS_PER_LEVEL}."
            )

    for t in data.topics:
        expected_level_id = data.level_id_by_order[t.level_order]
        actual = state.topics.get(t.id)
        if actual is None:
            raise SeedPostConditionError(f"Post-condition failed: topic {t.key} is missing.")
        if (
            actual.level_id != expected_level_id
            or actual.name != t.name
            or actual.description != t.description
            or actual.order_index != t.order_index
            or actual.theme_key != t.theme_key
        ):
            raise SeedPostConditionError(
                f"Post-condition failed: topic {t.key} attributes do not match seed data."
            )

    if len(state.lessons) != len(data.lessons):
        raise SeedPostConditionError(
            f"Post-condition failed: expected {len(data.lessons)} lessons, found {len(state.lessons)}."
        )

    topic_id_by_key = {t.key: t.id for t in data.topics}
    lessons_per_topic: dict[uuid.UUID, int] = {t.id: 0 for t in data.topics}
    for existing_lesson in state.lessons.values():
        if existing_lesson.topic_id not in lessons_per_topic:
            raise SeedPostConditionError("Post-condition failed: lesson belongs to unknown topic.")
        lessons_per_topic[existing_lesson.topic_id] += 1

    for t_id, count in lessons_per_topic.items():
        if count != LESSONS_PER_TOPIC:
            raise SeedPostConditionError(
                f"Post-condition failed: topic {t_id} has {count} lessons, expected {LESSONS_PER_TOPIC}."
            )

    for les in data.lessons:
        expected_topic_id = topic_id_by_key[les.topic_key]
        actual_les = state.lessons.get(les.id)
        if actual_les is None:
            raise SeedPostConditionError(f"Post-condition failed: lesson {les.key} is missing.")
        if (
            actual_les.topic_id != expected_topic_id
            or actual_les.title != les.title
            or actual_les.description != les.description
            or actual_les.order_index != les.order_index
        ):
            raise SeedPostConditionError(
                f"Post-condition failed: lesson {les.key} attributes do not match seed data."
            )


def build_topic_upsert(rows: Sequence[Mapping[str, Any]]) -> Insert:
    """Build a PostgreSQL INSERT ... ON CONFLICT (id) DO UPDATE statement for topics."""
    table = _table(Topic)
    stmt = pg_insert(table).values(list(rows))
    return stmt.on_conflict_do_update(
        index_elements=[table.c.id],
        set_={name: stmt.excluded[name] for name in _TOPIC_COLUMNS},
    )


def build_lesson_upsert(rows: Sequence[Mapping[str, Any]]) -> Insert:
    """Build a PostgreSQL INSERT ... ON CONFLICT (id) DO UPDATE statement for lessons."""
    table = _table(Lesson)
    stmt = pg_insert(table).values(list(rows))
    return stmt.on_conflict_do_update(
        index_elements=[table.c.id],
        set_={name: stmt.excluded[name] for name in _LESSON_COLUMNS},
    )


def apply_plan(session: Any, plan: SeedPlan) -> None:
    """Execute upsert statements in foreign-key order (Topic -> Lesson) and flush."""
    if plan.topics_to_upsert:
        session.execute(build_topic_upsert(plan.topics_to_upsert))
    if plan.lessons_to_upsert:
        session.execute(build_lesson_upsert(plan.lessons_to_upsert))
    if plan.has_changes:
        session.flush()


def _assert_schema_at_head(session: Any, head_provider: Callable[[], str]) -> None:
    expected_head = head_provider()
    try:
        rows = list(session.execute(_SQL_ALEMBIC_VERSION))
    except Exception as exc:
        raise SchemaNotAtHeadError(
            "Unable to read alembic_version; run 'alembic upgrade head' first."
        ) from exc
    versions = [str(row[0]) for row in rows]
    if versions != [expected_head]:
        raise SchemaNotAtHeadError(
            "Database schema is not at Alembic head; run 'alembic upgrade head' first."
        )


def run(
    session: Any,
    *,
    apply: bool,
    data: SeedDataset = SEED_DATA,
    head_provider: Callable[[], str] | None = None,
    state_loader: Callable[[Any, SeedDataset], ExistingState] | None = None,
) -> SeedReport:
    """Validate schema and current state, plan diffs, and optionally apply them in one transaction."""
    resolved_head_provider = get_head_revision if head_provider is None else head_provider
    resolved_state_loader = load_state if state_loader is None else state_loader
    _assert_schema_at_head(session, resolved_head_provider)
    state = resolved_state_loader(session, data)
    plan = plan_changes(state, data)

    if not apply:
        return SeedReport(
            applied=False,
            topic_counts=plan.topic_counts,
            lesson_counts=plan.lesson_counts,
        )

    if not plan.has_changes:
        verify_final_state(state, data)
        return SeedReport(
            applied=False,
            topic_counts=plan.topic_counts,
            lesson_counts=plan.lesson_counts,
        )

    try:
        apply_plan(session, plan)
        after_state = resolved_state_loader(session, data)
        verify_final_state(after_state, data)
        session.commit()
    except Exception:
        session.rollback()
        raise

    return SeedReport(
        applied=True,
        topic_counts=plan.topic_counts,
        lesson_counts=plan.lesson_counts,
    )


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="python -m app.seeds.content_seed")
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Write planned changes to the database (defaults to dry-run).",
    )
    parser.add_argument(
        "--confirm-database",
        default=None,
        help="Required with --apply; must match the configured database name.",
    )
    return parser


def _default_session_factory() -> Any:
    from app.database.session import SessionLocal

    return SessionLocal()


def _default_settings() -> Any:
    from app.core.config import settings

    return settings


def _emit(line: str) -> None:
    sys.stdout.write(f"{line}\n")
    sys.stdout.flush()


def main(
    argv: Sequence[str] | None = None,
    *,
    session_factory: Callable[[], Any] | None = None,
    settings_obj: Any = None,
    head_provider: Callable[[], str] | None = None,
    data: SeedDataset = SEED_DATA,
) -> int:
    """CLI entrypoint. Exit codes: 0 success, 2 blocked by safety gate/conflict, 1 unexpected error."""
    apply_default_timeouts()
    args = _build_parser().parse_args(argv)
    cfg_settings = _default_settings() if settings_obj is None else settings_obj
    url = make_url(cfg_settings.DATABASE_URL)
    _emit(f"target: {masked_url(url)}")
    _emit(f"mode: {'apply' if args.apply else 'dry-run'}")

    if args.apply:
        if not args.confirm_database:
            _emit("REFUSED: --apply requires --confirm-database <database_name>.")
            return 2
        if args.confirm_database != url.database:
            _emit("REFUSED: --confirm-database does not match the configured database name.")
            return 2

    factory = _default_session_factory if session_factory is None else session_factory
    try:
        with factory() as session:
            report = run(
                session,
                apply=bool(args.apply),
                data=data,
                head_provider=head_provider,
            )
    except (
        SeedConflictError,
        UnexpectedRowsError,
        LevelMissingError,
        SchemaNotAtHeadError,
        SeedPostConditionError,
    ) as exc:
        _emit(f"BLOCKED ({type(exc).__name__}): {exc}")
        return 2
    except Exception as exc:  # noqa: BLE001
        _emit(f"ERROR ({type(exc).__name__})")
        return 1

    _emit(
        f"topics: inserted={report.topic_counts.inserted} "
        f"updated={report.topic_counts.updated} "
        f"unchanged={report.topic_counts.unchanged}"
    )
    _emit(
        f"lessons: inserted={report.lesson_counts.inserted} "
        f"updated={report.lesson_counts.updated} "
        f"unchanged={report.lesson_counts.unchanged}"
    )
    _emit(f"applied: {report.applied}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
