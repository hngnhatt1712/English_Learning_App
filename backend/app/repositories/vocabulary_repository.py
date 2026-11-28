"""User-owned vocabulary batches and PostgreSQL insert-before-lock for C1."""
from collections.abc import Sequence
from datetime import datetime
from uuid import UUID

from app.models.progress import UserVocabularyProgress
from app.models.topic import Topic
from app.models.user import User
from app.models.vocabulary import Vocabulary
from app.schemas.flashcard import FlashcardLibrarySort
from app.schemas.vocabulary import VocabularySort, VocabularyStatus
from sqlalchemy import and_, func, or_, select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.orm import Session


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

    def list_flashcard_library(
        self, user_id: UUID, *, topic_id: UUID | None, search: str,
        sort: FlashcardLibrarySort | None, offset: int, limit: int,
    ) -> tuple[list[tuple[Vocabulary, UserVocabularyProgress, str]], int]:
        progress = UserVocabularyProgress
        cards = (
            select(Vocabulary, progress, Topic.name)
            .join(progress, progress.vocabulary_id == Vocabulary.id)
            .join(Topic, Topic.id == Vocabulary.topic_id)
            .where(progress.user_id == user_id, progress.is_in_flashcard.is_(True))
        )
        if topic_id is not None:
            cards = cards.where(Vocabulary.topic_id == topic_id)
        if search:
            cards = cards.where(or_(
                Vocabulary.word.icontains(search, autoescape=True),
                Vocabulary.meaning.icontains(search, autoescape=True),
            ))

        total = self.db.scalar(
            select(func.count()).select_from(cards.with_only_columns(Vocabulary.id).subquery())
        ) or 0
        if offset >= total:
            return [], total

        added_at = progress.added_to_flashcard_at
        order_by = {
            None: (Topic.order_index.asc(), Topic.id.asc(), added_at.desc().nulls_last()),
            "-addedAt": (added_at.desc().nulls_last(),),
            "addedAt": (added_at.asc().nulls_last(),),
            "word": (func.lower(Vocabulary.word).asc(),),
        }
        rows = self.db.execute(
            cards.order_by(*order_by[sort], Vocabulary.id.asc()).offset(offset).limit(limit)
        )
        return [(word, state, topic_name) for word, state, topic_name in rows], total

    def list_accessible_vocabulary(
        self, user_id: UUID, *, topic_ids: Sequence[UUID], level_id: UUID | None,
        status: VocabularyStatus | None, search: str, sort: VocabularySort, offset: int, limit: int,
    ) -> tuple[list[tuple[Vocabulary, UserVocabularyProgress | None]], int]:
        if not topic_ids:
            return [], 0
        progress = UserVocabularyProgress
        words = select(Vocabulary, progress).outerjoin(
            progress, and_(progress.vocabulary_id == Vocabulary.id, progress.user_id == user_id),
        ).where(Vocabulary.topic_id.in_(topic_ids))
        if level_id is not None:
            words = words.where(Vocabulary.level_id == level_id)
        if status is not None:
            words = words.where(func.coalesce(progress.status, "new") == status)
        if search:
            words = words.where(or_(
                Vocabulary.word.icontains(search, autoescape=True),
                Vocabulary.meaning.icontains(search, autoescape=True),
            ))
        total = self.db.scalar(
            select(func.count()).select_from(words.with_only_columns(Vocabulary.id).subquery())
        ) or 0
        if offset >= total:
            return [], total
        order = func.lower(Vocabulary.word)
        rows = self.db.execute(
            words.order_by(order.desc() if sort == "-word" else order.asc(), Vocabulary.id.asc())
            .offset(offset).limit(limit)
        )
        return [(word, state) for word, state in rows], total

    def list_due_flashcards(
        self, user_id: UUID, cutoff: datetime, *, offset: int, limit: int,
    ) -> tuple[list[tuple[Vocabulary, UserVocabularyProgress]], int]:
        progress = UserVocabularyProgress
        due = (
            progress.user_id == user_id,
            progress.is_in_flashcard.is_(True),
            progress.next_review <= cutoff,
        )
        cards = select(Vocabulary, progress).join(progress, progress.vocabulary_id == Vocabulary.id).where(*due)
        total = self.db.scalar(
            select(func.count()).select_from(cards.with_only_columns(Vocabulary.id).subquery())
        ) or 0
        if offset >= total:
            return [], total

        rows = self.db.execute(
            cards.order_by(progress.next_review, Vocabulary.id).offset(offset).limit(limit)
        )
        return [(word, state) for word, state in rows], total

    def lock_flashcard_for_review(self, user: User, vocabulary_id: UUID) -> UserVocabularyProgress | None:
        # Serialize streak writes with Round submissions before locking the card for rating/Undo.
        self.db.refresh(user, with_for_update=True)
        return self.db.scalar(
            select(UserVocabularyProgress).where(
                UserVocabularyProgress.user_id == user.id,
                UserVocabularyProgress.vocabulary_id == vocabulary_id,
                UserVocabularyProgress.is_in_flashcard.is_(True),
            ).with_for_update().execution_options(populate_existing=True)
        )

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
