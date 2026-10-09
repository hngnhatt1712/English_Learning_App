"""create levels table

Revision ID: 92c5a853ee56
Revises: 26de1d550d69
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "92c5a853ee56"
down_revision: Union[str, Sequence[str], None] = "26de1d550d69"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Create the levels table."""
    op.create_table(
        "levels",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("name", sa.String(length=100), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("order_index", sa.Integer(), nullable=False),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_levels")),
        sa.UniqueConstraint("order_index", name=op.f("uq_levels_order_index")),
    )


def downgrade() -> None:
    """Drop the levels table."""
    op.drop_table("levels")
