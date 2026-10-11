"""Domain enums for learning content."""
from enum import Enum


class ContentStatus(str, Enum):
    LOCKED = "locked"
    UNLOCKED = "unlocked"
    COMPLETED = "completed"
