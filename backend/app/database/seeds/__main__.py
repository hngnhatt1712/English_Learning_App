"""Run all seed scripts. Usage (from backend/): python -m app.database.seeds"""
import sys

from sqlalchemy.exc import SQLAlchemyError

from app.database.seeds.levels import seed_levels
from app.database.session import SessionLocal


def main() -> int:
    try:
        with SessionLocal() as session, session.begin():
            count = seed_levels(session)
    except SQLAlchemyError as exc:
        # Print only the exception type and first message line; never the connection URL.
        first_line = str(exc).splitlines()[0] if str(exc) else ""
        print(f"Seeding failed: {type(exc).__name__}: {first_line}", file=sys.stderr)
        return 1
    print(f"Seeded {count} levels.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
