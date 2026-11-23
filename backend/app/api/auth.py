"""Auth API router: register, login, me, password change & reset (contract 7.2)."""
from typing import Annotated

from app.core.dependencies import CurrentUser
from app.core.mailer import EmailSender, get_email_sender
from app.core.schemas import ApiResponse, ok
from app.database.session import get_db
from app.schemas.auth import (
    AuthResultDto,
    LoginRequest,
    LogoutRequest,
    RegisterRequest,
    UserDto,
)
from app.schemas.password import (
    ChangePasswordRequest,
    ForgotPasswordRequest,
    ForgotPasswordResultDto,
    ResetPasswordRequest,
    VerifyOtpRequest,
    VerifyOtpResultDto,
)
from app.services import (
    auth_service,
    logout_service,
    password_reset_service,
    password_service,
)
from fastapi import APIRouter, BackgroundTasks, Depends
from sqlalchemy.orm import Session

router = APIRouter(prefix="/auth", tags=["auth"])

DbSession = Annotated[Session, Depends(get_db)]
EmailSenderDep = Annotated[EmailSender, Depends(get_email_sender)]


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


@router.post(
    "/forgot-password",
    response_model=ApiResponse[ForgotPasswordResultDto],
    summary="Request a 6-digit password reset OTP via email",
    responses={
        422: {"model": ApiResponse[None], "description": "validation-error"},
    },
)
def forgot_password(
    payload: ForgotPasswordRequest,
    background_tasks: BackgroundTasks,
    db: DbSession,
    email_sender: EmailSenderDep,
):
    result = password_reset_service.request_reset(
        db,
        payload,
        background_tasks=background_tasks,
        email_sender=email_sender,
    )
    return ok(result, "If the email is registered, an OTP has been sent")


@router.post(
    "/forgot-password/verify-otp",
    response_model=ApiResponse[VerifyOtpResultDto],
    summary="Verify a 6-digit OTP and issue a one-time reset token",
    responses={
        400: {"model": ApiResponse[None], "description": "invalid_otp"},
        422: {"model": ApiResponse[None], "description": "validation-error"},
    },
)
def verify_otp(payload: VerifyOtpRequest, db: DbSession):
    return ok(password_reset_service.verify_otp(db, payload), "OTP verified")


@router.post(
    "/reset-password",
    response_model=ApiResponse[None],
    summary="Reset password using a one-time reset token",
    responses={
        400: {"model": ApiResponse[None], "description": "invalid_reset_token"},
        422: {"model": ApiResponse[None], "description": "validation-error"},
    },
)
def reset_password(payload: ResetPasswordRequest, db: DbSession):
    password_reset_service.reset_password(db, payload)
    return ok(None, "Password reset successful")


