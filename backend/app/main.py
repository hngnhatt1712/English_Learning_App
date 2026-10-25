from fastapi import FastAPI, Depends, HTTPException
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.errors import register_handlers
from app.core.schemas import ok
from app.database.session import get_db


app = FastAPI(
    title=settings.APP_NAME,
    version="1.0.0",
    description="Backend API for Owla English Learning App"
)

# Đăng ký global error handlers TRƯỚC mọi router (contract 10.2)
register_handlers(app)
from app.api import auth
app.include_router(auth.router, prefix="/api/v1")
from app.api import levels
app.include_router(levels.router, prefix="/api/v1")
from app.api import profile
app.include_router(profile.router, prefix="/api/v1")


@app.get("/")
def root():
    return {
        "message": "Owla English API"
    }


@app.get("/health")
def health_check(db: Session = Depends(get_db)):
    try:
        db.execute(text("SELECT 1"))
        return ok(
            {"service": "english-learning-api", "version": "1.1.0", "database": "up"},
            message="OK",
        )

    except Exception:
        raise HTTPException(
            status_code=503,
            detail="Service Unavailable",
        )