"""create quiz history

Revision ID: b82c4d7a9e31
Revises: a1c7e9d42b60
Create Date: 2026-11-27
"""
from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import JSONB

from alembic import op

revision: str = "b82c4d7a9e31"
down_revision: str | None = "a1c7e9d42b60"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "quiz_attempts",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("user_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("kind", sa.String(20), nullable=False),
        sa.Column("topic_id", sa.Uuid(as_uuid=True), nullable=True),
        # Sprint 9 creates grammar_lessons before adding this FK and extending the owner check.
        sa.Column("grammar_lesson_id", sa.Uuid(as_uuid=True), nullable=True),
        sa.Column("total_questions", sa.Integer(), nullable=False),
        sa.Column("correct_count", sa.Integer(), nullable=False),
        sa.Column("score_percent", sa.Numeric(4, 1), nullable=False),
        sa.Column("completed_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_quiz_attempts")),
        sa.ForeignKeyConstraint(
            ["user_id"], ["users.id"], name=op.f("fk_quiz_attempts_user_id_users"), ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["topic_id"], ["topics.id"], name=op.f("fk_quiz_attempts_topic_id_topics"), ondelete="RESTRICT",
        ),
        sa.CheckConstraint(
            "kind = 'vocabulary_quiz' AND topic_id IS NOT NULL AND grammar_lesson_id IS NULL",
            name=op.f("ck_quiz_attempts_kind_owner_match"),
        ),
        sa.CheckConstraint("total_questions > 0", name=op.f("ck_quiz_attempts_total_questions_positive")),
        sa.CheckConstraint("correct_count BETWEEN 0 AND total_questions", name=op.f("ck_quiz_attempts_correct_count_range")),
        sa.CheckConstraint("score_percent BETWEEN 0 AND 100", name=op.f("ck_quiz_attempts_score_percent_range")),
    )
    op.create_index(op.f("ix_quiz_attempts_user_id_completed_at"), "quiz_attempts", ["user_id", "completed_at"])
    op.create_index(op.f("ix_quiz_attempts_topic_id"), "quiz_attempts", ["topic_id"])

    op.create_table(
        "quiz_answers",
        sa.Column("id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("attempt_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("question_id", sa.Uuid(as_uuid=True), nullable=False),
        sa.Column("is_correct", sa.Boolean(), nullable=False),
        sa.Column("answer", JSONB(none_as_null=True), nullable=False),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_quiz_answers")),
        sa.ForeignKeyConstraint(
            ["attempt_id"], ["quiz_attempts.id"], name=op.f("fk_quiz_answers_attempt_id_quiz_attempts"), ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["question_id"], ["questions.id"], name=op.f("fk_quiz_answers_question_id_questions"), ondelete="RESTRICT",
        ),
        sa.UniqueConstraint("attempt_id", "question_id", name=op.f("uq_quiz_answers_attempt_id_question_id")),
        sa.CheckConstraint("jsonb_typeof(answer) = 'object'", name=op.f("ck_quiz_answers_answer_object")),
    )
    op.create_index(op.f("ix_quiz_answers_question_id"), "quiz_answers", ["question_id"])


def downgrade() -> None:
    """Refuse to drop quiz history."""
    if not op.get_context().as_sql:
        connection = op.get_bind()
        connection.execute(sa.text("LOCK TABLE quiz_attempts, quiz_answers IN ACCESS EXCLUSIVE MODE"))
        if connection.execute(sa.text("SELECT 1 FROM quiz_attempts LIMIT 1")).first() is not None:
            raise RuntimeError("Refusing to drop non-empty quiz history; quiz_attempts contains data.")
    op.drop_index(op.f("ix_quiz_answers_question_id"), table_name="quiz_answers")
    op.drop_table("quiz_answers")
    op.drop_index(op.f("ix_quiz_attempts_topic_id"), table_name="quiz_attempts")
    op.drop_index(op.f("ix_quiz_attempts_user_id_completed_at"), table_name="quiz_attempts")
    op.drop_table("quiz_attempts")
