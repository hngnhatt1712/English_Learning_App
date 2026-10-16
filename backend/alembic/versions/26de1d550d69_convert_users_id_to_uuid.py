"""convert users id to uuid

Revision ID: 26de1d550d69
Revises: 01755eab3271
"""
from typing import Sequence, Union

from alembic import context, op
import sqlalchemy as sa

revision: str = "26de1d550d69"
down_revision: Union[str, Sequence[str], None] = "01755eab3271"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def _assert_users_empty() -> None:
    """Abort if 'users' has rows: this migration drops and recreates the table."""
    if context.is_offline_mode():
        return  # cannot query the database when rendering SQL with --sql
    count = op.get_bind().execute(sa.text("SELECT count(*) FROM users")).scalar_one()
    if count:
        raise RuntimeError("Refusing to recreate 'users': table is not empty.")


def upgrade() -> None:
    """Recreate 'users' with UUID id and UUID selected_level_id (table must be empty)."""
    _assert_users_empty()
    op.drop_table("users")
    op.create_table(
        "users",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("username", sa.String(length=50), nullable=False),
        sa.Column("email", sa.String(length=255), nullable=False),
        sa.Column("password_hash", sa.String(length=255), nullable=False),
        sa.Column("selected_level_id", sa.Uuid(), nullable=True),
        sa.Column("current_streak", sa.Integer(), server_default=sa.text("0"), nullable=False),
        sa.Column("last_learning_date", sa.Date(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_users")),
    )
    op.create_index(op.f("ix_users_email"), "users", ["email"], unique=True)
    op.create_index(op.f("ix_users_username"), "users", ["username"], unique=True)


def downgrade() -> None:
    """Restore the previous integer-keyed 'users' (table must be empty)."""
    _assert_users_empty()
    op.drop_table("users")
    op.create_table(
        "users",
        sa.Column("id", sa.Integer(), autoincrement=True, nullable=False),
        sa.Column("username", sa.String(length=50), nullable=False),
        sa.Column("email", sa.String(length=255), nullable=False),
        sa.Column("password_hash", sa.String(length=255), nullable=False),
        sa.Column("selected_level_id", sa.Integer(), nullable=True),
        sa.Column("current_streak", sa.Integer(), server_default=sa.text("0"), nullable=False),
        sa.Column("last_learning_date", sa.Date(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_users")),
    )
    op.create_index(op.f("ix_users_username"), "users", ["username"], unique=True)
    op.create_index(op.f("ix_users_email"), "users", ["email"], unique=True)
