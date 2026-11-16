"""create user vocabulary progress storage

Revision ID: f2b8d6a30c95
Revises: e4a7c9b20f16
Create Date: 2026-11-14
"""
from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "f2b8d6a30c95"
down_revision: str | None = "e4a7c9b20f16"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    """Add storage only; do not seed or backfill user progress."""
    op.create_table(
        "user_vocabulary_progress",
        sa.Column("user_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("vocabulary_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("status", sa.String(length=20), server_default=sa.text("'new'"), nullable=False),
        sa.Column("is_in_flashcard", sa.Boolean(), server_default=sa.text("false"), nullable=False),
        sa.Column("repetition_count", sa.Integer(), server_default=sa.text("0"), nullable=False),
        sa.Column("interval", sa.Integer(), server_default=sa.text("0"), nullable=False),
        sa.Column("ease_factor", sa.Numeric(), server_default=sa.text("2.5"), nullable=False),
        sa.Column("last_reviewed", sa.DateTime(timezone=True), nullable=True),
        sa.Column("next_review", sa.DateTime(timezone=True), nullable=True),
        sa.Column("added_to_flashcard_at", sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint("user_id", "vocabulary_id", name=op.f("pk_user_vocabulary_progress")),
        sa.ForeignKeyConstraint(
            ["user_id"], ["users.id"],
            name=op.f("fk_user_vocabulary_progress_user_id_users"), ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["vocabulary_id"], ["vocabularies.id"],
            name=op.f("fk_user_vocabulary_progress_vocabulary_id_vocabularies"), ondelete="RESTRICT",
        ),
        sa.CheckConstraint(
            "status IN ('new', 'skipped', 'in_flashcard', 'learning', 'review', 'mastered')",
            name=op.f("ck_user_vocabulary_progress_status_valid"),
        ),
        sa.CheckConstraint(
            "repetition_count >= 0", name=op.f("ck_user_vocabulary_progress_repetition_count_nonnegative"),
        ),
        sa.CheckConstraint("interval >= 0", name=op.f("ck_user_vocabulary_progress_interval_nonnegative")),
        sa.CheckConstraint(
            "ease_factor >= 1.3 AND ease_factor NOT IN "
            "('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric)",
            name=op.f("ck_user_vocabulary_progress_ease_factor_min_finite"),
        ),
        sa.CheckConstraint(
            "(status IN ('new', 'skipped') AND is_in_flashcard = false) OR "
            "(status IN ('in_flashcard', 'learning', 'review', 'mastered') AND is_in_flashcard = true)",
            name=op.f("ck_user_vocabulary_progress_status_flashcard_consistent"),
        ),
    )
    op.create_index(
        op.f("ix_user_vocabulary_progress_vocabulary_id"), "user_vocabulary_progress", ["vocabulary_id"],
    )


def downgrade() -> None:
    """Refuse loss of progress before removing any schema objects."""
    if (
        not op.get_context().as_sql
        and op.get_bind().execute(sa.text("SELECT 1 FROM user_vocabulary_progress LIMIT 1")).first() is not None
    ):
        raise RuntimeError(
            "Refusing to drop non-empty table user_vocabulary_progress; delete its rows manually first."
        )
    op.drop_index(op.f("ix_user_vocabulary_progress_vocabulary_id"), table_name="user_vocabulary_progress")
    op.drop_table("user_vocabulary_progress")
