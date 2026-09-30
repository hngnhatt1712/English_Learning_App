from pathlib import Path
from pydantic_settings import BaseSettings, SettingsConfigDict

BASE_DIR = Path(__file__).resolve().parent.parent.parent  #Trỏ về thư mục backend


class Settings(BaseSettings):
  APP_NAME: str = "Owla English API"
  API_V1_PREFIX: str = "/api/v1"

  DATABASE_URL: str

  JWT_SECRET_KEY: str
  JWT_ALGORITHM: str = "HS256"
  ACCESS_TOKEN_EXPIRE_MINUTES: int = 30

  FCM_CREDENTIALS_PATH: str = ""

  model_config = SettingsConfigDict(
      env_file=str(BASE_DIR / ".env"),  # Trỏ file .env
      env_file_encoding="utf-8",
      extra="ignore",
  )


settings = Settings()