"""create device_tokens table

Revision ID: 3d8b5c4e9123
Revises: 0c2b35c36712
Create Date: 2026-10-14 22:45:00.000000

"""
from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

# revision identifiers, used by Alembic.
revision: str = "3d8b5c4e9123"
down_revision: str | Sequence[str] | None = "0c2b35c36712"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    """Create device_tokens table with user_id and fcm_token indexes."""
    op.create_table(
        "device_tokens",
        sa.Column("id", sa.Uuid(as_uuid=True), primary_key=True, nullable=False),
        sa.Column(
            "user_id",
            sa.Uuid(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE", name="fk_device_tokens_user_id_users"),
            nullable=False,
        ),
        sa.Column("fcm_token", sa.String(length=4096), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )
    op.create_index("ix_device_tokens_user_id", "device_tokens", ["user_id"], unique=False)
    op.create_index("ix_device_tokens_fcm_token", "device_tokens", ["fcm_token"], unique=True)


def downgrade() -> None:
    """Drop device_tokens table and its indexes."""
    op.drop_index("ix_device_tokens_fcm_token", table_name="device_tokens")
    op.drop_index("ix_device_tokens_user_id", table_name="device_tokens")
    op.drop_table("device_tokens")
