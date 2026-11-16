"""create activities and shared questions

Revision ID: c6e8a4b21d90
Revises: 7f4a19c8e201
Create Date: 2026-11-14

grammar_lesson_id deliberately has no FK until grammar_lessons exists.
"""
from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

revision: str = "c6e8a4b21d90"
down_revision: str | None = "7f4a19c8e201"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

_GUARD_STATEMENTS = (
    ("questions", sa.text("SELECT 1 FROM questions LIMIT 1")),
    ("activities", sa.text("SELECT 1 FROM activities LIMIT 1")),
)


def upgrade() -> None:
    """Add content only; existing tables and data are unchanged."""
    op.create_table(
        "activities",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("lesson_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("round_number", sa.Integer(), nullable=False),
        sa.Column("round_type", sa.String(length=20), nullable=False),
        sa.Column("title", sa.String(length=100), nullable=False),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_activities")),
        sa.ForeignKeyConstraint(
            ["lesson_id"], ["lessons.id"],
            name=op.f("fk_activities_lesson_id_lessons"), ondelete="RESTRICT",
        ),
        sa.UniqueConstraint(
            "lesson_id", "round_number", name=op.f("uq_activities_lesson_id_round_number"),
        ),
        sa.CheckConstraint(
            "round_number BETWEEN 1 AND 5", name=op.f("ck_activities_round_number_range"),
        ),
        sa.CheckConstraint(
            "round_type IN ('learn', 'recognition', 'matching', 'application', 'review')",
            name=op.f("ck_activities_round_type_valid"),
        ),
        sa.CheckConstraint(
            "(round_number = 1 AND round_type = 'learn') OR "
            "(round_number = 2 AND round_type = 'recognition') OR "
            "(round_number = 3 AND round_type = 'matching') OR "
            "(round_number = 4 AND round_type = 'application') OR "
            "(round_number = 5 AND round_type = 'review')",
            name=op.f("ck_activities_round_number_type_match"),
        ),
    )
    # UNIQUE(lesson_id, round_number) already indexes lookups by lesson_id.
    op.create_table(
        "questions",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("type", sa.String(length=20), nullable=False),
        sa.Column("prompt", sa.Text(), nullable=False),
        sa.Column("explanation", sa.Text(), nullable=True),
        sa.Column("image_url", sa.Text(), nullable=True),
        sa.Column("audio_url", sa.Text(), nullable=True),
        sa.Column("payload", postgresql.JSONB(none_as_null=True), nullable=False),
        sa.Column("source", sa.String(length=20), nullable=False),
        sa.Column("purpose", sa.String(length=20), nullable=True),
        sa.Column("activity_id", sa.Uuid(as_uuid=True), nullable=True),
        sa.Column("topic_id", sa.Uuid(as_uuid=True), nullable=True),
        sa.Column("grammar_lesson_id", sa.Uuid(as_uuid=True), nullable=True),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_questions")),
        sa.ForeignKeyConstraint(
            ["activity_id"], ["activities.id"],
            name=op.f("fk_questions_activity_id_activities"), ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["topic_id"], ["topics.id"],
            name=op.f("fk_questions_topic_id_topics"), ondelete="RESTRICT",
        ),
        sa.CheckConstraint(
            "type IN ('learn_card', 'multiple_choice', 'picture_choice', 'matching', "
            "'fill_blank', 'translate', 'sentence_order', 'sentence_correction', "
            "'sentence_completion')",
            name=op.f("ck_questions_type_valid"),
        ),
        sa.CheckConstraint(
            "source IN ('round', 'vocabulary_quiz', 'grammar')",
            name=op.f("ck_questions_source_valid"),
        ),
        sa.CheckConstraint(
            "purpose IS NULL OR purpose IN ('practice', 'quiz')",
            name=op.f("ck_questions_purpose_valid"),
        ),
        sa.CheckConstraint(
            "(source = 'round' AND activity_id IS NOT NULL AND topic_id IS NULL "
            "AND grammar_lesson_id IS NULL AND purpose IS NULL) OR "
            "(source = 'vocabulary_quiz' AND topic_id IS NOT NULL AND activity_id IS NULL "
            "AND grammar_lesson_id IS NULL AND purpose IS NULL) OR "
            "(source = 'grammar' AND grammar_lesson_id IS NOT NULL AND activity_id IS NULL "
            "AND topic_id IS NULL AND purpose IS NOT NULL)",
            name=op.f("ck_questions_source_owner_match"),
        ),
        sa.CheckConstraint(
            "jsonb_typeof(payload) = 'object'", name=op.f("ck_questions_payload_object"),
        ),
    )
    op.create_index(op.f("ix_questions_activity_id"), "questions", ["activity_id"])
    op.create_index(op.f("ix_questions_topic_id"), "questions", ["topic_id"])
    op.create_index(op.f("ix_questions_grammar_lesson_id"), "questions", ["grammar_lesson_id"])


def downgrade() -> None:
    """Refuse content loss, then remove the two new tables in dependency order."""
    if not op.get_context().as_sql:
        for table_name, statement in _GUARD_STATEMENTS:
            if op.get_bind().execute(statement).first() is not None:
                raise RuntimeError(
                    f"Refusing to drop non-empty table {table_name}; delete its rows manually first."
                )
    op.drop_index(op.f("ix_questions_grammar_lesson_id"), table_name="questions")
    op.drop_index(op.f("ix_questions_topic_id"), table_name="questions")
    op.drop_index(op.f("ix_questions_activity_id"), table_name="questions")
    op.drop_table("questions")
    op.drop_table("activities")
