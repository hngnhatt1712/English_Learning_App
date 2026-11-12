"""Email sender protocol and implementations for password reset OTP delivery (D-08)."""
from __future__ import annotations

import logging
import smtplib
from email.message import EmailMessage
from typing import Protocol, runtime_checkable

from app.core.config import settings

logger = logging.getLogger("app.mailer")
console_logger = logging.getLogger("app.mailer.console")
console_logger.setLevel(logging.INFO)
if not console_logger.handlers:
    _handler = logging.StreamHandler()
    _handler.setFormatter(logging.Formatter("%(levelname)s:%(name)s:%(message)s"))
    console_logger.addHandler(_handler)

OTP_EMAIL_SUBJECT = "Mã OTP đặt lại mật khẩu"


def build_otp_email_body(otp: str, ttl_minutes: int = 10) -> str:
    """Build the Vietnamese plain-text OTP email body without any links."""
    return (
        f"Mã OTP đặt lại mật khẩu của bạn là: {otp}\n"
        f"Mã có hiệu lực trong {ttl_minutes} phút.\n"
        "Nếu bạn không yêu cầu, hãy bỏ qua email này."
    )


@runtime_checkable
class EmailSender(Protocol):
    """Protocol for sending password reset OTP emails."""

    def send_otp_email(self, to_email: str, otp: str) -> None:
        """Deliver a 6-digit OTP email to `to_email`."""
        ...


class ConsoleEmailSender:
    """Development-only email sender that logs the OTP email body at INFO level."""

    def __init__(self, *, environment: str | None = None, email_backend: str | None = None) -> None:
        env_val = (
            environment
            if environment is not None
            else str(getattr(settings, "ENV", getattr(settings, "ENVIRONMENT", "")))
        )
        backend_val = (
            email_backend
            if email_backend is not None
            else str(getattr(settings, "EMAIL_BACKEND", "console"))
        )
        if env_val.strip().lower() in {"prod", "production"} or backend_val.strip().lower() != "console":
            raise RuntimeError("ConsoleEmailSender is forbidden outside console dev mode")
        console_logger.warning("console_email_sender_active_dev_only")

    def send_otp_email(self, to_email: str, otp: str) -> None:
        ttl_minutes = max(1, int(getattr(settings, "OTP_TTL_SECONDS", 600)) // 60)
        body = build_otp_email_body(otp, ttl_minutes=ttl_minutes)
        console_logger.info("otp_email_body\n%s", body)


class SmtpEmailSender:
    """SMTP email sender using stdlib `smtplib` with optional STARTTLS and 10s timeout."""

    def __init__(
        self,
        *,
        host: str | None = None,
        port: int | None = None,
        username: str | None = None,
        password: str | None = None,
        from_email: str | None = None,
        use_tls: bool | None = None,
        timeout: float = 10.0,
    ) -> None:
        self.host = (host if host is not None else settings.SMTP_HOST).strip()
        self.port = port if port is not None else (settings.SMTP_PORT or 587)
        self.username = username if username is not None else settings.SMTP_USERNAME
        self.password = password if password is not None else settings.SMTP_PASSWORD
        self.from_email = (from_email if from_email is not None else settings.SMTP_FROM).strip()
        self.use_tls = settings.SMTP_USE_TLS if use_tls is None else use_tls
        self.timeout = timeout

        if not self.host or not self.from_email:
            raise RuntimeError("SMTP_HOST and SMTP_FROM must be configured when EMAIL_BACKEND='smtp'")

    def send_otp_email(self, to_email: str, otp: str) -> None:
        ttl_minutes = max(1, int(getattr(settings, "OTP_TTL_SECONDS", 600)) // 60)
        msg = EmailMessage()
        msg["Subject"] = OTP_EMAIL_SUBJECT
        msg["From"] = self.from_email
        msg["To"] = to_email
        msg.set_content(build_otp_email_body(otp, ttl_minutes=ttl_minutes))

        try:
            with smtplib.SMTP(self.host, self.port, timeout=self.timeout) as smtp:
                if self.use_tls:
                    smtp.starttls()
                if self.username:
                    smtp.login(self.username, self.password)
                smtp.send_message(msg)
        except (smtplib.SMTPException, OSError, TimeoutError):
            logger.error("email_send_failed")


def get_email_sender() -> EmailSender:
    """FastAPI dependency / factory selecting the configured EmailSender implementation."""
    backend = str(getattr(settings, "EMAIL_BACKEND", "console")).strip().lower()
    if backend == "console":
        return ConsoleEmailSender()
    if backend == "smtp":
        return SmtpEmailSender()
    raise RuntimeError(f"Unsupported EMAIL_BACKEND: {backend}")

