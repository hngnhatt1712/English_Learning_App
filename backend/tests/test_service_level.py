"""Unit tests for level_service — contract sections 6.3, 9.2, D-12, D-13.

All tests are pure in-memory; no DB, no network.
Fake infrastructure imported from tests/fakes.py.

Test matrix (section G):
  Level status:
    [L01] selected=None → all 3 levels UNLOCKED
    [L02] selected=L1   → L1 unlocked, L2 locked, L3 locked
    [L03] selected=L2   → L1 unlocked, L2 unlocked, L3 locked
    [L04] selected=L3   → L1 unlocked, L2 locked, L3 unlocked
    [L05] No level ever gets status COMPLETED in Sprint 3

  Level DTO (list_levels):
    [D01] list_levels sorts by order_index even when repo returns reversed order
    [D02] stars=0, maxStars=45, topicsCompleted=0, topicsTotal=3 (Sprint 3 placeholders)
    [D03] LevelDto field keys match contract 6.3 (camelCase alias check)
    [D04] description=None is present in DTO (not omitted)

  Defensive:
    [V01] Empty level list → list_levels returns []
    [V02] selected_level_id not in list → L1 unlocked, others locked
    [V03] No level with order_index==1, selected=None → all unlocked
    [V04] No level with order_index==1, selected present → only selected unlocked

  is_level_unlocked:
    [I01] selected=None → every level is unlocked
    [I02] selected=L2   → L1 unlocked, L2 unlocked, L3 locked
    [I03] unknown level_id → locked (not in map)
"""
from __future__ import annotations

import uuid
from unittest.mock import MagicMock, patch

import pytest

from app.schemas.enums import ContentStatus
from app.schemas.level import LevelDto
from app.services.level_service import (
    MAX_STARS_PER_LEVEL,
    TOPICS_PER_LEVEL,
    compute_status_map,
    is_level_unlocked,
    list_levels,
)
from fakes import FakeLevelRepository, FakeLevel, FakeSession


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _three_levels() -> tuple:
    """Return (L1, L2, L3) with order_index 1, 2, 3 and stable UUIDs."""
    l1 = FakeLevel(order_index=1, name="Beginner (A1)")
    l2 = FakeLevel(order_index=2, name="Elementary (A2)")
    l3 = FakeLevel(order_index=3, name="Intermediate (B1)")
    return l1, l2, l3


# ---------------------------------------------------------------------------
# compute_status_map — Level status tests
# ---------------------------------------------------------------------------

class TestComputeStatusMap:

    def test_l01_selected_none_all_unlocked(self):
        """[L01] selected=None → all 3 levels UNLOCKED (D-14 / contract 7.4)."""
        l1, l2, l3 = _three_levels()
        result = compute_status_map([l1, l2, l3], selected_level_id=None)

        assert result[l1.id] == ContentStatus.UNLOCKED
        assert result[l2.id] == ContentStatus.UNLOCKED
        assert result[l3.id] == ContentStatus.UNLOCKED

    def test_l02_selected_l1_only_l1_unlocked(self):
        """[L02] selected=L1 → L1 unlocked, L2 locked, L3 locked."""
        l1, l2, l3 = _three_levels()
        result = compute_status_map([l1, l2, l3], selected_level_id=l1.id)

        assert result[l1.id] == ContentStatus.UNLOCKED
        assert result[l2.id] == ContentStatus.LOCKED
        assert result[l3.id] == ContentStatus.LOCKED

    def test_l03_selected_l2_l1_and_l2_unlocked(self):
        """[L03] selected=L2 → L1 unlocked (always), L2 unlocked (selected), L3 locked."""
        l1, l2, l3 = _three_levels()
        result = compute_status_map([l1, l2, l3], selected_level_id=l2.id)

        assert result[l1.id] == ContentStatus.UNLOCKED
        assert result[l2.id] == ContentStatus.UNLOCKED
        assert result[l3.id] == ContentStatus.LOCKED

    def test_l04_selected_l3_l1_and_l3_unlocked(self):
        """[L04] selected=L3 → L1 unlocked (always), L2 locked, L3 unlocked (selected)."""
        l1, l2, l3 = _three_levels()
        result = compute_status_map([l1, l2, l3], selected_level_id=l3.id)

        assert result[l1.id] == ContentStatus.UNLOCKED
        assert result[l2.id] == ContentStatus.LOCKED
        assert result[l3.id] == ContentStatus.UNLOCKED

    def test_l05_no_completed_status_in_sprint3(self):
        """[L05] COMPLETED must never appear in any status map (Sprint 3)."""
        l1, l2, l3 = _three_levels()
        for selected in (None, l1.id, l2.id, l3.id):
            result = compute_status_map([l1, l2, l3], selected_level_id=selected)
            for status in result.values():
                assert status != ContentStatus.COMPLETED, (
                    f"COMPLETED must not appear in Sprint 3, got {status} "
                    f"for selected={selected}"
                )

    def test_v01_empty_list_returns_empty_map(self):
        """[V01] Empty input → empty dict, no crash."""
        result = compute_status_map([], selected_level_id=None)
        assert result == {}

    def test_v02_selected_not_in_list_only_l1_unlocked(self):
        """[V02] selected_level_id not present → only order_index==1 unlocked."""
        l1, l2, l3 = _three_levels()
        unknown_id = uuid.uuid4()
        result = compute_status_map([l1, l2, l3], selected_level_id=unknown_id)

        assert result[l1.id] == ContentStatus.UNLOCKED
        assert result[l2.id] == ContentStatus.LOCKED
        assert result[l3.id] == ContentStatus.LOCKED

    def test_v03_no_order_index_1_selected_none_all_unlocked(self):
        """[V03] No level has order_index==1; selected=None → all unlocked."""
        l2 = FakeLevel(order_index=2)
        l3 = FakeLevel(order_index=3)
        result = compute_status_map([l2, l3], selected_level_id=None)
        assert result[l2.id] == ContentStatus.UNLOCKED
        assert result[l3.id] == ContentStatus.UNLOCKED

    def test_v04_no_order_index_1_selected_present_only_selected_unlocked(self):
        """[V04] No order_index==1; selected=l3 → only l3 unlocked."""
        l2 = FakeLevel(order_index=2)
        l3 = FakeLevel(order_index=3)
        result = compute_status_map([l2, l3], selected_level_id=l3.id)
        assert result[l2.id] == ContentStatus.LOCKED
        assert result[l3.id] == ContentStatus.UNLOCKED


# ---------------------------------------------------------------------------
# is_level_unlocked tests
# ---------------------------------------------------------------------------

class TestIsLevelUnlocked:

    def test_i01_selected_none_all_unlocked(self):
        """[I01] selected=None → every level reports as unlocked."""
        l1, l2, l3 = _three_levels()
        for lvl in (l1, l2, l3):
            assert is_level_unlocked(lvl.id, [l1, l2, l3], selected_level_id=None)

    def test_i02_selected_l2_l1_and_l2_unlocked_l3_locked(self):
        """[I02] selected=L2 → L1 unlocked, L2 unlocked, L3 locked."""
        l1, l2, l3 = _three_levels()
        assert is_level_unlocked(l1.id, [l1, l2, l3], selected_level_id=l2.id) is True
        assert is_level_unlocked(l2.id, [l1, l2, l3], selected_level_id=l2.id) is True
        assert is_level_unlocked(l3.id, [l1, l2, l3], selected_level_id=l2.id) is False

    def test_i03_unknown_level_id_returns_false(self):
        """[I03] Level id not in list → defaults to LOCKED → unlocked=False."""
        l1, l2, l3 = _three_levels()
        phantom_id = uuid.uuid4()
        assert is_level_unlocked(phantom_id, [l1, l2, l3], selected_level_id=None) is False

    def test_i04_selected_l1_only_l1_unlocked(self):
        """[I04] Mirrors [L02] via is_level_unlocked predicate."""
        l1, l2, l3 = _three_levels()
        assert is_level_unlocked(l1.id, [l1, l2, l3], selected_level_id=l1.id) is True
        assert is_level_unlocked(l2.id, [l1, l2, l3], selected_level_id=l1.id) is False
        assert is_level_unlocked(l3.id, [l1, l2, l3], selected_level_id=l1.id) is False


# ---------------------------------------------------------------------------
# list_levels — DTO tests (FakeLevelRepository injected via repo parameter)
# ---------------------------------------------------------------------------

class TestListLevels:
    """Tests for list_levels() injecting FakeLevelRepository via the repo parameter.

    No unittest.mock.patch is used here; the fake repo is passed directly.
    """

    def _run(self, levels_to_return, selected_id=None):
        """Helper: build FakeLevelRepository and call list_levels with it."""
        session = FakeSession()
        fake_repo = FakeLevelRepository(levels=levels_to_return)
        return list_levels(session, selected_id, repo=fake_repo)

    def test_d01_sort_by_order_index_when_repo_returns_reversed(self):
        """[D01] list_levels sorts ascending by order_index regardless of repo order."""
        l1, l2, l3 = _three_levels()
        # Repo returns them in reverse order
        result = self._run([l3, l2, l1], selected_id=None)

        assert len(result) == 3
        assert result[0].order_index == 1
        assert result[1].order_index == 2
        assert result[2].order_index == 3

    def test_d02_placeholder_values_stars_and_topics(self):
        """[D02] Sprint 3 placeholders: stars=0, maxStars=45, topicsCompleted=0, topicsTotal=3."""
        l1, l2, l3 = _three_levels()
        result = self._run([l1, l2, l3], selected_id=None)

        for dto in result:
            assert dto.stars == 0, f"stars must be 0 in Sprint 3, got {dto.stars}"
            assert dto.max_stars == MAX_STARS_PER_LEVEL, (
                f"maxStars must be {MAX_STARS_PER_LEVEL}, got {dto.max_stars}"
            )
            assert dto.topics_completed == 0
            assert dto.topics_total == TOPICS_PER_LEVEL

    def test_d03_level_dto_fields_match_contract_6_3(self):
        """[D03] LevelDto serialises to camelCase keys matching contract 6.3."""
        l1, l2, l3 = _three_levels()
        result = self._run([l1, l2, l3], selected_id=None)

        expected_camel_keys = {
            "id", "name", "description", "orderIndex",
            "status", "stars", "maxStars", "topicsCompleted", "topicsTotal",
        }
        for dto in result:
            dto_dict = dto.model_dump(by_alias=True)
            assert set(dto_dict.keys()) == expected_camel_keys, (
                f"LevelDto keys mismatch: {set(dto_dict.keys())} != {expected_camel_keys}"
            )

    def test_d04_description_none_present_not_omitted(self):
        """[D04] description=None must appear as a key (not omitted)."""
        lvl = FakeLevel(order_index=1, description=None)
        result = self._run([lvl], selected_id=None)

        assert len(result) == 1
        dto_dict = result[0].model_dump(by_alias=True)
        assert "description" in dto_dict, "description key must be present even when None"
        assert dto_dict["description"] is None

    def test_v01_empty_list_returns_empty(self):
        """[V01] No levels in DB → list_levels returns []."""
        result = self._run([], selected_id=None)
        assert result == []

    def test_v02_selected_not_in_list_does_not_crash(self):
        """[V02] selected_level_id not in list → only order_index==1 unlocked, no crash."""
        l1, l2, l3 = _three_levels()
        unknown = uuid.uuid4()
        result = self._run([l1, l2, l3], selected_id=unknown)

        assert len(result) == 3
        statuses = {dto.order_index: dto.status for dto in result}
        assert statuses[1] == ContentStatus.UNLOCKED
        assert statuses[2] == ContentStatus.LOCKED
        assert statuses[3] == ContentStatus.LOCKED

    def test_status_all_unlocked_when_selected_none(self):
        """list_levels with selected=None → all status UNLOCKED."""
        l1, l2, l3 = _three_levels()
        result = self._run([l1, l2, l3], selected_id=None)

        for dto in result:
            assert dto.status == ContentStatus.UNLOCKED

    def test_status_correct_for_selected_l2(self):
        """list_levels with selected=L2 → L1 unlocked, L2 unlocked, L3 locked."""
        l1, l2, l3 = _three_levels()
        result = self._run([l1, l2, l3], selected_id=l2.id)

        statuses = {dto.order_index: dto.status for dto in result}
        assert statuses[1] == ContentStatus.UNLOCKED
        assert statuses[2] == ContentStatus.UNLOCKED
        assert statuses[3] == ContentStatus.LOCKED

    def test_result_contains_level_dto_instances(self):
        """Each returned element must be a LevelDto instance."""
        l1, l2, l3 = _three_levels()
        result = self._run([l1, l2, l3], selected_id=None)
        for dto in result:
            assert isinstance(dto, LevelDto)

    def test_default_repo_created_from_session_when_repo_not_passed(self):
        """When repo=None (default), list_levels creates LevelRepository(session) internally.

        Uses patch exactly once to intercept the internal constructor call and
        verify the session is forwarded correctly.
        """
        l1, l2, l3 = _three_levels()
        session = FakeSession()

        with patch(
            "app.services.level_service.LevelRepository",
        ) as MockRepo:
            mock_instance = MockRepo.return_value
            mock_instance.get_all.return_value = [l1, l2, l3]

            # Call WITHOUT supplying repo — must trigger internal construction.
            result = list_levels(session, selected_level_id=None)

        # The constructor must have been called with our session.
        MockRepo.assert_called_once_with(session)
        assert len(result) == 3
