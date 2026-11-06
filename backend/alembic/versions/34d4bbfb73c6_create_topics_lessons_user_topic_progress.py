"""create topics, lessons, user_topic_progress

Revision ID: 34d4bbfb73c6
Revises: 3d8b5c4e9123
Create Date: 2026-11-05 16:52:31.460476

"""
from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "34d4bbfb73c6"
down_revision: str | None = "3d8b5c4e9123"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

# Fixed statements (no string building) used by the downgrade guard.
_GUARD_STATEMENTS = (
    ("user_topic_progress", sa.text("SELECT 1 FROM user_topic_progress LIMIT 1")),
    ("lessons", sa.text("SELECT 1 FROM lessons LIMIT 1")),
    ("topics", sa.text("SELECT 1 FROM topics LIMIT 1")),
)


def upgrade() -> None:
    """Create topics, lessons, and user_topic_progress tables."""
    op.create_table(
        "topics",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("level_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("name", sa.String(length=100), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("order_index", sa.Integer(), nullable=False),
        sa.Column(
            "theme_key",
            sa.String(length=50),
            server_default=sa.text("'default'"),
            nullable=False,
        ),
        sa.CheckConstraint("order_index >= 1", name=op.f("ck_topics_order_index_positive")),
        sa.ForeignKeyConstraint(
            ["level_id"],
            ["levels.id"],
            name=op.f("fk_topics_level_id_levels"),
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_topics")),
        sa.UniqueConstraint(
            "level_id",
            "order_index",
            name=op.f("uq_topics_level_id_order_index"),
        ),
    )

    op.create_table(
        "lessons",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("topic_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("title", sa.String(length=100), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("order_index", sa.Integer(), nullable=False),
        sa.CheckConstraint("order_index >= 1", name=op.f("ck_lessons_order_index_positive")),
        sa.ForeignKeyConstraint(
            ["topic_id"],
            ["topics.id"],
            name=op.f("fk_lessons_topic_id_topics"),
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_lessons")),
        sa.UniqueConstraint(
            "topic_id",
            "order_index",
            name=op.f("uq_lessons_topic_id_order_index"),
        ),
    )

    op.create_table(
        "user_topic_progress",
        sa.Column("user_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("topic_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("status", sa.String(length=20), nullable=False),
        sa.Column(
            "stars",
            sa.Integer(),
            server_default=sa.text("0"),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.CheckConstraint(
            "status IN ('locked', 'unlocked', 'completed')",
            name=op.f("ck_user_topic_progress_status_valid"),
        ),
        sa.CheckConstraint(
            "stars >= 0 AND stars <= 15",
            name=op.f("ck_user_topic_progress_stars_range"),
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name=op.f("fk_user_topic_progress_user_id_users"),
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["topic_id"],
            ["topics.id"],
            name=op.f("fk_user_topic_progress_topic_id_topics"),
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("user_id", "topic_id", name=op.f("pk_user_topic_progress")),
    )
    op.create_index(
        op.f("ix_user_topic_progress_topic_id"),
        "user_topic_progress",
        ["topic_id"],
        unique=False,
    )


def _assert_tables_empty() -> None:
    """Refuse to drop tables that still hold rows (online mode only)."""
    if op.get_context().as_sql:
        return
    bind = op.get_bind()
    for table_name, statement in _GUARD_STATEMENTS:
        if bind.execute(statement).first() is not None:
            raise RuntimeError(
                f"Refusing to drop non-empty table {table_name}; delete its rows manually first."
            )


def downgrade() -> None:
    """Drop user_topic_progress, lessons, and topics tables in reverse dependency order."""
    _assert_tables_empty()
    op.drop_index(op.f("ix_user_topic_progress_topic_id"), table_name="user_topic_progress")
    op.drop_table("user_topic_progress")
    op.drop_table("lessons")
    op.drop_table("topics")
