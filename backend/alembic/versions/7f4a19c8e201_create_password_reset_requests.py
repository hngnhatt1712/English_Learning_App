"""create password_reset_requests table

Revision ID: 7f4a19c8e201
Revises: 34d4bbfb73c6
Create Date: 2026-11-09 22:00:00.000000

"""
from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "7f4a19c8e201"
down_revision: str | Sequence[str] | None = "34d4bbfb73c6"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    """Create password_reset_requests table and indexes."""
    op.create_table(
        "password_reset_requests",
        sa.Column("id", sa.Uuid(as_uuid=True), primary_key=True, nullable=False),
        sa.Column(
            "user_id",
            sa.Uuid(as_uuid=True),
            sa.ForeignKey(
                "users.id",
                ondelete="CASCADE",
                name="fk_password_reset_requests_user_id_users",
            ),
            nullable=False,
        ),
        sa.Column("otp_hash", sa.String(length=128), nullable=False),
        sa.Column("otp_expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column(
            "attempts",
            sa.Integer(),
            server_default=sa.text("0"),
            nullable=False,
        ),
        sa.Column("reset_token_hash", sa.String(length=128), nullable=True),
        sa.Column("reset_token_expires_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("consumed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
    )
    op.create_index(
        "ix_password_reset_requests_user_id_created_at",
        "password_reset_requests",
        ["user_id", "created_at"],
        unique=False,
    )
    op.create_index(
        "ix_password_reset_requests_reset_token_hash",
        "password_reset_requests",
        ["reset_token_hash"],
        unique=False,
    )


def downgrade() -> None:
    """Drop password_reset_requests indexes and table."""
    op.drop_index(
        "ix_password_reset_requests_reset_token_hash",
        table_name="password_reset_requests",
    )
    op.drop_index(
        "ix_password_reset_requests_user_id_created_at",
        table_name="password_reset_requests",
    )
    op.drop_table("password_reset_requests")

