"""create user round progress storage

Revision ID: e4a7c9b20f16
Revises: d9f3b7a12e64
Create Date: 2026-11-14
"""
from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "e4a7c9b20f16"
down_revision: str | None = "d9f3b7a12e64"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    """Add storage without creating progress rows or modifying existing tables."""
    op.create_table(
        "user_round_progress",
        sa.Column("user_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("round_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("status", sa.String(length=20), server_default=sa.text("'locked'"), nullable=False),
        sa.Column("best_score", sa.Numeric(precision=4, scale=1), nullable=True),
        sa.Column("stars", sa.Integer(), server_default=sa.text("0"), nullable=False),
        sa.Column("attempts", sa.Integer(), server_default=sa.text("0"), nullable=False),
        sa.Column("completed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.PrimaryKeyConstraint("user_id", "round_id", name=op.f("pk_user_round_progress")),
        sa.ForeignKeyConstraint(
            ["user_id"], ["users.id"],
            name=op.f("fk_user_round_progress_user_id_users"), ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["round_id"], ["activities.id"],
            name=op.f("fk_user_round_progress_round_id_activities"), ondelete="RESTRICT",
        ),
        sa.CheckConstraint(
            "status IN ('locked', 'unlocked', 'completed')", name=op.f("ck_user_round_progress_status_valid"),
        ),
        sa.CheckConstraint("stars >= 0 AND stars <= 3", name=op.f("ck_user_round_progress_stars_range")),
        sa.CheckConstraint("attempts >= 0", name=op.f("ck_user_round_progress_attempts_nonnegative")),
        sa.CheckConstraint(
            "best_score IS NULL OR (best_score >= 0 AND best_score <= 100)",
            name=op.f("ck_user_round_progress_best_score_range"),
        ),
    )
    op.create_index(op.f("ix_user_round_progress_round_id"), "user_round_progress", ["round_id"])


def downgrade() -> None:
    """Refuse loss of progress before removing any schema objects."""
    if (
        not op.get_context().as_sql
        and op.get_bind().execute(sa.text("SELECT 1 FROM user_round_progress LIMIT 1")).first() is not None
    ):
        raise RuntimeError(
            "Refusing to drop non-empty table user_round_progress; delete its rows manually first."
        )
    op.drop_index(op.f("ix_user_round_progress_round_id"), table_name="user_round_progress")
    op.drop_table("user_round_progress")
