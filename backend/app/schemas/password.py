"""Password change request schema (contract 4.4, 7.2)."""
from __future__ import annotations

from pydantic import Field, ValidationInfo, field_validator

from app.core.schemas import CamelModel
from app.schemas.auth import RegisterRequest


class ChangePasswordRequest(CamelModel):
    current_password: str = Field(min_length=1, max_length=64, repr=False)
    new_password: str = Field(min_length=8, max_length=64, repr=False)

    @field_validator("new_password")
    @classmethod
    def _validate_new_password(cls, value: str, info: ValidationInfo) -> str:
        RegisterRequest._password_rules(value)
        if value == info.data.get("current_password"):
            raise ValueError("New password must differ from the current password")
        return value

