"""Flashcard scheduling according to API contract §9.4."""
from dataclasses import dataclass
from datetime import datetime, time, timedelta, timezone
from decimal import ROUND_HALF_UP, Decimal
from zoneinfo import ZoneInfo

from app.schemas.vocabulary import SrsRating, VocabularyStatus
from app.services.streak_service import APP_TIMEZONE

_LOCAL_ZONE = ZoneInfo(APP_TIMEZONE)
_MIN_EASE = Decimal("1.3")


@dataclass(frozen=True)
class ReviewSchedule:
    repetition_count: int
    interval_days: int
    ease_factor: Decimal
    status: VocabularyStatus
    last_reviewed_at: datetime
    next_review_at: datetime


def _round_days(days: Decimal) -> int:
    return int(days.to_integral_value(rounding=ROUND_HALF_UP))


def calculate_review(
    *,
    repetition_count: int,
    interval_days: int,
    ease_factor: Decimal,
    rating: SrsRating,
    reviewed_at: datetime,
) -> ReviewSchedule:
    """Calculate one rating from the card's latest state at the supplied instant."""
    if reviewed_at.utcoffset() is None:
        raise ValueError("reviewed_at must be timezone-aware")

    if rating == "again":
        interval_days = 1
        repetition_count = 0
        ease_factor = max(_MIN_EASE, ease_factor - Decimal("0.2"))
    elif rating == "hard":
        interval_days = max(1, _round_days(interval_days * Decimal("1.2")))
        ease_factor = max(_MIN_EASE, ease_factor - Decimal("0.15"))
    elif rating in ("good", "easy"):
        if repetition_count == 0:
            interval_days = 1
        elif repetition_count == 1:
            interval_days = 6
        else:
            days = interval_days * ease_factor
            if rating == "easy":
                days *= Decimal("1.3")
            interval_days = _round_days(days)
        repetition_count += 1
        if rating == "easy":
            ease_factor += Decimal("0.15")
    else:
        raise ValueError("Unknown flashcard rating")

    status: VocabularyStatus = "learning"
    if interval_days >= 21:
        status = "mastered"
    elif repetition_count >= 2:
        status = "review"

    due_date = reviewed_at.astimezone(_LOCAL_ZONE).date() + timedelta(days=interval_days)
    midnight = datetime.combine(due_date, time.min, tzinfo=_LOCAL_ZONE)
    return ReviewSchedule(
        repetition_count=repetition_count,
        interval_days=interval_days,
        ease_factor=ease_factor,
        status=status,
        last_reviewed_at=reviewed_at.astimezone(timezone.utc),
        next_review_at=midnight.astimezone(timezone.utc),
    )
