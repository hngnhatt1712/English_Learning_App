"""Unit tests for logout_service (contract 7.2).

Tests:
- test_logout_with_token_deletes_own_row_and_commits_once
- test_logout_scopes_delete_by_user_id_and_token
- test_logout_without_token_is_noop
- test_logout_is_idempotent
- test_logout_does_not_delete_other_users_token
- test_logout_rolls_back_and_reraises_when_delete_fails
- test_logout_rolls_back_and_reraises_when_commit_fails
- test_logout_does_not_log_token
"""
from __future__ import annotations

import logging
import uuid

import pytest
from app.services import logout_service


class FakeSession:
    """Double for SQLAlchemy Session tracking commit and rollback."""

    def __init__(self, error_on_commit: Exception | None = None) -> None:
        self.commit_count = 0
        self.rollback_count = 0
        self.error_on_commit = error_on_commit

    def commit(self) -> None:
        self.commit_count += 1
        if self.error_on_commit is not None:
            raise self.error_on_commit

    def rollback(self) -> None:
        self.rollback_count += 1


class FakeDeviceTokenRepository:
    """Double for DeviceTokenRepository tracking tokens and calls."""

    def __init__(
        self,
        tokens: list[tuple[uuid.UUID, str]] | None = None,
        error_on_delete: Exception | None = None,
    ) -> None:
        self.tokens: list[tuple[uuid.UUID, str]] = list(tokens or [])
        self.delete_calls: list[tuple[uuid.UUID, str]] = []
        self.error_on_delete = error_on_delete

    def delete_by_user_and_token(self, user_id: uuid.UUID, fcm_token: str) -> None:
        self.delete_calls.append((user_id, fcm_token))
        if self.error_on_delete is not None:
            raise self.error_on_delete
        self.tokens = [t for t in self.tokens if not (t[0] == user_id and t[1] == fcm_token)]


def test_logout_with_token_deletes_own_row_and_commits_once():
    """Logout with a valid token deletes the caller's token and commits the session once."""
    session = FakeSession()
    user_id = uuid.uuid4()
    fcm_token = "fcm_token_12345"
    repo = FakeDeviceTokenRepository(tokens=[(user_id, fcm_token)])

    logout_service.logout(session, user_id, fcm_token, repo=repo)

    assert (user_id, fcm_token) not in repo.tokens
    assert session.commit_count == 1
    assert session.rollback_count == 0


def test_logout_scopes_delete_by_user_id_and_token():
    """Repo delete_by_user_and_token is invoked with both user_id and fcm_token."""
    session = FakeSession()
    user_id = uuid.uuid4()
    fcm_token = "fcm_token_scope_check"
    repo = FakeDeviceTokenRepository(tokens=[(user_id, fcm_token)])

    logout_service.logout(session, user_id, fcm_token, repo=repo)

    assert len(repo.delete_calls) == 1
    called_user_id, called_token = repo.delete_calls[0]
    assert called_user_id == user_id
    assert called_token == fcm_token


def test_logout_without_token_is_noop():
    """When fcm_token is None, repo is never called and session is never committed."""
    session = FakeSession()
    user_id = uuid.uuid4()
    repo = FakeDeviceTokenRepository(tokens=[(user_id, "some_token")])

    logout_service.logout(session, user_id, None, repo=repo)

    assert len(repo.delete_calls) == 0
    assert session.commit_count == 0
    assert session.rollback_count == 0
    assert len(repo.tokens) == 1


def test_logout_is_idempotent():
    """Calling logout multiple times with the same token succeeds and leaves token deleted."""
    session = FakeSession()
    user_id = uuid.uuid4()
    fcm_token = "fcm_token_idempotent"
    repo = FakeDeviceTokenRepository(tokens=[(user_id, fcm_token)])

    # First call
    logout_service.logout(session, user_id, fcm_token, repo=repo)
    assert session.commit_count == 1
    assert (user_id, fcm_token) not in repo.tokens

    # Second call (token already missing in repo)
    logout_service.logout(session, user_id, fcm_token, repo=repo)
    assert session.commit_count == 2
    assert session.rollback_count == 0
    assert (user_id, fcm_token) not in repo.tokens


def test_logout_does_not_delete_other_users_token():
    """Token with identical string belonging to a different user is never deleted."""
    session = FakeSession()
    user1_id = uuid.uuid4()
    user2_id = uuid.uuid4()
    shared_or_different_token = "fcm_token_abc"
    repo = FakeDeviceTokenRepository(tokens=[(user2_id, shared_or_different_token)])

    logout_service.logout(session, user1_id, shared_or_different_token, repo=repo)

    assert (user2_id, shared_or_different_token) in repo.tokens
    assert session.commit_count == 1
    assert session.rollback_count == 0


def test_logout_rolls_back_and_reraises_when_delete_fails():
    """If repository raises an exception, session.rollback() is called and exception is reraised."""
    session = FakeSession()
    user_id = uuid.uuid4()
    fcm_token = "fcm_error_token"
    expected_error = RuntimeError("Database dead")
    repo = FakeDeviceTokenRepository(error_on_delete=expected_error)

    with pytest.raises(RuntimeError) as exc_info:
        logout_service.logout(session, user_id, fcm_token, repo=repo)

    assert exc_info.value is expected_error
    assert session.rollback_count == 1
    assert session.commit_count == 0


def test_logout_rolls_back_and_reraises_when_commit_fails():
    """If session.commit() raises an exception, session.rollback() is called and exception is reraised."""
    expected_error = RuntimeError("Commit failure")
    session = FakeSession(error_on_commit=expected_error)
    user_id = uuid.uuid4()
    fcm_token = "fcm_commit_error"
    repo = FakeDeviceTokenRepository(tokens=[(user_id, fcm_token)])

    with pytest.raises(RuntimeError) as exc_info:
        logout_service.logout(session, user_id, fcm_token, repo=repo)

    assert exc_info.value is expected_error
    assert session.rollback_count == 1
    assert session.commit_count == 1


def test_logout_does_not_log_token(caplog: pytest.LogCaptureFixture):
    """Execution of logout does not leak the FCM token in any debug/info logs."""
    session = FakeSession()
    user_id = uuid.uuid4()
    sensitive_token = "super_sensitive_fcm_token_xyz"
    repo = FakeDeviceTokenRepository(tokens=[(user_id, sensitive_token)])

    with caplog.at_level(logging.DEBUG):
        logout_service.logout(session, user_id, sensitive_token, repo=repo)

    assert sensitive_token not in caplog.text
