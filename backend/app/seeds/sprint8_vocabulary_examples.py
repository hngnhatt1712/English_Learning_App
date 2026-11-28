"""Backfill official Vocabulary examples with python -m app.seeds.sprint8_vocabulary_examples."""

from __future__ import annotations

import argparse
from collections.abc import Callable, Iterator, Mapping, Sequence
from contextlib import AbstractContextManager, contextmanager
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol, cast
from uuid import UUID

from alembic.runtime.migration import MigrationContext
from app.models.vocabulary import Vocabulary
from app.seeds import sprint5_seed
from app.seeds.content_seed import apply_default_timeouts
from pydantic import ValidationError
from sqlalchemy import Table, func, or_, select, text, update
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

DATABASE = "english_learning_app"
SCHEMA = "public"
IDENTITY = ("topic_id", "level_id", "word", "part_of_speech")


class ExampleSeedError(ValueError):
    """Invalid target, identity or write result."""


@dataclass(frozen=True)
class Change:
    id: UUID
    word: str
    previous: str | None
    example: str


class Repository(Protocol):
    def transaction(self) -> AbstractContextManager[Any]: ...
    def read(self, ids: Sequence[UUID]) -> list[dict[str, Any]]: ...
    def update_example(self, change: Change) -> int: ...


def plan_examples(
    desired: Sequence[Mapping[str, Any]], existing: Sequence[Mapping[str, Any]],
) -> list[Change]:
    changes = []
    problems = []
    by_id = {row["id"]: row for row in existing}
    for target in desired:
        vocabulary_id = target["id"]
        row = by_id.get(vocabulary_id)
        if row is None:
            problems.append(f"Missing Vocabulary {vocabulary_id} ({target['word']})")
            continue
        fields = [field for field in IDENTITY if row[field] != target[field]]
        if fields:
            problems.append(f"Vocabulary {vocabulary_id} ({target['word']}) differs in {', '.join(fields)}")
            continue
        previous = row["example"]
        if previous == target["example"]:
            continue
        if previous is not None and previous.strip(" "):
            problems.append(f"Vocabulary {vocabulary_id} ({target['word']}) has a different nonblank example")
            continue
        changes.append(Change(vocabulary_id, target["word"], previous, target["example"]))
    if problems:
        raise ExampleSeedError("\n".join(problems))
    return changes


def run(
    repo: Repository, desired: Sequence[Mapping[str, Any]], *, apply: bool = False,
    record: Callable[[Sequence[Change]], None] | None = None, emit: Callable[[str], None] = print,
) -> list[Change]:
    ids = [row["id"] for row in desired]
    with repo.transaction():
        changes = plan_examples(desired, repo.read(ids))
        for change in changes:
            emit(f"{change.id} | {change.word} | {change.example}")
        emit(f"would_update={len(changes)} already_matched={len(ids) - len(changes)} would_overwrite=0")
        if apply and changes:
            if record is None:
                raise ExampleSeedError("A rollback record is required before applying changes")
            for change in changes:
                count = repo.update_example(change)
                if count != 1:
                    raise ExampleSeedError(f"Expected one updated row for {change.id}; got {count}")
            if plan_examples(desired, repo.read(ids)):
                raise ExampleSeedError("Stored examples differ from the validated seed")
            record(changes)
    if apply:
        emit(f"updated={len(changes)}")
    return changes


def rollback_sql(changes: Sequence[Change]) -> str:
    """Preserve each original value and guard against subsequent edits."""
    def literal(value: str | None) -> str:
        if value is None:
            return "NULL::text"
        escaped = value.replace("\\", "\\\\").replace("'", "''")
        return f"E'{escaped}'"

    values = ",\n".join(
        f"    ('{change.id}'::uuid, {literal(change.previous)}, {literal(change.example)})"
        for change in changes
    )
    return f"""-- S8-BE-08 Vocabulary example rollback. Review before running.
-- This restores Vocabulary examples only. Quiz questions are unaffected.
BEGIN;
DO $guard$
BEGIN
    IF current_database() <> '{DATABASE}' THEN
        RAISE EXCEPTION 'Wrong database for S8-BE-08 rollback';
    END IF;
END;
$guard$;
UPDATE {SCHEMA}.vocabularies AS v
SET example = changes.previous
FROM (VALUES
{values}
) AS changes(id, previous, applied)
WHERE v.id = changes.id AND v.example IS NOT DISTINCT FROM changes.applied
RETURNING v.id;
COMMIT;
"""


class PostgresRepository:
    def __init__(self, session: Session, *, apply: bool) -> None:
        self.session = session
        self.apply = apply
        self.table = cast(Table, Vocabulary.__table__)

    @contextmanager
    def transaction(self) -> Iterator[None]:
        with self.session.begin():
            if not self.apply:
                self.session.execute(text("SET TRANSACTION READ ONLY"))
            verify_target(self.session)
            yield

    def read(self, ids: Sequence[UUID]) -> list[dict[str, Any]]:
        cols = self.table.c
        statement = select(cols.id, *(cols[field] for field in IDENTITY), cols.example).where(
            cols.id.in_(ids),
        ).order_by(cols.id)
        if self.apply:
            statement = statement.with_for_update()
        return [dict(row) for row in self.session.execute(statement).mappings()]

    def update_example(self, change: Change) -> int:
        cols = self.table.c
        statement = update(self.table).where(
            cols.id == change.id, or_(cols.example.is_(None), func.btrim(cols.example) == ""),
        ).values(example=change.example).returning(cols.id)
        return len(self.session.scalars(statement).all())


def verify_target(session: Session) -> None:
    connection = session.connection()
    if connection.dialect.name != "postgresql" or connection.engine.url.database != DATABASE:
        raise ExampleSeedError(f"Expected a PostgreSQL connection configured for {DATABASE}")
    database, server, port, schema = session.execute(text(
        "SELECT current_database(), inet_server_addr(), inet_server_port(), current_schema()",
    )).one()
    url = connection.engine.url
    revision = MigrationContext.configure(connection).get_current_heads()
    print(f"Configured host={url.host} port={url.port or 5432}; "
          f"connected server={server} port={port} database={database} schema={schema} "
          f"revision={','.join(revision) or '(missing)'}")
    if (database, schema) != (DATABASE, SCHEMA) or not revision:
        raise ExampleSeedError(f"Expected migrated database {DATABASE}, schema {SCHEMA}")


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--dry-run", action="store_true", help="Preview changes (default)")
    modes.add_argument("--apply", action="store_true", help="Update blank examples")
    parser.add_argument("--rollback-file", type=Path, help="New SQL file recording changes made by --apply")
    args = parser.parse_args(argv)
    output = args.rollback_file
    if args.apply and output is None:
        parser.error("--apply requires --rollback-file")
    if output is not None:
        output = output.resolve()
        if not output.is_relative_to(Path(__file__).resolve().parents[3]):
            parser.error("--rollback-file must be within the project")

    def record(changes: Sequence[Change]) -> None:
        assert output is not None
        with output.open("x", encoding="utf-8", newline="\n") as file:
            file.write(rollback_sql(changes))

    try:
        desired = sprint5_seed.build_seed_rows().vocabulary
        apply_default_timeouts()
        try:
            from app.database.session import SessionLocal
        except ValidationError:
            raise ExampleSeedError("Backend settings failed validation") from None
        print("APPLY" if args.apply else "DRY-RUN")
        with SessionLocal() as session:
            run(PostgresRepository(session, apply=args.apply), desired, apply=args.apply, record=record)
    except (ValueError, OSError) as exc:
        print(f"Backfill failed: {exc}")
        return 1
    except SQLAlchemyError as exc:
        code = getattr(getattr(exc, "orig", None), "pgcode", None)
        print(f"Backfill failed: {type(exc).__name__}. PostgreSQL SQLSTATE: {code or 'unavailable'}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
