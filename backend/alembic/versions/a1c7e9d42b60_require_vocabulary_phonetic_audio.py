"""Require real Vocabulary phonetic/audio content (contract D23).

Revision ID: a1c7e9d42b60
Revises: f2b8d6a30c95
Create Date: 2026-11-17

No backfill is safe: missing pronunciation/audio must be supplied by content
owners. Offline SQL retains the ALTERs; the data preflight runs online only.
"""
from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "a1c7e9d42b60"
down_revision: str | None = "f2b8d6a30c95"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    """Check both columns before either ALTER; Alembic owns the transaction."""
    if op.get_context().as_sql:
        op.execute(
            "-- Online data preflight required before applying offline SQL: "
            "check vocabularies for NULL phonetic or audio_url; supply real content first."
        )
    elif op.get_bind().execute(sa.text(
        "SELECT 1 FROM vocabularies WHERE phonetic IS NULL OR audio_url IS NULL LIMIT 1"
    )).first() is not None:
        raise RuntimeError(
            "Cannot require Vocabulary phonetic/audio_url: NULL content exists. "
            "Supply real phonetic and audio media paths before retrying; no rows were changed."
        )
    op.alter_column("vocabularies", "phonetic", existing_type=sa.Text(), nullable=False)
    op.alter_column("vocabularies", "audio_url", existing_type=sa.Text(), nullable=False)


def downgrade() -> None:
    """Relax nullability without changing content, keys or progress."""
    op.alter_column("vocabularies", "audio_url", existing_type=sa.Text(), nullable=True)
    op.alter_column("vocabularies", "phonetic", existing_type=sa.Text(), nullable=True)
