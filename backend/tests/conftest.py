"""Pytest configuration and contract assertion helpers."""
import sys
from pathlib import Path

# Ensure backend root is in sys.path
backend_dir = Path(__file__).resolve().parent.parent
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))

import pytest
from app.core.errors import ErrorType


def assert_envelope(data: dict, ok: bool = True) -> None:
    """Verify that a response JSON conforms strictly to the contract envelope."""
    assert isinstance(data, dict), "Response must be a JSON object"
    assert set(data.keys()) == {"status", "message", "data", "error"}, (
        f"Envelope keys must be exactly {{status, message, data, error}}, got {set(data.keys())}"
    )
    assert isinstance(data["message"], str), "message must be a string"

    if ok:
        assert data["status"] is True, "status must be True for successful response"
        assert data["error"] is None, "error must be None for successful response"
    else:
        assert data["status"] is False, "status must be False for error response"
        assert data["data"] is None, "data must be None for error response"
        assert isinstance(data["error"], dict), "error must be an object for error response"
        assert set(data["error"].keys()) == {"type", "detail", "traceId", "errors"}, (
            f"error object keys must be exactly {{type, detail, traceId, errors}}, got {set(data['error'].keys())}"
        )
        assert data["error"]["type"] in {e.value for e in ErrorType}, (
            f"error.type '{data['error']['type']}' must be in ErrorType enum"
        )
        assert isinstance(data["error"]["detail"], str), "error.detail must be a string"
        assert isinstance(data["error"]["traceId"], str) and data["error"]["traceId"], (
            "error.traceId must be a non-empty string"
        )
        if data["error"]["type"] == "validation-error":
            assert isinstance(data["error"]["errors"], list), (
                "error.errors must be a list for validation-error"
            )
            for err in data["error"]["errors"]:
                assert isinstance(err, dict), "Each item in error.errors must be an object"
                assert set(err.keys()) == {"field", "message", "code"}, (
                    f"FieldError keys must be {{field, message, code}}, got {set(err.keys())}"
                )
        else:
            assert data["error"]["errors"] is None, (
                "error.errors must be None for non-validation error"
            )


def assert_error(data: dict, type_: str, code: str | None = None) -> None:
    """Verify an error envelope and its type/code."""
    assert_envelope(data, ok=False)
    assert data["error"]["type"] == type_, (
        f"Expected error.type '{type_}', got '{data['error']['type']}'"
    )
    if code is not None:
        errors = data["error"]["errors"]
        assert isinstance(errors, list) and len(errors) > 0, "Expected non-empty error.errors list"
        codes = [e.get("code") for e in errors]
        assert code in codes, f"Expected error code '{code}' in {codes}"


@pytest.fixture
def env_assert():
    return assert_envelope


@pytest.fixture
def err_assert():
    return assert_error
