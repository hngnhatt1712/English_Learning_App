"""add users selected level fk

Revision ID: aab29adbb80e
Revises: 92c5a853ee56
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "aab29adbb80e"
down_revision: Union[str, Sequence[str], None] = "92c5a853ee56"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Add FK users.selected_level_id -> levels.id (SET NULL on level delete)."""
    op.create_foreign_key(
        op.f("fk_users_selected_level_id_levels"),
        "users",
        "levels",
        ["selected_level_id"],
        ["id"],
        ondelete="SET NULL",
    )


def downgrade() -> None:
    """Drop the foreign key."""
    op.drop_constraint(op.f("fk_users_selected_level_id_levels"), "users", type_="foreignkey")
