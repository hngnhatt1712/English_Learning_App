"""Tests for Level and Profile schemas (contract 6.3, 7.3)."""
import uuid
from datetime import datetime, timezone
from uuid import UUID

import pytest
from pydantic import ValidationError

from app.schemas.enums import ContentStatus
from app.schemas.level import LevelDto
from app.schemas.profile import ProfileDto, ProfileLevelDto, ProfileUpdateRequest


def test_level_dto_keys_and_null_description():
    """Verify LevelDto 9 keys and description=None serializes to null."""
    uid = uuid.uuid4()
    level = LevelDto(
        id=uid,
        name="Beginner (A1)",
        description=None,
        order_index=1,
        status=ContentStatus.UNLOCKED,
        stars=7,
        max_stars=45,
        topics_completed=0,
        topics_total=3,
    )
    dumped = level.model_dump(mode="json", by_alias=True)
    expected_keys = {
        "id",
        "name",
        "description",
        "orderIndex",
        "status",
        "stars",
        "maxStars",
        "topicsCompleted",
        "topicsTotal",
    }
    assert set(dumped.keys()) == expected_keys
    assert len(dumped) == 9
    assert dumped["description"] is None
    assert dumped["status"] == "unlocked"


def test_level_dto_status_enum():
    """Verify status accepts lowercase, rejects uppercase and unknown values."""
    uid = uuid.uuid4()
    # Accept valid string
    level = LevelDto(
        id=uid,
        name="Beginner",
        description="desc",
        order_index=1,
        status="unlocked",  # type: ignore[arg-type]
        stars=0,
        max_stars=45,
        topics_completed=0,
        topics_total=3,
    )
    assert level.status == ContentStatus.UNLOCKED

    # Reject uppercase
    with pytest.raises(ValidationError):
        LevelDto(
            id=uid,
            name="Beginner",
            description="desc",
            order_index=1,
            status="UNLOCKED",  # type: ignore[arg-type]
            stars=0,
            max_stars=45,
            topics_completed=0,
            topics_total=3,
        )

    # Reject unknown
    with pytest.raises(ValidationError):
        LevelDto(
            id=uid,
            name="Beginner",
            description="desc",
            order_index=1,
            status="in_progress",  # type: ignore[arg-type]
            stars=0,
            max_stars=45,
            topics_completed=0,
            topics_total=3,
        )


def test_level_dto_order_index_constraint():
    with pytest.raises(ValidationError):
        LevelDto(
            id=uuid.uuid4(),
            name="Beginner",
            description=None,
            order_index=0,  # ge=1 required
            status=ContentStatus.LOCKED,
            stars=0,
            max_stars=45,
            topics_completed=0,
            topics_total=3,
        )


def test_profile_dto_keys_and_null_level():
    """Verify ProfileDto 8 keys, ProfileLevelDto 5 keys, and null level."""
    uid = uuid.uuid4()
    profile = ProfileDto(
        id=uid,
        username="nhat_dao",
        email="nhat@example.com",
        level=None,
        total_stars=7,
        max_stars=135,
        current_streak=3,
        created_at=datetime(2026, 10, 4, 10, 30, 0, tzinfo=timezone.utc),
    )
    dumped = profile.model_dump(mode="json", by_alias=True)
    expected_profile_keys = {
        "id",
        "username",
        "email",
        "level",
        "totalStars",
        "maxStars",
        "currentStreak",
        "createdAt",
    }
    assert set(dumped.keys()) == expected_profile_keys
    assert len(dumped) == 8
    assert dumped["level"] is None
    assert dumped["createdAt"].endswith("Z")

    # With ProfileLevelDto
    level_sub = ProfileLevelDto(
        id=uuid.uuid4(),
        name="Beginner (A1)",
        stars=7,
        max_stars=45,
        progress_percent=15.6,
    )
    dumped_sub = level_sub.model_dump(mode="json", by_alias=True)
    assert set(dumped_sub.keys()) == {
        "id",
        "name",
        "stars",
        "maxStars",
        "progressPercent",
    }
    assert len(dumped_sub) == 5


@pytest.mark.parametrize("invalid_percent", [-0.1, 100.1])
def test_profile_level_dto_percent_bounds(invalid_percent):
    with pytest.raises(ValidationError):
        ProfileLevelDto(
            id=uuid.uuid4(),
            name="Beginner",
            stars=0,
            max_stars=45,
            progress_percent=invalid_percent,
        )


def test_profile_update_request_validation():
    """Verify partial updates, model_fields_set, extra field ignoring, and null rejection."""
    valid_uuid_str = str(uuid.uuid4())

    # Empty payload fails
    with pytest.raises(ValidationError):
        ProfileUpdateRequest.model_validate({})

    # Payload with null username fails
    with pytest.raises(ValidationError):
        ProfileUpdateRequest.model_validate({"username": None})

    # Payload with null selectedLevelId fails
    with pytest.raises(ValidationError):
        ProfileUpdateRequest.model_validate({"selectedLevelId": None})

    # Invalid selectedLevelId type/format fails
    with pytest.raises(ValidationError):
        ProfileUpdateRequest.model_validate({"selectedLevelId": "abc"})

    with pytest.raises(ValidationError):
        ProfileUpdateRequest.model_validate({"selectedLevelId": 123})

    # Invalid username format/length fails
    with pytest.raises(ValidationError):
        ProfileUpdateRequest.model_validate({"username": ""})

    with pytest.raises(ValidationError):
        ProfileUpdateRequest.model_validate({"username": "a@b"})

    # Extra field ignored -> effectively empty -> fails validator
    with pytest.raises(ValidationError):
        ProfileUpdateRequest.model_validate({"email": "x"})

    # Valid: only username
    p1 = ProfileUpdateRequest.model_validate({"username": "nhat_new"})
    assert p1.username == "nhat_new"
    assert p1.model_fields_set == {"username"}

    # Valid: only selectedLevelId
    p2 = ProfileUpdateRequest.model_validate({"selectedLevelId": valid_uuid_str})
    assert p2.selected_level_id == UUID(valid_uuid_str)
    assert p2.model_fields_set == {"selected_level_id"}

    # Valid: both
    p3 = ProfileUpdateRequest.model_validate(
        {"username": "nhat_new", "selectedLevelId": valid_uuid_str}
    )
    assert p3.username == "nhat_new"
    assert p3.selected_level_id == UUID(valid_uuid_str)
    assert p3.model_fields_set == {"username", "selected_level_id"}


def test_profile_update_request_json_schema():
    """Verify JSON Schema matches non-nullable, non-required, and no default null."""
    schema = ProfileUpdateRequest.model_json_schema(by_alias=True)
    props = schema.get("properties", {})
    assert set(props.keys()) == {"username", "selectedLevelId"}

    # Not in required
    assert "required" not in schema or not schema["required"]

    # username: type string, no default null, not nullable
    u_prop = props["username"]
    assert u_prop.get("type") == "string"
    assert "default" not in u_prop
    assert "anyOf" not in u_prop

    # selectedLevelId: type string, format uuid, no default null, not nullable
    l_prop = props["selectedLevelId"]
    assert l_prop.get("type") == "string"
    assert l_prop.get("format") == "uuid"
    assert "default" not in l_prop
    assert "anyOf" not in l_prop
