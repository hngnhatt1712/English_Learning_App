"""Password change and password reset schemas (contract 4.4, 7.2)."""
from __future__ import annotations

from app.core.schemas import CamelModel
from app.schemas.auth import RegisterRequest
from pydantic import EmailStr, Field, ValidationInfo, field_validator


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


class ForgotPasswordRequest(CamelModel):
    email: EmailStr = Field(max_length=254)

    @field_validator("email", mode="before")
    @classmethod
    def _plain_email_only(cls, value: object) -> object:
        if isinstance(value, str):
            value = value.strip()
        return RegisterRequest._plain_email_only(value)


class ForgotPasswordResultDto(CamelModel):
    retry_after_seconds: int = Field(ge=1)


class VerifyOtpRequest(CamelModel):
    email: EmailStr = Field(max_length=254)
    otp: str = Field(pattern=r"^\d{6}$", repr=False)

    @field_validator("email", mode="before")
    @classmethod
    def _plain_email_only(cls, value: object) -> object:
        if isinstance(value, str):
            value = value.strip()
        return RegisterRequest._plain_email_only(value)


class VerifyOtpResultDto(CamelModel):
    reset_token: str = Field(repr=False)
    expires_in_seconds: int = Field(ge=1)


class ResetPasswordRequest(CamelModel):
    reset_token: str = Field(min_length=1, max_length=256, repr=False)
    new_password: str = Field(min_length=8, max_length=64, repr=False)

    @field_validator("new_password")
    @classmethod
    def _validate_new_password(cls, value: str) -> str:
        return RegisterRequest._password_rules(value)


