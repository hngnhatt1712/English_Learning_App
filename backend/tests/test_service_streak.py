"""Unit tests for streak_service — contract section 9.3.

All tests are pure in-memory; no DB, no network.
Time is always supplied explicitly to avoid flakiness.

Test matrix (section G):
  [S01] last=None → effective_streak returns 0
  [S02] last=today → keep current streak
  [S03] last=yesterday → keep current streak
  [S04] last=2 days ago → effective_streak returns 0
  [S05] last=future date → keep current streak (clock skew safety)
  [S06] timezone boundary 16:59 UTC (same day VN) → correct VN date
  [S07] timezone boundary 17:01 UTC (next day VN) → correct VN date
  [S08] naive datetime → ValueError
  [S09] today_vn(now=None) → returns a date object (smoke test, no crash)
"""
from __future__ import annotations

from datetime import date, datetime, timezone, timedelta

import pytest
from zoneinfo import ZoneInfo

from app.services.streak_service import effective_streak, today_vn

_VN = ZoneInfo("Asia/Ho_Chi_Minh")
_UTC = timezone.utc


# ---------------------------------------------------------------------------
# effective_streak tests
# ---------------------------------------------------------------------------

class TestEffectiveStreak:

    def test_s01_last_none_returns_zero(self):
        """[S01] last=None → 0 regardless of current streak value."""
        today = date(2026, 10, 11)
        assert effective_streak(current=5, last=None, today=today) == 0

    def test_s02_last_today_keeps_streak(self):
        """[S02] last=today → keep current (already learned today)."""
        today = date(2026, 10, 11)
        assert effective_streak(current=7, last=today, today=today) == 7

    def test_s03_last_yesterday_keeps_streak(self):
        """[S03] last=yesterday (gap=1) → keep current streak."""
        today = date(2026, 10, 11)
        yesterday = date(2026, 10, 10)
        assert effective_streak(current=3, last=yesterday, today=today) == 3

    def test_s04_last_two_days_ago_returns_zero(self):
        """[S04] last=2 days ago (gap=2) → reset display to 0."""
        today = date(2026, 10, 11)
        two_days_ago = date(2026, 10, 9)
        assert effective_streak(current=10, last=two_days_ago, today=today) == 0

    def test_s04b_last_many_days_ago_returns_zero(self):
        """[S04b] large gap → also returns 0."""
        today = date(2026, 10, 11)
        old = date(2026, 1, 1)
        assert effective_streak(current=100, last=old, today=today) == 0

    def test_s05_last_future_keeps_streak(self):
        """[S05] last in the future (gap<0) → keep streak (clock skew safety)."""
        today = date(2026, 10, 11)
        future = date(2026, 10, 12)
        assert effective_streak(current=4, last=future, today=today) == 4

    def test_s05b_current_zero_kept(self):
        """[S05b] current=0 is a valid value that should propagate unchanged."""
        today = date(2026, 10, 11)
        yesterday = date(2026, 10, 10)
        assert effective_streak(current=0, last=yesterday, today=today) == 0


# ---------------------------------------------------------------------------
# today_vn tests (timezone boundary)
# ---------------------------------------------------------------------------

class TestTodayVn:

    def test_s06_timezone_boundary_before_midnight_vn(self):
        """[S06] 16:59:59 UTC on 2026-10-11 = 23:59:59+07 → still Oct 11 VN."""
        # UTC 16:59:59 = VN 23:59:59 (same calendar day in VN as 17:00 UTC)
        aware_utc = datetime(2026, 10, 11, 16, 59, 59, tzinfo=_UTC)
        result = today_vn(aware_utc)
        assert result == date(2026, 10, 11), (
            f"Expected 2026-10-11 (still Oct 11 in VN), got {result}"
        )

    def test_s07_timezone_boundary_after_midnight_vn(self):
        """[S07] 17:01:00 UTC on 2026-10-11 = 00:01:00+07 Oct 12 → Oct 12 VN."""
        # UTC 17:01 = VN 00:01 next day
        aware_utc = datetime(2026, 10, 11, 17, 1, 0, tzinfo=_UTC)
        result = today_vn(aware_utc)
        assert result == date(2026, 10, 12), (
            f"Expected 2026-10-12 (next day in VN), got {result}"
        )

    def test_s08_naive_datetime_raises_value_error(self):
        """[S08] Naive datetime must raise ValueError (programming error guard)."""
        naive = datetime(2026, 10, 11, 10, 0, 0)  # no tzinfo
        with pytest.raises(ValueError, match="naive"):
            today_vn(naive)

    def test_s09_no_args_returns_date(self):
        """[S09] today_vn() with no args returns a date object (smoke test)."""
        result = today_vn()
        assert isinstance(result, date)
        assert not isinstance(result, datetime)  # must be plain date, not datetime

    def test_s06b_utc_17_00_00_is_next_day_vn(self):
        """[S06b] Exact midnight VN = UTC 17:00:00 → next day in VN."""
        aware_utc = datetime(2026, 10, 11, 17, 0, 0, tzinfo=_UTC)
        result = today_vn(aware_utc)
        assert result == date(2026, 10, 12)

    def test_vn_aware_datetime_same_as_utc_conversion(self):
        """Passing a VN-aware datetime directly also works."""
        aware_vn = datetime(2026, 10, 11, 23, 30, 0, tzinfo=_VN)
        result = today_vn(aware_vn)
        assert result == date(2026, 10, 11)
