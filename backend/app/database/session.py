"""Kết nối PostgreSQL bằng SQLAlchemy 2.0 (sync, driver psycopg2)."""
from collections.abc import Generator

from sqlalchemy import create_engine, text
from sqlalchemy.orm import Session, sessionmaker

from app.core.config import settings

engine = create_engine(
    settings.DATABASE_URL,  # postgresql+psycopg2://user:pass@localhost:5432/dbname
    pool_pre_ping=True,     # tự bỏ connection chết 
    pool_size=5,
    max_overflow=10,
)

SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, expire_on_commit=False)


def get_db() -> Generator[Session, None, None]:
    """FastAPI dependency: mỗi request 1 session, luôn đóng khi xong."""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def check_db_connection() -> bool:
    """Dùng cho GET /health: chạy SELECT 1 để xác nhận DB sống."""
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
        return True
    except Exception:
        return False