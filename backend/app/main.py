from fastapi import FastAPI, Depends, HTTPException
from sqlalchemy import text
from sqlalchemy.orm import Session

from app.core.config import settings
from app.database.session import get_db


app = FastAPI(
    title=settings.APP_NAME,
    version="1.0.0",
    description="Backend API for Owla English Learning App"
)


@app.get("/")
def root():
    return {
        "message": "Owla English API"
    }


@app.get("/health")
def health_check(db: Session = Depends(get_db)):
    try:
        db.execute(text("SELECT 1"))

        return {
            "status": "healthy",
            "database": "connected"
        }

    except Exception:
        raise HTTPException(
            status_code=503,
            detail={
                "status": "unhealthy",
                "database": "disconnected"
            }
        )