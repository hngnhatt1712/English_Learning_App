"""create vocabulary content with consistent Topic/Level references

Revision ID: d9f3b7a12e64
Revises: c6e8a4b21d90
Create Date: 2026-11-14

Difficulty has no specified scale; it is an optional internal text label.
"""
from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str = "d9f3b7a12e64"
down_revision: str | None = "c6e8a4b21d90"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    """Add the referenced key before the composite FK; preserve existing content."""
    op.create_unique_constraint(op.f("uq_topics_id_level_id"), "topics", ["id", "level_id"])
    op.create_table(
        "vocabularies",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("word", sa.Text(), nullable=False),
        sa.Column("meaning", sa.Text(), nullable=False),
        sa.Column("part_of_speech", sa.String(length=20), nullable=False),
        sa.Column("phonetic", sa.Text(), nullable=True),
        sa.Column("example", sa.Text(), nullable=True),
        sa.Column("image_url", sa.Text(), nullable=True),
        sa.Column("audio_url", sa.Text(), nullable=True),
        sa.Column("difficulty", sa.Text(), nullable=True),
        sa.Column("topic_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("level_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_vocabularies")),
        sa.CheckConstraint(
            "part_of_speech IN ('noun', 'verb', 'adjective', 'adverb', 'pronoun', "
            "'preposition', 'conjunction', 'interjection', 'phrase', 'other')",
            name=op.f("ck_vocabularies_part_of_speech_valid"),
        ),
        sa.ForeignKeyConstraint(
            ["topic_id", "level_id"], ["topics.id", "topics.level_id"],
            name=op.f("fk_vocabularies_topic_id_level_id_topics"), ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["level_id"], ["levels.id"],
            name=op.f("fk_vocabularies_level_id_levels"), ondelete="RESTRICT",
        ),
    )
    # The composite index covers topic_id lookups; no redundant topic-only index.
    op.create_index(
        op.f("ix_vocabularies_topic_id_level_id"), "vocabularies", ["topic_id", "level_id"],
    )
    op.create_index(op.f("ix_vocabularies_level_id"), "vocabularies", ["level_id"])


def downgrade() -> None:
    """Keep the content-table convention: refuse a downgrade that would lose rows."""
    if (
        not op.get_context().as_sql
        and op.get_bind().execute(sa.text("SELECT 1 FROM vocabularies LIMIT 1")).first() is not None
    ):
        raise RuntimeError(
            "Refusing to drop non-empty table vocabularies; delete its rows manually first."
        )
    op.drop_index(op.f("ix_vocabularies_level_id"), table_name="vocabularies")
    op.drop_index(op.f("ix_vocabularies_topic_id_level_id"), table_name="vocabularies")
    op.drop_table("vocabularies")
    op.drop_constraint(op.f("uq_topics_id_level_id"), "topics", type_="unique")
