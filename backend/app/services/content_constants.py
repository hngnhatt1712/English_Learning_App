"""Shared content limits from contract sections 6.4 and 9.1."""
from app.models.progress import MAX_STARS_PER_TOPIC

ROUNDS_PER_TOPIC = 5
MAX_STARS_PER_ROUND = MAX_STARS_PER_TOPIC // ROUNDS_PER_TOPIC

__all__ = ["MAX_STARS_PER_ROUND", "MAX_STARS_PER_TOPIC", "ROUNDS_PER_TOPIC"]
