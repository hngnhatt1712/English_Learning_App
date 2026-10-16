"""Profile schemas (contract 7.3)."""
from typing import Any, cast
from uuid import UUID

from pydantic import ConfigDict, Field, model_validator

from app.core.schemas import CamelModel
from app.schemas.types import USERNAME_MAX, USERNAME_MIN, USERNAME_PATTERN, UtcDatetime


class ProfileLevelDto(CamelModel):
    id: UUID
    name: str
    stars: int = Field(ge=0)
    max_stars: int = Field(ge=0)
    progress_percent: float = Field(ge=0, le=100)


class ProfileDto(CamelModel):
    id: UUID
    username: str
    email: str
    level: ProfileLevelDto | None
    total_stars: int = Field(ge=0)
    max_stars: int = Field(ge=0)
    current_streak: int = Field(ge=0)
    created_at: UtcDatetime


def _remove_default_none(schema: dict, _model_class: type) -> None:
    props = schema.get("properties", {})
    for _k, v in props.items():
        if isinstance(v, dict) and "default" in v and v["default"] is None:
            del v["default"]


class ProfileUpdateRequest(CamelModel):
    model_config = ConfigDict(
        alias_generator=CamelModel.model_config.get("alias_generator"),
        populate_by_name=True,
        from_attributes=True,
        extra="ignore",
        json_schema_extra=_remove_default_none,
    )

    username: str = Field(
        default=cast(Any, None),
        min_length=USERNAME_MIN,
        max_length=USERNAME_MAX,
        pattern=USERNAME_PATTERN,
    )
    selected_level_id: UUID = Field(default=cast(Any, None))

    @model_validator(mode="after")
    def _at_least_one_field(self) -> "ProfileUpdateRequest":
        if not ({"username", "selected_level_id"} & self.model_fields_set):
            raise ValueError("At least one field must be provided")
        return self
