"""Tests for global envelope and error handlers (contract 3, 10.1, 10.2)."""
import re
import uuid
from enum import Enum
from uuid import UUID

import pytest
from fastapi import FastAPI, HTTPException
from fastapi.testclient import TestClient
from pydantic import BaseModel, Field

from app.core.errors import AppError, ErrorType, register_handlers
from app.core.schemas import ok, paginated
from app.schemas.auth import RegisterRequest
from app.schemas.profile import ProfileUpdateRequest
from tests.conftest import assert_envelope, assert_error

# Build mini test app
app = FastAPI()
register_handlers(app)


class SampleEnum(str, Enum):
    A = "a"
    B = "b"


class CodeMapSchema(BaseModel):
    req_field: str
    short_str: str = Field(default="ok_val", min_length=5)
    long_str: str = Field(default="ok_val", max_length=10)
    min_val: int = Field(default=10, ge=10)
    max_val: int = Field(default=10, le=10)
    enum_val: SampleEnum = SampleEnum.A
    pattern_val: str = Field(default="123", pattern=r"^[0-9]+$")
    uuid_val: UUID = Field(default=UUID("00000000-0000-0000-0000-000000000001"))


@app.get("/ok")
def route_ok():
    return ok({"x": 1})


@app.get("/paginated")
def route_paginated():
    return paginated(items=[{"id": "1"}], total=1, page=1, limit=20)


@app.get("/app-error")
def route_app_error():
    raise AppError(409, ErrorType.EMAIL_TAKEN, "Email already registered")


@app.get("/http-404")
def route_http_404():
    raise HTTPException(404)


@app.get("/http-418")
def route_http_418():
    raise HTTPException(418)


@app.get("/http-503")
def route_http_503():
    raise HTTPException(503, detail="secret-internal")


@app.get("/boom")
def route_boom():
    raise RuntimeError("secret-internal")


@app.post("/register-echo")
def route_register_echo(body: RegisterRequest):
    return ok(body.model_dump(mode="json"))


@app.post("/profile-echo")
def route_profile_echo(body: ProfileUpdateRequest):
    return ok(body.model_dump(mode="json"))


@app.post("/code-map-echo")
def route_code_map_echo(body: CodeMapSchema):
    return ok(body.model_dump(mode="json"))


client = TestClient(app, raise_server_exceptions=False)


def _assert_camel_case_keys(data: object) -> None:
    """Recursively assert all JSON object keys are camelCase (^[a-z][A-Za-z0-9]*$)."""
    camel_re = re.compile(r"^[a-z][A-Za-z0-9]*$")
    if isinstance(data, dict):
        for k, v in data.items():
            assert camel_re.match(k), f"Key '{k}' is not camelCase!"
            _assert_camel_case_keys(v)
    elif isinstance(data, list):
        for item in data:
            _assert_camel_case_keys(item)


def test_1_ok_and_request_id_header():
    """Case 1: /ok has X-Request-Id, echoes valid, generates new UUID for invalid/too long."""
    # Default without header -> generates UUID
    res = client.get("/ok")
    assert res.status_code == 200
    assert_envelope(res.json(), ok=True)
    rid = res.headers.get("X-Request-Id")
    assert rid is not None
    UUID(rid)  # Must be valid UUID

    # Echo valid X-Request-Id
    res_echo = client.get("/ok", headers={"X-Request-Id": "abc"})
    assert res_echo.status_code == 200
    assert res_echo.headers.get("X-Request-Id") == "abc"

    # Reject invalid characters
    res_bad = client.get("/ok", headers={"X-Request-Id": "bad id!"})
    assert res_bad.status_code == 200
    rid_bad = res_bad.headers.get("X-Request-Id")
    assert rid_bad != "bad id!"
    UUID(rid_bad)

    # Reject too long (>64 chars)
    long_id = "a" * 200
    res_long = client.get("/ok", headers={"X-Request-Id": long_id})
    assert res_long.status_code == 200
    rid_long = res_long.headers.get("X-Request-Id")
    assert rid_long != long_id
    UUID(rid_long)


def test_2_app_error():
    """Case 2: /app-error HTTP 409, email-taken, traceId == header, errors is None, data is None."""
    res = client.get("/app-error")
    assert res.status_code == 409
    body = res.json()
    assert_envelope(body, ok=False)
    assert body["error"]["type"] == "email-taken"
    assert body["error"]["traceId"] == res.headers.get("X-Request-Id")
    assert body["error"]["errors"] is None
    assert body["data"] is None


def test_3_validation_error_code_map_and_model_level():
    """Case 3: 422 errors[0].field has no body prefix, Table 4.3 covered, model-level field == 'body'."""
    cases = [
        ({}, "REQUIRED", "missing"),
        ({"req_field": "ok", "short_str": "a"}, "MIN_LENGTH", "string_too_short"),
        ({"req_field": "ok", "long_str": "12345678901"}, "MAX_LENGTH", "string_too_long"),
        ({"req_field": "ok", "min_val": 5}, "MIN_VALUE", "greater_than_equal"),
        ({"req_field": "ok", "max_val": 15}, "MAX_VALUE", "less_than_equal"),
        ({"req_field": "ok", "enum_val": "invalid"}, "INVALID_ENUM", "enum"),
        ({"req_field": "ok", "pattern_val": "abc"}, "INVALID_FORMAT", "string_pattern_mismatch"),
        ({"req_field": "ok", "uuid_val": "not-a-uuid"}, "INVALID_FORMAT", "uuid_parsing"),
        ({"req_field": "ok", "uuid_val": 12345}, "INVALID_FORMAT", "uuid_type"),
    ]
    for payload, exp_code, pydantic_type in cases:
        res = client.post("/code-map-echo", json=payload)
        assert res.status_code == 422
        body = res.json()
        assert_error(body, type_="validation-error", code=exp_code)
        err = body["error"]["errors"][0]
        assert not err["field"].startswith("body."), (
            f"Field '{err['field']}' has unwanted 'body.' prefix for {pydantic_type}"
        )

    # json_invalid case
    res_json = client.post(
        "/code-map-echo",
        content="{bad json",
        headers={"Content-Type": "application/json"},
    )
    assert res_json.status_code == 422
    assert_error(res_json.json(), type_="validation-error", code="INVALID_FORMAT")

    # model_attributes_type case (list instead of dict)
    res_model_attr = client.post("/code-map-echo", json=[1, 2, 3])
    assert res_model_attr.status_code == 422
    assert_error(res_model_attr.json(), type_="validation-error", code="INVALID_FORMAT")

    # Model-level error: ProfileUpdateRequest with {} -> field == "body"
    res_prof = client.post("/profile-echo", json={})
    assert res_prof.status_code == 422
    prof_body = res_prof.json()
    assert_error(prof_body, type_="validation-error", code="INVALID_FORMAT")
    assert prof_body["error"]["errors"][0]["field"] == "body"


def test_4_unhandled_exception_boom():
    """Case 4: /boom HTTP 500, internal-error, has X-Request-Id header matching traceId, no leakage."""
    res = client.get("/boom")
    assert res.status_code == 500
    body = res.json()
    assert_envelope(body, ok=False)
    assert body["error"]["type"] == "internal-error"
    rid = res.headers.get("X-Request-Id")
    assert rid is not None
    assert body["error"]["traceId"] == rid

    # Response text must not leak internal details
    assert "secret-internal" not in res.text
    assert "Traceback" not in res.text
    assert ".py" not in res.text


def test_5_http_status_mapping():
    """Case 5: 503 -> 503 internal-error (DB down giữ nguyên status), 418/405 -> 400 bad-request, 404 -> not-found."""
    # 503 (DB/infra down) -> giữ 503, type == internal-error, không rò secret
    res_503 = client.get("/http-503")
    assert res_503.status_code == 503
    body_503 = res_503.json()
    assert_error(body_503, type_="internal-error")
    assert "secret-internal" not in res_503.text

    # 418 -> 400 bad-request
    res_418 = client.get("/http-418")
    assert res_418.status_code == 400
    assert_error(res_418.json(), type_="bad-request")

    # 405 Method Not Allowed -> 400 bad-request
    res_405 = client.post("/ok")
    assert res_405.status_code == 400
    assert_error(res_405.json(), type_="bad-request")

    # 404 handler and nonexistent route -> 404 not-found
    res_404_h = client.get("/http-404")
    assert res_404_h.status_code == 404
    assert_error(res_404_h.json(), type_="not-found")

    res_404_non = client.get("/nonexistent-endpoint")
    assert res_404_non.status_code == 404
    assert_error(res_404_non.json(), type_="not-found")


def test_6_app_error_enum_validation():
    """Case 6: AppError requires valid ErrorType or ValueError is raised."""
    with pytest.raises(ValueError):
        AppError(409, "email_taken", "x")

    with pytest.raises(ValueError):
        AppError(409, "nope", "x")

    err = AppError(409, "email-taken", "x")
    assert err.type == "email-taken"

    err_enum = AppError(409, ErrorType.EMAIL_TAKEN, "x")
    assert err_enum.type == "email-taken"


def test_7_error_type_enum_members():
    """Case 7: ErrorType has exactly the 15 values defined in contract Table 3.3."""
    contract_types = {
        "bad-request",
        "wrong-password",
        "unauthorized",
        "invalid-credentials",
        "forbidden",
        "level-locked",
        "topic-locked",
        "round-locked",
        "not-found",
        "email-taken",
        "username-taken",
        "round-incomplete",
        "validation-error",
        "rate-limited",
        "internal-error",
    }
    actual_types = {m.value for m in ErrorType}
    assert actual_types == contract_types
    assert len(ErrorType) == 15


def test_8_all_json_keys_camel_case():
    """Case 8: All JSON response keys (success, errors, pagination) match camelCase."""
    # Success /ok
    _assert_camel_case_keys(client.get("/ok").json())

    # Success /paginated
    _assert_camel_case_keys(client.get("/paginated").json())

    # App error
    _assert_camel_case_keys(client.get("/app-error").json())

    # Validation error
    _assert_camel_case_keys(client.post("/code-map-echo", json={}).json())

    # Internal error
    _assert_camel_case_keys(client.get("/boom").json())


def test_9_register_password_short_no_leak():
    """Case 9: POST /register-echo with password='short1' does not leak 'short1' in error."""
    res = client.post(
        "/register-echo",
        json={"username": "nhat_dao", "email": "nhat@example.com", "password": "short1"},
    )
    assert res.status_code == 422
    body = res.json()
    assert_error(body, type_="validation-error")
    assert "short1" not in res.text
    for err in body["error"]["errors"]:
        assert "short1" not in err["message"]
