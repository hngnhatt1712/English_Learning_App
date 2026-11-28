"""Read vocabulary using the current user's Topic access and progress."""
from uuid import UUID

from app.core.errors import AppError, ErrorType
from app.models.user import User
from app.repositories.vocabulary_repository import VocabularyRepository
from app.schemas.enums import ContentStatus
from app.schemas.vocabulary import VocabularyDto, VocabularySort, VocabularyStatus
from app.services import topic_service
from app.services.level_progress_provider import get_user_level_progress
from app.services.round_content import vocabulary_dto


def list_vocabulary(
    repository: VocabularyRepository, user: User, *, topic_id: UUID | None, level_id: UUID | None,
    status: VocabularyStatus | None, search: str | None, sort: VocabularySort, page: int, limit: int,
) -> tuple[list[VocabularyDto], int]:
    db = repository.db
    snapshot = get_user_level_progress(db, user)
    if topic_id is not None:
        topic = topic_service.get_topic_state(db, user.id, topic_id, snapshot=snapshot)
        if topic.status == ContentStatus.LOCKED:
            raise AppError(403, ErrorType.TOPIC_LOCKED, "Topic is locked")
    if level_id is not None and level_id not in snapshot.statuses:
        raise AppError(404, ErrorType.NOT_FOUND, "Level not found")

    topic_ids = [topic_id] if topic_id is not None else topic_service.get_accessible_topic_ids(db, snapshot, level_id)
    rows, total = repository.list_accessible_vocabulary(
        user.id, topic_ids=topic_ids, level_id=level_id, status=status, search=(search or "").strip(),
        sort=sort, offset=(page - 1) * limit, limit=limit,
    )
    return [vocabulary_dto(word, progress) for word, progress in rows], total
