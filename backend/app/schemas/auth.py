"""Auth schemas (contract 6.2, 7.2)."""
import re
from datetime import date
from typing import Literal
from uuid import UUID

from pydantic import EmailStr, Field, field_validator

from app.core.schemas import CamelModel
from app.schemas.types import USERNAME_MAX, USERNAME_MIN, USERNAME_PATTERN, UtcDatetime

_HAS_LETTER = re.compile(r"[A-Za-z]")
_HAS_DIGIT = re.compile(r"[0-9]")

class RegisterRequest(CamelModel):
    username: str = Field(
        min_length=USERNAME_MIN,
        max_length=USERNAME_MAX,
        pattern=USERNAME_PATTERN,
    )
    email: EmailStr = Field(max_length=254)
    password: str = Field(min_length=8, max_length=64, repr=False)

    @field_validator("email", mode="before")
    @classmethod
    def _plain_email_only(cls, value: object) -> object:
        if isinstance(value, str) and (any(c in value for c in "<>") or any(c.isspace() for c in value)):
            raise ValueError("Invalid email address")
        return value

    @field_validator("password")
    @classmethod
    def _password_rules(cls, value: str) -> str:
        if not _HAS_LETTER.search(value) or not _HAS_DIGIT.search(value):
            raise ValueError("Password must contain at least one letter and one digit")
        return value


class LoginRequest(CamelModel):
    identifier: str = Field(min_length=1, max_length=254)
    password: str = Field(min_length=1, max_length=64, repr=False)

    @field_validator("identifier")
    @classmethod
    def _identifier_not_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("Identifier must not be blank")
        return value


class UserDto(CamelModel):
    """Build via the service layer: currentStreak must be read-adjusted
    (contract 9.3); never model_validate(orm_user) directly in a router."""

    id: UUID
    username: str
    email: str
    selected_level_id: UUID | None
    current_streak: int = Field(ge=0)
    last_learning_date: date | None
    created_at: UtcDatetime


class AuthResultDto(CamelModel):
    access_token: str = Field(repr=False)
    token_type: Literal["Bearer"] = "Bearer"
    expires_in: int = Field(ge=1)
    user: UserDto


class LogoutRequest(CamelModel):
    fcm_token: str | None = Field(default=None, min_length=1, max_length=4096, repr=False)
