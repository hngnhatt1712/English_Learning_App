"""Auth API router: register, login, me (contract 7.2)."""
from typing import Annotated

from app.core.dependencies import CurrentUser
from app.core.schemas import ApiResponse, ok
from app.database.session import get_db
from app.schemas.auth import (
    AuthResultDto,
    LoginRequest,
    LogoutRequest,
    RegisterRequest,
    UserDto,
)
from app.services import auth_service, logout_service
from app.schemas.password import ChangePasswordRequest
from app.services import password_service
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

router = APIRouter(prefix="/auth", tags=["auth"])

DbSession = Annotated[Session, Depends(get_db)]


@router.post(
    "/register",
    status_code=201,
    response_model=ApiResponse[AuthResultDto],
    summary="Register a new account",
    responses={
        409: {"model": ApiResponse[None], "description": "email-taken or username-taken"},
        422: {"model": ApiResponse[None], "description": "validation-error"},
    },
)
def register(payload: RegisterRequest, db: DbSession):
    return ok(auth_service.register(db, payload), "User registered")


@router.post(
    "/login",
    response_model=ApiResponse[AuthResultDto],
    summary="Log in with email or username",
    responses={
        401: {"model": ApiResponse[None], "description": "invalid-credentials"},
        422: {"model": ApiResponse[None], "description": "validation-error"},
    },
)
def login(payload: LoginRequest, db: DbSession):
    return ok(auth_service.login(db, payload), "Login successful")


@router.get(
    "/me",
    response_model=ApiResponse[UserDto],
    summary="Get the current user (session check)",
    responses={401: {"model": ApiResponse[None], "description": "unauthorized"}},
)
def me(user: CurrentUser):
    return ok(auth_service.get_me(user), "OK")


@router.post(
    "/logout",
    response_model=ApiResponse[None],
    summary="Log out and unregister the device token",
    responses={
        401: {"model": ApiResponse[None], "description": "unauthorized"},
        422: {"model": ApiResponse[None], "description": "validation-error"},
    },
)
def logout(user: CurrentUser, db: DbSession, payload: LogoutRequest | None = None):
    fcm_token = payload.fcm_token if payload is not None else None
    logout_service.logout(db, user.id, fcm_token)
    return ok(None, "Logged out")


@router.put(
    "/change-password",
    response_model=ApiResponse[AuthResultDto],
    summary="Change the current user's password and issue a new token",
    responses={
        400: {"model": ApiResponse[None], "description": "wrong-password"},
        401: {"model": ApiResponse[None], "description": "unauthorized"},
        422: {"model": ApiResponse[None], "description": "validation-error"},
    },
)
def change_password(payload: ChangePasswordRequest, user: CurrentUser, db: DbSession):
    return ok(password_service.change_password(db, user, payload), "Password changed")

