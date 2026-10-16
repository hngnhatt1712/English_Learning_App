"""Unified error handling and Request ID middleware (contract 10.2)."""
import logging
import re
import traceback
import uuid
from enum import Enum
from typing import Any, Mapping

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException


class ErrorType(str, Enum):
    BAD_REQUEST = "bad-request"
    WRONG_PASSWORD = "wrong-password"
    UNAUTHORIZED = "unauthorized"
    INVALID_CREDENTIALS = "invalid-credentials"
    FORBIDDEN = "forbidden"
    LEVEL_LOCKED = "level-locked"
    TOPIC_LOCKED = "topic-locked"
    ROUND_LOCKED = "round-locked"
    NOT_FOUND = "not-found"
    EMAIL_TAKEN = "email-taken"
    USERNAME_TAKEN = "username-taken"
    ROUND_INCOMPLETE = "round-incomplete"
    VALIDATION_ERROR = "validation-error"
    RATE_LIMITED = "rate-limited"
    INTERNAL_ERROR = "internal-error"


class AppError(Exception):
    """Lỗi nghiệp vụ. Vd: raise AppError(403, ErrorType.ROUND_LOCKED, "Round is locked")"""

    def __init__(self, status_code: int, type_: str | ErrorType, detail: str):
        self.status_code = status_code
        self.type = ErrorType(type_).value
        self.detail = detail


_REQUEST_ID_RE = re.compile(r"^[A-Za-z0-9._-]{1,64}$")
logger = logging.getLogger("app.errors")


def _trace(request: Request) -> str:
    return getattr(request.state, "request_id", str(uuid.uuid4()))


def _body(
    request: Request,
    status_code: int,
    message: str,
    type_: str,
    detail: str,
    errors: list[dict[str, Any]] | None = None,
    headers: Mapping[str, str] | None = None,
) -> JSONResponse:
    resp_headers = dict(headers or {})
    trace_id = _trace(request)
    if "X-Request-Id" not in resp_headers:
        resp_headers["X-Request-Id"] = trace_id
    return JSONResponse(
        status_code=status_code,
        headers=resp_headers,
        content={
            "status": False,
            "message": message,
            "data": None,
            "error": {
                "type": type_,
                "detail": detail,
                "traceId": trace_id,
                "errors": errors,
            },
        },
    )


_HTTP_TYPES = {
    400: "bad-request",
    401: "unauthorized",
    403: "forbidden",
    404: "not-found",
    429: "rate-limited",
    503: "internal-error",  # DB/infrastructure unavailable — giữ nguyên 503, bọc envelope chuẩn
}

_CODE_MAP = {
    "missing": "REQUIRED",
    "string_too_short": "MIN_LENGTH",
    "too_short": "MIN_LENGTH",
    "string_too_long": "MAX_LENGTH",
    "too_long": "MAX_LENGTH",
    "greater_than_equal": "MIN_VALUE",
    "greater_than": "MIN_VALUE",
    "less_than_equal": "MAX_VALUE",
    "less_than": "MAX_VALUE",
    "enum": "INVALID_ENUM",
    "literal_error": "INVALID_ENUM",
    "string_pattern_mismatch": "INVALID_FORMAT",
    "value_error": "INVALID_FORMAT",
    "uuid_parsing": "INVALID_FORMAT",
    "uuid_type": "INVALID_FORMAT",
    "json_invalid": "INVALID_FORMAT",
    "model_attributes_type": "INVALID_FORMAT",
    "string_type": "INVALID_FORMAT",
    "int_parsing": "INVALID_FORMAT",
    "int_type": "INVALID_FORMAT",
    "float_parsing": "INVALID_FORMAT",
    "float_type": "INVALID_FORMAT",
    "bool_parsing": "INVALID_FORMAT",
    "bool_type": "INVALID_FORMAT",
    "date_parsing": "INVALID_FORMAT",
    "date_type": "INVALID_FORMAT",
    "datetime_parsing": "INVALID_FORMAT",
    "datetime_type": "INVALID_FORMAT",
}


def register_handlers(app: FastAPI) -> None:
    @app.middleware("http")
    async def request_id_middleware(request: Request, call_next):
        incoming = request.headers.get("X-Request-Id")
        rid = incoming if incoming and _REQUEST_ID_RE.fullmatch(incoming) else str(uuid.uuid4())
        request.state.request_id = rid
        response = await call_next(request)
        response.headers["X-Request-Id"] = rid
        return response

    @app.exception_handler(AppError)
    async def app_error(request: Request, exc: AppError):
        return _body(
            request,
            exc.status_code,
            exc.type.replace("-", " ").title(),
            exc.type,
            exc.detail,
        )

    @app.exception_handler(StarletteHTTPException)
    async def http_error(request: Request, exc: StarletteHTTPException):
        status = exc.status_code
        if status in _HTTP_TYPES:
            if status >= 500:
                # 5xx từ hạ tầng (vd. 503 DB down): giữ nguyên status code,
                # KHÔNG echo exc.detail để tránh rò rỉ thông tin nội bộ.
                return _body(
                    request, status,
                    "Service Unavailable" if status == 503 else "Internal Server Error",
                    _HTTP_TYPES[status],
                    "An unexpected error occurred",
                    headers=exc.headers,
                )
            detail = exc.detail if isinstance(exc.detail, str) else "Error"
            return _body(request, status, detail, _HTTP_TYPES[status], detail, headers=exc.headers)
        if status >= 500:
            return _body(
                request,
                500,
                "Internal Server Error",
                "internal-error",
                "An unexpected error occurred",
            )
        return _body(request, 400, "Bad Request", "bad-request", "Bad request")

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, exc: RequestValidationError):
        errors = [
            {
                "field": ".".join(str(p) for p in e["loc"][1:]) or str(e["loc"][0]),
                "message": e["msg"],
                "code": _CODE_MAP.get(e["type"], "INVALID_VALUE"),
            }
            for e in exc.errors()
        ]
        return _body(
            request,
            422,
            "Validation Error",
            "validation-error",
            "One or more fields failed validation",
            errors=errors,
        )

    @app.exception_handler(Exception)
    async def unhandled(request: Request, exc: Exception):
        trace = _trace(request)
        logger.error(
            "Unhandled %s trace_id=%s %s %s\n%s",
            type(exc).__name__,
            trace,
            request.method,
            request.url.path,
            "".join(traceback.format_tb(exc.__traceback__)),
        )
        return _body(
            request,
            500,
            "Internal Server Error",
            "internal-error",
            "An unexpected error occurred",
            headers={"X-Request-Id": trace},
        )
