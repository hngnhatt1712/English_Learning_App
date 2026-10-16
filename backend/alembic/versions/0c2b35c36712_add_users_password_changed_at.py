"""add users.password_changed_at

Revision ID: 0c2b35c36712
Revises: aab29adbb80e
Create Date: 2026-10-12 21:26:37.359334

"""
from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

# revision identifiers, used by Alembic.
revision: str = '0c2b35c36712'
down_revision: str | Sequence[str] | None = 'aab29adbb80e'
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    """Add password_changed_at column to users table."""
    op.add_column(
        "users",
        sa.Column("password_changed_at", sa.DateTime(timezone=True), nullable=True),
    )


def downgrade() -> None:
    """Drop password_changed_at column from users table."""
    op.drop_column("users", "password_changed_at")

