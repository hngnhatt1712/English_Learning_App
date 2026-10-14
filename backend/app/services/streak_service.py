"""Streak read helpers — contract section 9.3.

Rules (read path only; write / touch() is Sprint 7):
- today_vn(now): convert aware datetime to Asia/Ho_Chi_Minh date.
- effective_streak(current, last, today): return 0 when last is None or
  gap >= 2 days; otherwise return current unchanged.

NOTE: config.py has no APP_TIMEZONE variable — using module constant (D-16).
"""
from __future__ import annotations

from datetime import date, datetime
from zoneinfo import ZoneInfo

# D-16: config.py does not expose a timezone variable; use module constant.
APP_TIMEZONE = "Asia/Ho_Chi_Minh"
_TZ = ZoneInfo(APP_TIMEZONE)


def today_vn(now: datetime | None = None) -> date:
    """Return the current calendar date in the Asia/Ho_Chi_Minh timezone.

    Args:
        now: An **aware** datetime.  When None, defaults to ``datetime.now(_TZ)``.

    Returns:
        The local date in the VN timezone.

    Raises:
        ValueError: If ``now`` is a naive datetime (no tzinfo).  This
            indicates a programming error — callers must always supply
            timezone-aware datetimes.
    """
    if now is None:
        now = datetime.now(_TZ)
    if now.tzinfo is None:
        raise ValueError(
            "today_vn() received a naive datetime; supply an aware datetime "
            "(e.g. datetime.now(ZoneInfo('Asia/Ho_Chi_Minh'))) or pass None."
        )
    return now.astimezone(_TZ).date()


def effective_streak(current: int, last: date | None, today: date) -> int:
    """Return the effective (display) streak for the read path.

    Contract 9.3 — when *reading* (/auth/me, /profile, /home, /progress):
    if ``lastLearningDate`` is ≥ 2 days before today → return 0 without
    touching the database.

    Args:
        current: The ``currentStreak`` stored in the DB.
        last:    The ``lastLearningDate`` stored in the DB (may be None).
        today:   The caller-supplied "today" date in VN timezone (use
                 ``today_vn()``).  Accepting it as a parameter allows tests
                 to fix time without monkeypatching.

    Returns:
        0 if ``last`` is None or ``(today - last).days >= 2``.
        ``current`` in all other cases (including ``last`` in the future,
        which can happen due to clock skew — safe to keep streak).
    """
    if last is None:
        return 0
    gap = (today - last).days
    if gap >= 2:
        return 0
    return current
