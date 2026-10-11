"""Unit tests for level_service — contract sections 6.3, 7.3, 9.2, D11, D14, D22.

Pure in-memory unit tests using FakeLevel / FakeLevelRepository / FakeSession;
no database required.

Tested requirements:
1. selected=None → all 3 levels UNLOCKED.
2. selected=L3   → L1 and L3 UNLOCKED, L2 LOCKED.
3. Empty level list & selected_level_id not in list.
4. D22: Level with progress (in progress_level_ids) remains UNLOCKED even if
   it is no longer the selected level.
5. Level in completed_level_ids → status is COMPLETED, and following level
   becomes UNLOCKED via cascading progression rule (contract 9.2).
"""
from __future__ import annotations

import uuid
from typing import Optional

import pytest

from app.schemas.enums import ContentStatus
from app.services.level_service import (
    MAX_STARS_PER_LEVEL,
    TOPICS_PER_LEVEL,
    compute_status_map,
    is_level_unlocked,
    list_levels,
)
from fakes import FakeLevel, FakeLevelRepository, FakeSession


# ---------------------------------------------------------------------------
# Test Helpers
# ---------------------------------------------------------------------------

def _build_three_levels() -> tuple[FakeLevel, FakeLevel, FakeLevel]:
    """Return 3 FakeLevel instances with order_index 1, 2, 3."""
    l1 = FakeLevel(order_index=1, name="Beginner (A1)")
    l2 = FakeLevel(order_index=2, name="Elementary (A2)")
    l3 = FakeLevel(order_index=3, name="Intermediate (B1)")
    return l1, l2, l3


# ---------------------------------------------------------------------------
# 1. selected=None → all 3 unlocked
# ---------------------------------------------------------------------------

def test_selected_none_all_three_unlocked() -> None:
    """When selected_level_id is None, all levels receive UNLOCKED status (D14 / contract 7.4)."""
    l1, l2, l3 = _build_three_levels()
    levels = [l1, l2, l3]

    # 1. compute_status_map
    status_map = compute_status_map(levels, selected_level_id=None)
    assert status_map[l1.id] == ContentStatus.UNLOCKED
    assert status_map[l2.id] == ContentStatus.UNLOCKED
    assert status_map[l3.id] == ContentStatus.UNLOCKED

    # 2. is_level_unlocked
    for lvl in levels:
        assert is_level_unlocked(lvl.id, levels, selected_level_id=None) is True

    # 3. list_levels
    session = FakeSession()
    repo = FakeLevelRepository(levels=levels)
    dtos = list_levels(session, selected_level_id=None, repo=repo)
    assert len(dtos) == 3
    assert [dto.status for dto in dtos] == [
        ContentStatus.UNLOCKED,
        ContentStatus.UNLOCKED,
        ContentStatus.UNLOCKED,
    ]


# ---------------------------------------------------------------------------
# 2. chọn Level 3 → L1, L3 unlocked; L2 locked
# ---------------------------------------------------------------------------

def test_select_level_3_l1_l3_unlocked_l2_locked() -> None:
    """When Level 3 is selected, L1 (order_index=1) and L3 (selected) are UNLOCKED, L2 is LOCKED."""
    l1, l2, l3 = _build_three_levels()
    levels = [l1, l2, l3]

    # 1. compute_status_map
    status_map = compute_status_map(levels, selected_level_id=l3.id)
    assert status_map[l1.id] == ContentStatus.UNLOCKED
    assert status_map[l2.id] == ContentStatus.LOCKED
    assert status_map[l3.id] == ContentStatus.UNLOCKED

    # 2. is_level_unlocked
    assert is_level_unlocked(l1.id, levels, selected_level_id=l3.id) is True
    assert is_level_unlocked(l2.id, levels, selected_level_id=l3.id) is False
    assert is_level_unlocked(l3.id, levels, selected_level_id=l3.id) is True

    # 3. list_levels
    session = FakeSession()
    repo = FakeLevelRepository(levels=levels)
    dtos = list_levels(session, selected_level_id=l3.id, repo=repo)
    assert len(dtos) == 3
    assert dtos[0].id == l1.id and dtos[0].status == ContentStatus.UNLOCKED
    assert dtos[1].id == l2.id and dtos[1].status == ContentStatus.LOCKED
    assert dtos[2].id == l3.id and dtos[2].status == ContentStatus.UNLOCKED


# ---------------------------------------------------------------------------
# 3. list rỗng; selected_level_id không có trong list
# ---------------------------------------------------------------------------

def test_empty_levels_list() -> None:
    """An empty level sequence returns an empty dict or empty DTO list without crashing."""
    # 1. compute_status_map
    status_map = compute_status_map([], selected_level_id=None)
    assert status_map == {}

    # 2. is_level_unlocked
    random_id = uuid.uuid4()
    assert is_level_unlocked(random_id, [], selected_level_id=None) is False

    # 3. list_levels
    session = FakeSession()
    repo = FakeLevelRepository(levels=[])
    dtos = list_levels(session, selected_level_id=None, repo=repo)
    assert dtos == []


def test_selected_level_id_not_in_list() -> None:
    """When selected_level_id is not present in levels, only order_index==1 is unlocked."""
    l1, l2, l3 = _build_three_levels()
    levels = [l1, l2, l3]
    unknown_id = uuid.uuid4()

    # 1. compute_status_map
    status_map = compute_status_map(levels, selected_level_id=unknown_id)
    assert status_map[l1.id] == ContentStatus.UNLOCKED
    assert status_map[l2.id] == ContentStatus.LOCKED
    assert status_map[l3.id] == ContentStatus.LOCKED

    # 2. is_level_unlocked
    assert is_level_unlocked(l1.id, levels, selected_level_id=unknown_id) is True
    assert is_level_unlocked(l2.id, levels, selected_level_id=unknown_id) is False
    assert is_level_unlocked(l3.id, levels, selected_level_id=unknown_id) is False
    assert is_level_unlocked(unknown_id, levels, selected_level_id=unknown_id) is False

    # 3. list_levels
    session = FakeSession()
    repo = FakeLevelRepository(levels=levels)
    dtos = list_levels(session, selected_level_id=unknown_id, repo=repo)
    assert len(dtos) == 3
    assert dtos[0].status == ContentStatus.UNLOCKED
    assert dtos[1].status == ContentStatus.LOCKED
    assert dtos[2].status == ContentStatus.LOCKED


# ---------------------------------------------------------------------------
# 4. D22: L3 không còn được chọn nhưng nằm trong progress_level_ids → vẫn unlocked
# ---------------------------------------------------------------------------

def test_d22_progress_level_ids_remains_unlocked() -> None:
    """D22: A level in progress_level_ids remains UNLOCKED even when not selected."""
    l1, l2, l3 = _build_three_levels()
    levels = [l1, l2, l3]

    # User currently has L1 selected, but previously made progress in L3
    progress_set = frozenset([l3.id])

    # 1. compute_status_map
    status_map = compute_status_map(
        levels,
        selected_level_id=l1.id,
        progress_level_ids=progress_set,
    )
    assert status_map[l1.id] == ContentStatus.UNLOCKED  # selected & order_index==1
    assert status_map[l2.id] == ContentStatus.LOCKED    # neither selected nor progress
    assert status_map[l3.id] == ContentStatus.UNLOCKED  # unlocked per D22 progress

    # 2. is_level_unlocked
    assert is_level_unlocked(
        l3.id,
        levels,
        selected_level_id=l1.id,
        progress_level_ids=progress_set,
    ) is True

    # 3. list_levels
    session = FakeSession()
    repo = FakeLevelRepository(levels=levels)
    dtos = list_levels(
        session,
        selected_level_id=l1.id,
        repo=repo,
        progress_level_ids=progress_set,
    )
    statuses = {dto.id: dto.status for dto in dtos}
    assert statuses[l1.id] == ContentStatus.UNLOCKED
    assert statuses[l2.id] == ContentStatus.LOCKED
    assert statuses[l3.id] == ContentStatus.UNLOCKED


# ---------------------------------------------------------------------------
# 5. L1 trong completed_level_ids → L1 completed, L2 unlocked
# ---------------------------------------------------------------------------

def test_completed_level_ids_cascades_to_next_level() -> None:
    """L1 in completed_level_ids becomes COMPLETED and unlocks L2 via order progression (contract 9.2)."""
    l1, l2, l3 = _build_three_levels()
    levels = [l1, l2, l3]

    completed_set = frozenset([l1.id])

    # 1. compute_status_map: user has no level selected or has L1 selected
    status_map = compute_status_map(
        levels,
        selected_level_id=l1.id,
        completed_level_ids=completed_set,
    )
    assert status_map[l1.id] == ContentStatus.COMPLETED
    assert status_map[l2.id] == ContentStatus.UNLOCKED  # previous level L1 is completed
    assert status_map[l3.id] == ContentStatus.LOCKED

    # 2. is_level_unlocked: both COMPLETED and UNLOCKED report True as accessible
    assert is_level_unlocked(
        l1.id,
        levels,
        selected_level_id=l1.id,
        completed_level_ids=completed_set,
    ) is True
    assert is_level_unlocked(
        l2.id,
        levels,
        selected_level_id=l1.id,
        completed_level_ids=completed_set,
    ) is True
    assert is_level_unlocked(
        l3.id,
        levels,
        selected_level_id=l1.id,
        completed_level_ids=completed_set,
    ) is False

    # 3. list_levels
    session = FakeSession()
    repo = FakeLevelRepository(levels=levels)
    dtos = list_levels(
        session,
        selected_level_id=l1.id,
        repo=repo,
        completed_level_ids=completed_set,
    )
    statuses = {dto.id: dto.status for dto in dtos}
    assert statuses[l1.id] == ContentStatus.COMPLETED
    assert statuses[l2.id] == ContentStatus.UNLOCKED
    assert statuses[l3.id] == ContentStatus.LOCKED


def test_multiple_completed_levels_cascade_to_l3() -> None:
    """When L1 and L2 are in completed_level_ids, both are COMPLETED and L3 is UNLOCKED."""
    l1, l2, l3 = _build_three_levels()
    levels = [l1, l2, l3]

    completed_set = frozenset([l1.id, l2.id])
    status_map = compute_status_map(
        levels,
        selected_level_id=l1.id,
        completed_level_ids=completed_set,
    )
    assert status_map[l1.id] == ContentStatus.COMPLETED
    assert status_map[l2.id] == ContentStatus.COMPLETED
    assert status_map[l3.id] == ContentStatus.UNLOCKED


def test_unsorted_levels_input_evaluated_in_order() -> None:
    """When levels sequence is provided out of order, sorting ensures correct progression."""
    l1, l2, l3 = _build_three_levels()
    # Provide reversed sequence
    reversed_levels = [l3, l2, l1]

    completed_set = frozenset([l1.id])
    status_map = compute_status_map(
        reversed_levels,
        selected_level_id=l1.id,
        completed_level_ids=completed_set,
    )
    assert status_map[l1.id] == ContentStatus.COMPLETED
    assert status_map[l2.id] == ContentStatus.UNLOCKED
    assert status_map[l3.id] == ContentStatus.LOCKED
