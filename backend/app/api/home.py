"""Authenticated draft Home endpoint (contract section 7.9)."""
from typing import Annotated

from app.core.dependencies import CurrentUser
from app.core.schemas import ApiResponse, ok
from app.database.session import get_db
from app.schemas.home import HomeDto
from app.services import home_service
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

router = APIRouter(prefix="/home", tags=["Home"])
DbSession = Annotated[Session, Depends(get_db)]


@router.get(
    "", response_model=ApiResponse[HomeDto], summary="Get the current user's Home dashboard",
    responses={
        401: {"model": ApiResponse[None], "description": "unauthorized"},
        403: {"model": ApiResponse[None], "description": "forbidden"},
        404: {"model": ApiResponse[None], "description": "not-found"},
        422: {"model": ApiResponse[None], "description": "validation-error"},
        500: {"model": ApiResponse[None], "description": "internal-error"},
    },
)
def get_home(user: CurrentUser, db: DbSession) -> dict[str, object]:
    return ok(home_service.get_home(db, user))
