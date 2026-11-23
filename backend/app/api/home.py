"""Authenticated draft Home endpoint (contract section 7.9)."""
from fastapi import APIRouter

from app.core.dependencies import CurrentUser, HomeServiceDep
from app.core.schemas import ApiResponse, ok
from app.schemas.home import HomeDto

router = APIRouter(prefix="/home", tags=["Home"])


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
def get_home(user: CurrentUser, service: HomeServiceDep) -> dict[str, object]:
    return ok(service.get_home(user))
