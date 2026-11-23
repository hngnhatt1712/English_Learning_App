"""User-owned vocabulary batches and PostgreSQL insert-before-lock for C1."""
from collections.abc import Sequence
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session

from app.models.progress import UserVocabularyProgress
from app.models.vocabulary import Vocabulary


class VocabularyRepository:
    def __init__(self, db: Session) -> None:
        self.db = db

    def get_by_ids(self, ids: Sequence[UUID]) -> dict[UUID, Vocabulary]:
        if not ids:
            return {}
        return {row.id: row for row in self.db.scalars(select(Vocabulary).where(Vocabulary.id.in_(ids)))}

    def progress_by_ids(self, user_id: UUID, ids: Sequence[UUID]) -> dict[UUID, UserVocabularyProgress]:
        if not ids:
            return {}
        return {row.vocabulary_id: row for row in self.db.scalars(
            select(UserVocabularyProgress).where(
                UserVocabularyProgress.user_id == user_id, UserVocabularyProgress.vocabulary_id.in_(ids),
            )
        )}

    def get_or_create_locked(self, user_id: UUID, vocabulary_id: UUID) -> UserVocabularyProgress:
        # A missing row cannot be locked. INSERT ON CONFLICT waits for any competing
        # insertion; the following SELECT locks/refreshes the committed winner.
        self.db.execute(insert(UserVocabularyProgress).values(
            user_id=user_id, vocabulary_id=vocabulary_id,
        ).on_conflict_do_nothing(index_elements=["user_id", "vocabulary_id"]))
        return self.db.scalars(
            select(UserVocabularyProgress).where(
                UserVocabularyProgress.user_id == user_id,
                UserVocabularyProgress.vocabulary_id == vocabulary_id,
            ).with_for_update().execution_options(populate_existing=True)
        ).one()
