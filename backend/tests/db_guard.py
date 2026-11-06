"""Safety tooling for tests/commands that touch a real PostgreSQL server.

Only databases named ``<real>_scratch_t1`` on a local host may be created, written to or dropped.
The real database is read-only except for one guarded ``alembic upgrade head`` (``apply-real``).

Usage (from the ``backend`` directory)::

    python -m tests.db_guard describe
    python -m tests.db_guard inventory
    python -m tests.db_guard create-scratch
    python -m tests.db_guard drop-scratch
    python -m tests.db_guard alembic <upgrade|downgrade|check|current> [revision] --target scratch|real
    python -m tests.db_guard verify-catalog --target scratch|real
    python -m tests.db_guard probe-insert
    python -m tests.db_guard probe-clear
    python -m tests.db_guard apply-real --confirm-database <name> [--allow-existing-data]

Output never contains the full URL, the user name or the password (see ``masked``).
"""
from __future__ import annotations

import argparse
import os
import re
import sys
import uuid
from collections.abc import Callable, Sequence
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from alembic.config import Config
from alembic.script import ScriptDirectory
from alembic.util import CommandError
from sqlalchemy import column, create_engine, delete, func, literal, select, table, text
from sqlalchemy.engine import URL, Connection, Engine, make_url
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session
from sqlalchemy.pool import NullPool

from alembic import command as alembic_command
from app.core.config import settings

LOCAL_HOSTS = frozenset({"localhost", "127.0.0.1", "::1"})
SCRATCH_SUFFIX = "_scratch_t1"
FORBIDDEN_WORDS = ("prod", "live", "staging")
ROW_COUNT_CAP = 1001
_NAME_RE = re.compile(r"^[a-z0-9_]{1,63}$")

BACKEND_DIR = Path(__file__).resolve().parents[1]
ALEMBIC_INI_PATH = BACKEND_DIR / "alembic.ini"
ALEMBIC_SCRIPT_DIR = BACKEND_DIR / "alembic"

SCRATCH_COMMANDS = frozenset({"upgrade", "downgrade", "check", "current"})
REAL_COMMANDS = frozenset({"check", "current"})

STATE_EMPTY = "EMPTY"
STATE_AT_REVISION = "AT_REVISION"
STATE_DRIFT = "DRIFT"
STATE_HAS_DATA = "HAS_DATA"

NEW_TABLES = ("topics", "lessons", "user_topic_progress")

EXPECTED_CONSTRAINT_NAMES = frozenset(
    {
        "pk_topics",
        "fk_topics_level_id_levels",
        "uq_topics_level_id_order_index",
        "ck_topics_order_index_positive",
        "pk_lessons",
        "fk_lessons_topic_id_topics",
        "uq_lessons_topic_id_order_index",
        "ck_lessons_order_index_positive",
        "pk_user_topic_progress",
        "fk_user_topic_progress_user_id_users",
        "fk_user_topic_progress_topic_id_topics",
        "ck_user_topic_progress_status_valid",
        "ck_user_topic_progress_stars_range",
    }
)
EXPECTED_FK_DELETE_RULES: dict[str, str] = {
    "fk_topics_level_id_levels": "RESTRICT",
    "fk_lessons_topic_id_topics": "RESTRICT",
    "fk_user_topic_progress_user_id_users": "CASCADE",
    "fk_user_topic_progress_topic_id_topics": "RESTRICT",
}
EXPECTED_INDEX_NAME = "ix_user_topic_progress_topic_id"

# Fixed primary keys of the scratch-only probe rows used to exercise the downgrade guard.
PROBE_LEVEL_ID = uuid.UUID("00000000-0000-4000-8000-00000000c101")
PROBE_TOPIC_ID = uuid.UUID("00000000-0000-4000-8000-00000000c102")

# Fixed read-only statements (no string building).
_SQL_IN_RECOVERY = text("SELECT pg_is_in_recovery()")
_SQL_CURRENT_DATABASE = text("SELECT current_database()")
_SQL_SERVER_VERSION = text("SHOW server_version")
_SQL_SCRATCH_EXISTS = text("SELECT 1 FROM pg_database WHERE datname = :name")
_SQL_TABLES = text(
    "SELECT table_name FROM information_schema.tables "
    "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name"
)
_SQL_CONSTRAINTS = text(
    "SELECT t.relname::text AS table_name, c.conname::text AS constraint_name "
    "FROM pg_constraint c "
    "JOIN pg_class t ON t.oid = c.conrelid "
    "JOIN pg_namespace n ON n.oid = t.relnamespace "
    "WHERE n.nspname = 'public' AND t.relname::text = ANY(:tables) "
    "AND c.contype IN ('p', 'f', 'u', 'c')"
)
_SQL_FK_RULES = text(
    "SELECT constraint_name::text, delete_rule::text "
    "FROM information_schema.referential_constraints "
    "WHERE constraint_schema = 'public' AND constraint_name::text = ANY(:names)"
)
_SQL_COLUMN_DEFAULTS = text(
    "SELECT table_name::text, column_name::text, column_default::text "
    "FROM information_schema.columns "
    "WHERE table_schema = 'public' AND table_name::text = ANY(:tables)"
)
_SQL_INDEXES = text(
    "SELECT indexname::text, tablename::text, indexdef::text FROM pg_indexes "
    "WHERE schemaname = 'public' AND tablename::text = ANY(:tables)"
)


class UnsafeTargetError(RuntimeError):
    """Raised when a database target does not satisfy the safety rules."""


# ---------------------------------------------------------------------------
# Pure safety rules (no connection)
# ---------------------------------------------------------------------------


def masked(url: URL) -> str:
    """Printable description without credentials."""
    return f"{url.drivername}://***@{url.host}:{url.port}/{url.database}"


def scrub(message: str, url: URL | None = None) -> str:
    """Remove the user name, password and any rendered URL from an error message."""
    cleaned = message
    if url is not None:
        rendered = url.render_as_string(hide_password=False)
        cleaned = cleaned.replace(rendered, masked(url))
        for secret in (url.password, url.username):
            if secret:
                cleaned = cleaned.replace(str(secret), "***")
    return cleaned


def apply_safe_timeouts() -> None:
    """Set libpq connection/lock/statement timeouts for this process only."""
    os.environ["PGCONNECT_TIMEOUT"] = "5"
    os.environ["PGOPTIONS"] = (
        "-c lock_timeout=5000 -c statement_timeout=120000 -c idle_in_transaction_session_timeout=120000"
    )


def assert_local_postgres(url: URL) -> None:
    """Hard gate: PostgreSQL driver, local host, non-shared database name."""
    if not url.drivername.startswith("postgresql"):
        raise UnsafeTargetError("target is not PostgreSQL")
    if (url.host or "") not in LOCAL_HOSTS:
        raise UnsafeTargetError("target host is not local")
    database = (url.database or "").lower()
    if not database:
        raise UnsafeTargetError("target has no database name")
    if any(word in database for word in FORBIDDEN_WORDS):
        raise UnsafeTargetError("database name looks like a shared or production database")


def scratch_database_name(real_database: str) -> str:
    """Derive the single allowed scratch database name from the real database name."""
    name = f"{real_database.lower()}{SCRATCH_SUFFIX}"
    if not _NAME_RE.fullmatch(name):
        raise UnsafeTargetError("scratch database name is not a valid safe identifier")
    return name


def assert_scratch(url: URL, real_database: str | None = None) -> None:
    """Hard gate for any write: the target must be the local scratch database."""
    assert_local_postgres(url)
    database = url.database or ""
    if not database.endswith(SCRATCH_SUFFIX) or not _NAME_RE.fullmatch(database):
        raise UnsafeTargetError("database is not a scratch database")
    if real_database is not None and database == real_database.lower():
        raise UnsafeTargetError("scratch database equals the real database")


def classify_state(tables: set[str], versions: list[str], known_revisions: set[str]) -> str:
    """Classify the migration state of a database from its table list and alembic_version rows."""
    other_tables = tables - {"alembic_version"}
    if "alembic_version" not in tables or not versions:
        return STATE_EMPTY if not other_tables else STATE_DRIFT
    if len(versions) != 1 or versions[0] not in known_revisions:
        return STATE_DRIFT
    return STATE_AT_REVISION


def has_data(row_counts: dict[str, int]) -> bool:
    """True when any table other than alembic_version holds rows."""
    return any(count > 0 for name, count in row_counts.items() if name != "alembic_version")


def overall_state(row_counts: dict[str, int], versions: list[str], known_revisions: set[str]) -> str:
    """DRIFT wins over HAS_DATA; HAS_DATA wins over EMPTY/AT_REVISION."""
    state = classify_state(set(row_counts), versions, known_revisions)
    if state == STATE_DRIFT:
        return STATE_DRIFT
    if has_data(row_counts):
        return STATE_HAS_DATA
    return state


def ensure_command_allowed(command: str, target: str) -> None:
    """Whitelist of Alembic commands per target (real upgrade only via apply_real)."""
    if target == "scratch" and command in SCRATCH_COMMANDS:
        return
    if target == "real" and command in REAL_COMMANDS:
        return
    raise UnsafeTargetError(f"alembic command '{command}' is not allowed on target '{target}'")


def point_settings_at(url: URL, target_settings: Any = None) -> None:
    """Point ``settings.DATABASE_URL`` at ``url`` and assert the database name really changed."""
    obj = settings if target_settings is None else target_settings
    object.__setattr__(obj, "DATABASE_URL", url.render_as_string(hide_password=False))
    if make_url(obj.DATABASE_URL).database != url.database:
        raise UnsafeTargetError("settings.DATABASE_URL does not point at the expected database")


# ---------------------------------------------------------------------------
# URLs and engines
# ---------------------------------------------------------------------------

_ORIGINAL_DATABASE_URL: str | None = None


def real_url() -> URL:
    """URL of the real database from settings (captured before any re-pointing)."""
    global _ORIGINAL_DATABASE_URL
    if _ORIGINAL_DATABASE_URL is None:
        _ORIGINAL_DATABASE_URL = settings.DATABASE_URL
    return make_url(_ORIGINAL_DATABASE_URL)


def scratch_url(real: URL | None = None) -> URL:
    """URL of the scratch database derived from the real URL, validated."""
    base = real_url() if real is None else real
    assert_local_postgres(base)
    url = base.set(database=scratch_database_name(base.database or ""))
    assert_scratch(url, base.database)
    return url


def _engine(url: URL) -> Engine:
    return create_engine(url, poolclass=NullPool)


def _admin_engine(url: URL) -> Engine:
    return create_engine(url.set(database="postgres"), isolation_level="AUTOCOMMIT", poolclass=NullPool)


def assert_primary(connection: Connection) -> None:
    """Refuse to work on a replica (hot standby)."""
    if connection.execute(_SQL_IN_RECOVERY).scalar():
        raise UnsafeTargetError("target server is a replica (pg_is_in_recovery() is true)")


def _assert_current_database(connection: Connection, expected: str) -> None:
    if connection.execute(_SQL_CURRENT_DATABASE).scalar() != expected:
        raise UnsafeTargetError("connected database does not match the expected name")


def scratch_exists(real: URL | None = None) -> bool:
    """Read-only check in pg_database whether the scratch database exists."""
    base = real_url() if real is None else real
    name = scratch_url(base).database
    engine = _admin_engine(base)
    try:
        with engine.connect() as connection:
            assert_primary(connection)
            return bool(connection.execute(_SQL_SCRATCH_EXISTS, {"name": name}).scalar())
    finally:
        engine.dispose()


def create_scratch_database(real: URL) -> URL:
    """Create a fresh scratch database; refuse if it already exists."""
    assert_local_postgres(real)
    name = scratch_database_name(real.database or "")
    target = real.set(database=name)
    assert_scratch(target, real.database)
    engine = _admin_engine(real)
    try:
        with engine.connect() as connection:
            assert_primary(connection)
            exists = connection.execute(_SQL_SCRATCH_EXISTS, {"name": name}).scalar()
            if exists:
                raise UnsafeTargetError("scratch database already exists; ask the owner to drop it")
            quoted = engine.dialect.identifier_preparer.quote_identifier(name)
            connection.execute(text(f"CREATE DATABASE {quoted}"))  # only f-string SQL allowed (D-05)
    finally:
        engine.dispose()
    return target


def drop_scratch_database(target: URL, real_database: str) -> None:
    """Drop the scratch database (validated before any engine is created)."""
    assert_scratch(target, real_database)
    engine = _admin_engine(target)
    try:
        with engine.connect() as connection:
            assert_primary(connection)
            quoted = engine.dialect.identifier_preparer.quote_identifier(target.database or "")
            connection.execute(text(f"DROP DATABASE IF EXISTS {quoted}"))  # only f-string SQL allowed (D-05)
    finally:
        engine.dispose()


# ---------------------------------------------------------------------------
# Read-only inventory and catalog
# ---------------------------------------------------------------------------


@dataclass
class Inventory:
    """Read-only snapshot of a database."""

    database: str
    server_version: str
    in_recovery: bool
    row_counts: dict[str, int] = field(default_factory=dict)
    versions: list[str] = field(default_factory=list)

    @property
    def tables(self) -> set[str]:
        return set(self.row_counts)


def _capped_count(connection: Connection, table_name: str) -> int:
    limited = select(literal(1)).select_from(table(table_name)).limit(ROW_COUNT_CAP).subquery()
    return int(connection.execute(select(func.count()).select_from(limited)).scalar_one())


def inventory(url: URL) -> Inventory:
    """SELECT-only inventory: version, replica flag, tables with capped row counts, alembic_version."""
    assert_local_postgres(url)
    engine = _engine(url)
    try:
        with engine.connect() as connection:
            in_recovery = bool(connection.execute(_SQL_IN_RECOVERY).scalar())
            if in_recovery:
                raise UnsafeTargetError("target server is a replica (pg_is_in_recovery() is true)")
            _assert_current_database(connection, url.database or "")
            server_version = str(connection.execute(_SQL_SERVER_VERSION).scalar())
            table_names = [str(row[0]) for row in connection.execute(_SQL_TABLES)]
            row_counts = {name: _capped_count(connection, name) for name in table_names}
            versions: list[str] = []
            if "alembic_version" in row_counts:
                version_query = select(column("version_num")).select_from(table("alembic_version"))
                versions = [str(row[0]) for row in connection.execute(version_query)]
            connection.rollback()
    finally:
        engine.dispose()
    return Inventory(
        database=url.database or "",
        server_version=server_version,
        in_recovery=in_recovery,
        row_counts=row_counts,
        versions=versions,
    )


@dataclass
class CatalogSnapshot:
    """Constraint names, FK delete rules, column defaults and indexes of the new tables."""

    constraint_names: set[str] = field(default_factory=set)
    fk_delete_rules: dict[str, str] = field(default_factory=dict)
    column_defaults: dict[tuple[str, str], str | None] = field(default_factory=dict)
    indexes: dict[str, tuple[str, str]] = field(default_factory=dict)


def read_catalog(connection: Connection) -> CatalogSnapshot:
    """Read-only catalog queries for the three new tables."""
    tables = list(NEW_TABLES)
    snapshot = CatalogSnapshot()
    for row in connection.execute(_SQL_CONSTRAINTS, {"tables": tables}):
        snapshot.constraint_names.add(str(row[1]))
    for row in connection.execute(_SQL_FK_RULES, {"names": list(EXPECTED_FK_DELETE_RULES)}):
        snapshot.fk_delete_rules[str(row[0])] = str(row[1])
    for row in connection.execute(_SQL_COLUMN_DEFAULTS, {"tables": tables}):
        snapshot.column_defaults[(str(row[0]), str(row[1]))] = None if row[2] is None else str(row[2])
    for row in connection.execute(_SQL_INDEXES, {"tables": tables}):
        snapshot.indexes[str(row[0])] = (str(row[1]), str(row[2]))
    return snapshot


def compare_catalog(snapshot: CatalogSnapshot) -> list[str]:
    """Return human-readable mismatches between the catalog and the expected schema."""
    problems: list[str] = []
    missing = EXPECTED_CONSTRAINT_NAMES - snapshot.constraint_names
    extra = snapshot.constraint_names - EXPECTED_CONSTRAINT_NAMES
    if missing:
        problems.append(f"missing constraints: {sorted(missing)}")
    if extra:
        problems.append(f"unexpected constraints: {sorted(extra)}")
    for name, rule in EXPECTED_FK_DELETE_RULES.items():
        actual = snapshot.fk_delete_rules.get(name)
        if actual != rule:
            problems.append(f"{name}: delete_rule {actual!r}, expected {rule!r}")
    defaults = snapshot.column_defaults
    theme_default = defaults.get(("topics", "theme_key"))
    if theme_default is None or not theme_default.startswith("'default'"):
        problems.append(f"topics.theme_key default {theme_default!r}, expected 'default'")
    if defaults.get(("user_topic_progress", "stars")) != "0":
        problems.append(f"user_topic_progress.stars default {defaults.get(('user_topic_progress', 'stars'))!r}")
    if defaults.get(("user_topic_progress", "updated_at")) != "now()":
        problems.append(
            f"user_topic_progress.updated_at default {defaults.get(('user_topic_progress', 'updated_at'))!r}"
        )
    if ("user_topic_progress", "status") not in defaults:
        problems.append("user_topic_progress.status column missing")
    elif defaults[("user_topic_progress", "status")] is not None:
        problems.append("user_topic_progress.status must not have a default")
    index = snapshot.indexes.get(EXPECTED_INDEX_NAME)
    if index is None:
        problems.append(f"index {EXPECTED_INDEX_NAME} missing")
    elif index[0] != "user_topic_progress" or "(topic_id)" not in index[1]:
        problems.append(f"index {EXPECTED_INDEX_NAME} has unexpected definition")
    return problems


def verify_catalog(url: URL) -> list[str]:
    """Read-only comparison of the catalog with the expected schema (empty list = match)."""
    assert_local_postgres(url)
    engine = _engine(url)
    try:
        with engine.connect() as connection:
            assert_primary(connection)
            _assert_current_database(connection, url.database or "")
            snapshot = read_catalog(connection)
            connection.rollback()
    finally:
        engine.dispose()
    return compare_catalog(snapshot)


# ---------------------------------------------------------------------------
# Alembic
# ---------------------------------------------------------------------------


def alembic_config() -> Config:
    cfg = Config(str(ALEMBIC_INI_PATH))
    cfg.set_main_option("script_location", str(ALEMBIC_SCRIPT_DIR))
    return cfg


def known_revisions() -> set[str]:
    script = ScriptDirectory.from_config(alembic_config())
    return {rev.revision for rev in script.walk_revisions()}


def alembic_head() -> str:
    heads = ScriptDirectory.from_config(alembic_config()).get_heads()
    if len(heads) != 1:
        raise UnsafeTargetError("alembic must have exactly one head")
    return heads[0]


def _run_alembic_unchecked(command_name: str, revision: str | None, target_url: URL) -> str:
    """Run one Alembic command in-process against ``target_url``; settings are restored afterwards."""
    apply_safe_timeouts()
    original = settings.DATABASE_URL
    real_url()  # capture the real URL before re-pointing
    engine = _engine(target_url)
    try:
        with engine.connect() as connection:
            assert_primary(connection)
            _assert_current_database(connection, target_url.database or "")
    finally:
        engine.dispose()
    cfg = alembic_config()
    try:
        point_settings_at(target_url)
        if make_url(settings.DATABASE_URL).database != target_url.database:
            raise UnsafeTargetError("refusing to run alembic: settings point at another database")
        if command_name == "upgrade":
            alembic_command.upgrade(cfg, revision or "head")
            return "upgrade done"
        if command_name == "downgrade":
            if not revision:
                raise UnsafeTargetError("downgrade requires an explicit revision")
            alembic_command.downgrade(cfg, revision)
            return "downgrade done"
        if command_name == "current":
            alembic_command.current(cfg)
            return "current done"
        if command_name == "check":
            try:
                alembic_command.check(cfg)
            except CommandError as exc:
                return f"check: {exc}"
            return "check: no new upgrade operations detected"
        raise UnsafeTargetError(f"unknown alembic command '{command_name}'")
    finally:
        object.__setattr__(settings, "DATABASE_URL", original)


def run_alembic(command_name: str, revision: str | None, target: str) -> str:
    """Run a whitelisted Alembic command on the scratch or real database."""
    ensure_command_allowed(command_name, target)
    real = real_url()
    assert_local_postgres(real)
    target_url = scratch_url(real) if target == "scratch" else real
    return _run_alembic_unchecked(command_name, revision, target_url)


def check_mentions_new_tables(check_output: str) -> bool:
    return any(name in check_output for name in NEW_TABLES)


def apply_real(
    confirm_database: str,
    allow_existing_data: bool = False,
    *,
    real: URL | None = None,
    inventory_fn: Callable[[URL], Inventory] | None = None,
    revisions: set[str] | None = None,
    upgrade_fn: Callable[[URL], object] | None = None,
) -> str:
    """The only path that runs ``alembic upgrade head`` on the real database. Returns the gate state."""
    target = real_url() if real is None else real
    assert_local_postgres(target)
    if confirm_database != target.database:
        raise UnsafeTargetError("--confirm-database does not match the configured database name")
    inv = (inventory if inventory_fn is None else inventory_fn)(target)
    if inv.in_recovery:
        raise UnsafeTargetError("target server is a replica")
    state = overall_state(inv.row_counts, inv.versions, known_revisions() if revisions is None else revisions)
    if state == STATE_DRIFT:
        raise UnsafeTargetError("real database is in DRIFT state; refusing to apply migrations")
    if state == STATE_HAS_DATA and not allow_existing_data:
        raise UnsafeTargetError("real database already has data; confirm a backup and use --allow-existing-data")
    runner = upgrade_fn if upgrade_fn is not None else (
        lambda url: _run_alembic_unchecked("upgrade", "head", url)
    )
    runner(target)
    return state


# ---------------------------------------------------------------------------
# Scratch-only probes for the downgrade guard
# ---------------------------------------------------------------------------


def probe_insert(target: URL, real_database: str) -> None:
    """Commit one fixed Level and one fixed Topic in the scratch database."""
    from app.models.level import Level
    from app.models.topic import Topic

    assert_scratch(target, real_database)
    engine = _engine(target)
    try:
        with engine.connect() as connection:
            assert_primary(connection)
            _assert_current_database(connection, target.database or "")
            connection.rollback()  # end the autobegun read transaction before writing
            with Session(bind=connection) as session:
                session.add(Level(id=PROBE_LEVEL_ID, name="Probe Level", order_index=9001))
                session.flush()
                session.add(Topic(id=PROBE_TOPIC_ID, level_id=PROBE_LEVEL_ID, name="Probe Topic", order_index=1))
                session.commit()
            connection.commit()
    finally:
        engine.dispose()


def probe_clear(target: URL, real_database: str) -> None:
    """Delete exactly the two probe rows (by fixed primary key) from the scratch database."""
    from app.models.level import Level
    from app.models.topic import Topic

    assert_scratch(target, real_database)
    engine = _engine(target)
    try:
        with engine.connect() as connection:
            assert_primary(connection)
            _assert_current_database(connection, target.database or "")
            connection.rollback()  # end the autobegun read transaction before writing
            with connection.begin():
                connection.execute(delete(Topic).where(Topic.id == PROBE_TOPIC_ID))
                connection.execute(delete(Level).where(Level.id == PROBE_LEVEL_ID))
    finally:
        engine.dispose()


# ---------------------------------------------------------------------------
# Formatting (credentials never included)
# ---------------------------------------------------------------------------


def gate_results(url: URL) -> list[tuple[str, bool]]:
    """Static hard gates (no connection)."""
    database = (url.database or "").lower()
    return [
        ("driver is postgresql*", url.drivername.startswith("postgresql")),
        ("host is local", (url.host or "") in LOCAL_HOSTS),
        ("database name present", bool(database)),
        ("database name not prod/live/staging", not any(w in database for w in FORBIDDEN_WORDS)),
    ]


def format_describe(url: URL, gates: Sequence[tuple[str, bool]], scratch_name: str) -> list[str]:
    lines = [f"target: {masked(url)}"]
    lines += [f"gate {name}: {'OK' if ok else 'FAIL'}" for name, ok in gates]
    lines.append(f"planned scratch database: {scratch_name}")
    return lines


def format_inventory(url: URL, inv: Inventory, state: str) -> list[str]:
    lines = [
        f"target: {masked(url)}",
        f"server_version: {inv.server_version}",
        f"current_database: {inv.database}",
        f"in_recovery (replica): {inv.in_recovery}",
        f"tables: {len(inv.row_counts)}",
    ]
    for name in sorted(inv.row_counts):
        count = inv.row_counts[name]
        shown = f"{ROW_COUNT_CAP - 1}+" if count >= ROW_COUNT_CAP else str(count)
        lines.append(f"  {name}: {shown} rows")
    lines.append(f"alembic_version: {inv.versions}")
    lines.append(f"state: {state}")
    return lines


def _emit(lines: Sequence[str] | str) -> None:
    for line in [lines] if isinstance(lines, str) else lines:
        sys.stdout.write(f"{line}\n")
    sys.stdout.flush()


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="python -m tests.db_guard")
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("describe")
    sub.add_parser("inventory")
    sub.add_parser("create-scratch")
    sub.add_parser("drop-scratch")
    alembic_parser = sub.add_parser("alembic")
    alembic_parser.add_argument("alembic_cmd", choices=sorted(SCRATCH_COMMANDS))
    alembic_parser.add_argument("revision", nargs="?", default=None)
    alembic_parser.add_argument("--target", choices=("scratch", "real"), required=True)
    verify_parser = sub.add_parser("verify-catalog")
    verify_parser.add_argument("--target", choices=("scratch", "real"), required=True)
    sub.add_parser("probe-insert")
    sub.add_parser("probe-clear")
    apply_parser = sub.add_parser("apply-real")
    apply_parser.add_argument("--confirm-database", required=True)
    apply_parser.add_argument("--allow-existing-data", action="store_true")
    return parser


def _dispatch(args: argparse.Namespace) -> int:
    real = real_url()
    if args.cmd == "describe":
        gates = gate_results(real)
        _emit(format_describe(real, gates, scratch_database_name(real.database or "x")))
        if not all(ok for _, ok in gates):
            _emit("RESULT: unsafe target, stopping")
            return 1
        _emit(f"scratch database exists: {scratch_exists(real)}")
        _emit("gate not a replica: OK")
        return 0
    assert_local_postgres(real)
    if args.cmd == "inventory":
        inv = inventory(real)
        _emit(format_inventory(real, inv, overall_state(inv.row_counts, inv.versions, known_revisions())))
        _emit(f"scratch database exists: {scratch_exists(real)}")
        return 0
    if args.cmd == "create-scratch":
        created = create_scratch_database(real)
        _emit(f"created scratch database: {masked(created)}")
        return 0
    if args.cmd == "drop-scratch":
        drop_scratch_database(scratch_url(real), real.database or "")
        _emit(f"dropped scratch database: {scratch_url(real).database}")
        _emit(f"scratch database exists: {scratch_exists(real)}")
        return 0
    if args.cmd == "alembic":
        target_url = scratch_url(real) if args.target == "scratch" else real
        _emit(f"alembic {args.alembic_cmd} {args.revision or ''} on {masked(target_url)}")
        result = run_alembic(args.alembic_cmd, args.revision, args.target)
        _emit(result)
        if args.alembic_cmd == "check":
            _emit(f"check mentions new tables: {check_mentions_new_tables(result)}")
        return 0
    if args.cmd == "verify-catalog":
        target_url = scratch_url(real) if args.target == "scratch" else real
        problems = verify_catalog(target_url)
        _emit(f"verify-catalog on {masked(target_url)}: {'OK' if not problems else 'MISMATCH'}")
        _emit([f"  - {p}" for p in problems])
        return 0 if not problems else 1
    if args.cmd == "probe-insert":
        probe_insert(scratch_url(real), real.database or "")
        _emit("probe rows inserted into scratch (1 level, 1 topic)")
        return 0
    if args.cmd == "probe-clear":
        probe_clear(scratch_url(real), real.database or "")
        _emit("probe rows deleted from scratch")
        return 0
    if args.cmd == "apply-real":
        state = apply_real(args.confirm_database, args.allow_existing_data)
        _emit(f"apply-real on {masked(real)}: state before = {state}; upgrade head done")
        return 0
    raise UnsafeTargetError("unknown command")


def main(argv: Sequence[str] | None = None) -> int:
    apply_safe_timeouts()
    args = _build_parser().parse_args(argv)
    try:
        return _dispatch(args)
    except UnsafeTargetError as exc:
        _emit(f"REFUSED: {scrub(str(exc), real_url())}")
        return 1
    except (SQLAlchemyError, CommandError, RuntimeError) as exc:
        _emit(f"ERROR ({type(exc).__name__}): {scrub(str(exc), real_url())}")
        return 2


if __name__ == "__main__":
    sys.exit(main())
