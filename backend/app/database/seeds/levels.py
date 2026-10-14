"""Idempotent seed for the levels table."""
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from app.database.seeds.ids import LEVEL_1_ID, LEVEL_2_ID, LEVEL_3_ID
from app.models.level import Level

LEVELS: list[dict] = [
    {
        "id": LEVEL_1_ID,
        "name": "Beginner (A1)",
        "description": "Get started with everyday vocabulary and basic sentence patterns.",
        "order_index": 1,
    },
    {
        "id": LEVEL_2_ID,
        "name": "Elementary (A2)",
        "description": "Expand your vocabulary and grammar to talk about familiar situations.",
        "order_index": 2,
    },
    {
        "id": LEVEL_3_ID,
        "name": "Intermediate (B1)",
        "description": "Communicate more confidently and understand familiar topics in study and work.",
        "order_index": 3,
    },
]


def seed_levels(session: Session) -> int:
    """Upsert the three levels by id. Does not commit; the caller owns the transaction."""
    stmt = pg_insert(Level).values(LEVELS)
    stmt = stmt.on_conflict_do_update(
        index_elements=["id"],
        set_={
            "name": stmt.excluded.name,
            "description": stmt.excluded.description,
            "order_index": stmt.excluded.order_index,
        },
    )
    session.execute(stmt)
    return len(LEVELS)
