"""Shared fake infrastructure for service-layer tests (no DB required).

Contents:
- FakeSession  : counts commit/rollback calls; can be configured to raise
                 IntegrityError on commit (for future auth/profile tests).
- FakeLevelRepository : in-memory replacement for LevelRepository.

Design (D-18):
- Each test should construct fresh fakes to avoid shared state.
- Adding new helpers is allowed; changing existing behaviour must be
  reported in the sprint handover.
"""
from __future__ import annotations

import uuid
from collections.abc import Sequence
from typing import Optional

from sqlalchemy.exc import IntegrityError


# ---------------------------------------------------------------------------
# FakeSession
# ---------------------------------------------------------------------------

class FakeSession:
    """Minimal SQLAlchemy Session substitute for unit tests.

    Tracks ``commit`` and ``rollback`` call counts.  Optionally raises
    ``IntegrityError`` on the next ``commit`` call when
    ``force_integrity_error`` is set to True.

    Usage::

        session = FakeSession()
        # ... call service code ...
        assert session.commit_count == 1
        assert session.rollback_count == 0

    Force an IntegrityError::

        session = FakeSession(force_integrity_error=True)
        with pytest.raises(IntegrityError):
            session.commit()
    """

    def __init__(self, *, force_integrity_error: bool = False) -> None:
        self.commit_count: int = 0
        self.rollback_count: int = 0
        self._force_integrity_error: bool = force_integrity_error

    def commit(self) -> None:
        """Increment commit counter; raise IntegrityError if configured."""
        if self._force_integrity_error:
            raise IntegrityError(
                statement="INSERT INTO ...",
                params={},
                orig=Exception("duplicate key value"),
            )
        self.commit_count += 1

    def rollback(self) -> None:
        """Increment rollback counter."""
        self.rollback_count += 1

    def add(self, obj: object) -> None:
        """No-op: services may call session.add(); we silently accept it."""

    def flush(self) -> None:
        """No-op placeholder."""

    def close(self) -> None:
        """No-op placeholder."""

    # Context manager support (for `with session.begin(): ...`)
    def begin(self):  # noqa: ANN201
        return _FakeTransaction(self)

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        return False


class _FakeTransaction:
    """Minimal context manager returned by FakeSession.begin()."""

    def __init__(self, session: FakeSession) -> None:
        self._session = session

    def __enter__(self):
        return self._session

    def __exit__(self, exc_type, exc_val, exc_tb):
        if exc_type is None:
            self._session.commit()
        else:
            self._session.rollback()
        return False


# ---------------------------------------------------------------------------
# FakeLevelRepository
# ---------------------------------------------------------------------------

class FakeLevelRepository:
    """In-memory Level repository for service-layer tests.

    Wraps a plain list of fake Level-like objects.  Service code receives a
    ``FakeSession`` and we monkey-patch the repository instantiation via the
    factory pattern: ``list_levels`` accepts a session and internally creates
    a ``LevelRepository(session)`` — so to test without a DB we subclass or
    pass a fake session whose ``scalars`` method returns known data.

    However, because ``LevelRepository`` directly uses ``session.scalars()``,
    the cleanest approach is to provide a fake that quacks like a
    ``LevelRepository`` and pass it *directly* to the service helpers that
    accept ``Sequence[Level]`` (``compute_status_map``, ``is_level_unlocked``).

    For ``list_levels`` we create a subclass that overrides ``get_all`` so
    the test only needs to construct a ``FakeLevelRepository`` and pass a
    ``FakeSession``.

    Usage::

        repo = FakeLevelRepository(levels=[lvl1, lvl2, lvl3])
        dtos = list_levels_with_repo(repo, selected_level_id=some_uuid)
    """

    def __init__(self, levels: list | None = None) -> None:
        self._levels: list = levels or []

    def get_all(self) -> Sequence:
        """Return all fake levels (already in insertion order)."""
        return list(self._levels)

    def get_by_id(self, level_id: uuid.UUID) -> Optional[object]:
        """Return the first fake level whose id matches, or None."""
        for lvl in self._levels:
            if lvl.id == level_id:
                return lvl
        return None


# ---------------------------------------------------------------------------
# Fake Level ORM object
# ---------------------------------------------------------------------------

class FakeLevel:
    """Lightweight substitute for the SQLAlchemy Level model.

    Only exposes the attributes used by the service layer:
    ``id``, ``name``, ``description``, ``order_index``.
    """

    def __init__(
        self,
        order_index: int,
        name: str = "",
        description: Optional[str] = None,
        level_id: Optional[uuid.UUID] = None,
    ) -> None:
        self.id: uuid.UUID = level_id or uuid.uuid4()
        self.name: str = name or f"Level {order_index}"
        self.description: Optional[str] = description
        self.order_index: int = order_index

    def __repr__(self) -> str:  # pragma: no cover
        return f"<FakeLevel(id={self.id}, order_index={self.order_index})>"


# ---------------------------------------------------------------------------
# Fake User ORM object & FakeUserRepository
# ---------------------------------------------------------------------------

from datetime import date, datetime, timezone


class FakeUser:
    """Lightweight substitute for the SQLAlchemy User model."""

    def __init__(
        self,
        username: str,
        email: str,
        password_hash: str,
        selected_level_id: Optional[uuid.UUID] = None,
        user_id: Optional[uuid.UUID] = None,
        current_streak: int = 0,
        last_learning_date: Optional[date] = None,
        created_at: Optional[datetime] = None,
    ) -> None:
        self.id: uuid.UUID = user_id or uuid.uuid4()
        self.username: str = username
        self.email: str = email
        self.password_hash: str = password_hash
        self.selected_level_id: Optional[uuid.UUID] = selected_level_id
        self.current_streak: int = current_streak
        self.last_learning_date: Optional[date] = last_learning_date
        self.created_at: datetime = created_at or datetime.now(timezone.utc)

    def __repr__(self) -> str:  # pragma: no cover
        return f"<FakeUser(id={self.id}, username='{self.username}', email='{self.email}')>"


class FakeUserRepository:
    """In-memory User repository for service-layer tests.

    Stores username and email exactly as passed by caller (no auto-lowercasing)
    to reveal normalization bugs in the service layer.
    """

    def __init__(self, users: list[FakeUser] | None = None, session: FakeSession | None = None) -> None:
        self.users: list[FakeUser] = list(users or [])
        self._uncommitted: list[tuple[FakeUser, int]] = []
        self.session: FakeSession | None = session
        self.create_user_calls: list[dict] = []
        self.get_by_email_calls: list[str] = []
        self.get_by_username_calls: list[str] = []
        self.get_by_identifier_calls: list[str] = []
        self.get_by_id_calls: list[uuid.UUID] = []

    def _is_active(self, user: FakeUser) -> bool:
        if self.session is None:
            return True
        for u, rb_at_creation in self._uncommitted:
            if u is user:
                if self.session.rollback_count > rb_at_creation:
                    return False
        return True

    def create_user(
        self,
        username: str,
        email: str,
        password_hash: str,
        selected_level_id: Optional[uuid.UUID] = None,
    ) -> FakeUser:
        self.create_user_calls.append(
            {
                "username": username,
                "email": email,
                "password_hash": password_hash,
                "selected_level_id": selected_level_id,
            }
        )
        user = FakeUser(
            username=username,
            email=email,
            password_hash=password_hash,
            selected_level_id=selected_level_id,
        )
        self.users.append(user)
        cur_rb = getattr(self.session, "rollback_count", 0) if self.session else 0
        self._uncommitted.append((user, cur_rb))
        return user

    def get_by_email(self, email: str) -> Optional[FakeUser]:
        self.get_by_email_calls.append(email)
        norm = email.strip().lower()
        if not norm:
            return None
        for u in self.users:
            if self._is_active(u) and u.email.strip().lower() == norm:
                return u
        return None

    def get_by_username(self, username: str) -> Optional[FakeUser]:
        self.get_by_username_calls.append(username)
        norm = username.strip().lower()
        if not norm:
            return None
        for u in self.users:
            if self._is_active(u) and u.username.strip().lower() == norm:
                return u
        return None

    def get_by_identifier(self, identifier: str) -> Optional[FakeUser]:
        self.get_by_identifier_calls.append(identifier)
        norm = identifier.strip().lower()
        if not norm:
            return None
        if "@" in norm:
            for u in self.users:
                if self._is_active(u) and u.email.strip().lower() == norm:
                    return u
        else:
            for u in self.users:
                if self._is_active(u) and u.username.strip().lower() == norm:
                    return u
        return None

    def get_by_id(self, user_id: uuid.UUID) -> Optional[FakeUser]:
        self.get_by_id_calls.append(user_id)
        for u in self.users:
            if self._is_active(u) and u.id == user_id:
                return u
        return None

