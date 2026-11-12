from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

BASE_DIR = Path(__file__).resolve().parent.parent.parent  #Trỏ về thư mục backend


class Settings(BaseSettings):
  APP_NAME: str = "Owla English API"
  API_V1_PREFIX: str = "/api/v1"

  DATABASE_URL: str

  JWT_SECRET_KEY: str
  JWT_ALGORITHM: str = "HS256"
  ACCESS_TOKEN_EXPIRE_MINUTES: int = 10080

  FCM_CREDENTIALS_PATH: str = ""

  PASSWORD_RESET_PEPPER: str = ""
  OTP_TTL_SECONDS: int = 600
  OTP_MAX_ATTEMPTS: int = 5
  OTP_RESEND_COOLDOWN_SECONDS: int = 60
  OTP_MAX_REQUESTS_PER_HOUR: int = 5
  RESET_TOKEN_TTL_SECONDS: int = 600
  EMAIL_BACKEND: str = "console"
  SMTP_HOST: str = ""
  SMTP_PORT: int | None = None
  SMTP_USERNAME: str = ""
  SMTP_PASSWORD: str = ""
  SMTP_FROM: str = ""
  SMTP_USE_TLS: bool = True

  model_config = SettingsConfigDict(
      env_file=str(BASE_DIR / ".env"),  # Trỏ file .env
      env_file_encoding="utf-8",
      extra="ignore",
  )


settings = Settings()  # type: ignore[call-arg]
