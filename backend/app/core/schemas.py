"""Core base schemas and response helpers (contract 10.1)."""
from typing import Generic, TypeVar
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

T = TypeVar("T")


class CamelModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,   # code dùng snake_case, JSON ra camelCase
        from_attributes=True,    # đọc thẳng từ SQLAlchemy model
        extra="ignore",          # D-07: Giữ extra="ignore" (không bao giờ nhận id, passwordHash…)
    )


class FieldError(CamelModel):
    field: str
    message: str
    code: str


class ErrorBody(CamelModel):
    type: str
    detail: str
    trace_id: str
    errors: list[FieldError] | None = None


class ApiResponse(CamelModel, Generic[T]):
    status: bool
    message: str
    data: T | None = None
    error: ErrorBody | None = None


class PageData(CamelModel, Generic[T]):
    items: list[T]
    total: int
    page: int
    limit: int
    total_pages: int


class ListData(CamelModel, Generic[T]):  # danh sách cố định, không phân trang
    items: list[T]


def ok(data=None, message: str = "OK") -> dict:
    return {"status": True, "message": message, "data": data, "error": None}


def paginated(items, total: int, page: int, limit: int, message: str = "OK") -> dict:
    return ok(
        {
            "items": items,
            "total": total,
            "page": page,
            "limit": limit,
            "totalPages": -(-total // limit),
        },
        message,
    )
