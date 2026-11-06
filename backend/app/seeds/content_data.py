"""Seed content for topics and lessons (contract D3, D11, D16, C4).

Note on unique order constraints: ``topics`` has ``UNIQUE(level_id, order_index)`` and
``lessons`` has ``UNIQUE(topic_id, order_index)`` as non-deferrable constraints.
Swapping ``order_index`` between two existing rows via single-pass upsert will trigger
a uniqueness violation; reordering existing seeded items requires a two-step update or migration.
"""
from __future__ import annotations

import re
import uuid
from collections.abc import Mapping
from dataclasses import dataclass
from types import MappingProxyType

from app.database.seeds.ids import LEVEL_1_ID, LEVEL_2_ID, LEVEL_3_ID

THEME_KEY_RE = re.compile(r"^[a-z][a-z0-9_]{0,49}$")
EXPECTED_LEVEL_ORDERS: tuple[int, ...] = (1, 2, 3)
TOPICS_PER_LEVEL: int = 3
LESSONS_PER_TOPIC: int = 1
MAX_NAME_LENGTH: int = 100


class SeedDataError(ValueError):
    """Raised when the static seed data violates its own invariants."""


@dataclass(frozen=True)
class TopicSeed:
    """Immutable specification of a seeded Topic row."""

    key: str
    id: uuid.UUID
    level_order: int
    name: str
    description: str
    order_index: int
    theme_key: str


@dataclass(frozen=True)
class LessonSeed:
    """Immutable specification of a seeded Lesson row."""

    key: str
    id: uuid.UUID
    topic_key: str
    title: str
    description: str
    order_index: int


@dataclass(frozen=True)
class SeedDataset:
    """Complete immutable dataset used by the content seed planner and runner."""

    level_id_by_order: Mapping[int, uuid.UUID]
    topics: tuple[TopicSeed, ...]
    lessons: tuple[LessonSeed, ...]


LEVEL_ID_BY_ORDER: Mapping[int, uuid.UUID] = MappingProxyType(
    {
        1: LEVEL_1_ID,
        2: LEVEL_2_ID,
        3: LEVEL_3_ID,
    }
)

TOPICS: tuple[TopicSeed, ...] = (
    TopicSeed(
        key="l1_self_family",
        id=uuid.UUID("b0000000-0000-4000-8000-000000000011"),
        level_order=1,
        name="Self & Family",
        description="Giới thiệu bản thân & Gia đình: chào hỏi, nói về tên, tuổi, quê quán và các thành viên trong gia đình.",
        order_index=1,
        theme_key="family",
    ),
    TopicSeed(
        key="l1_daily_hobbies",
        id=uuid.UUID("b0000000-0000-4000-8000-000000000012"),
        level_order=1,
        name="Daily Routines & Hobbies",
        description="Hoạt động hàng ngày & Sở thích: kể về một ngày của bạn, giờ giấc và những điều bạn thích làm.",
        order_index=2,
        theme_key="daily_life",
    ),
    TopicSeed(
        key="l1_food_shopping",
        id=uuid.UUID("b0000000-0000-4000-8000-000000000013"),
        level_order=1,
        name="Food & Shopping",
        description="Đồ ăn thức uống & Mua sắm cơ bản: gọi món, hỏi giá và mua sắm hằng ngày.",
        order_index=3,
        theme_key="food",
    ),
    TopicSeed(
        key="l2_travel_directions",
        id=uuid.UUID("b0000000-0000-4000-8000-000000000021"),
        level_order=2,
        name="Travel & Directions",
        description="Du lịch & Hỏi đường: đặt vé, hỏi đường và di chuyển khi đi du lịch.",
        order_index=1,
        theme_key="travel",
    ),
    TopicSeed(
        key="l2_work_study_past",
        id=uuid.UUID("b0000000-0000-4000-8000-000000000022"),
        level_order=2,
        name="Work, Study & Past Events",
        description="Công việc, Học tập & Kỷ niệm: nói về công việc, việc học và kể lại những chuyện đã xảy ra.",
        order_index=2,
        theme_key="work_study",
    ),
    TopicSeed(
        key="l2_health_lifestyle",
        id=uuid.UUID("b0000000-0000-4000-8000-000000000023"),
        level_order=2,
        name="Health & Lifestyle",
        description="Sức khỏe & Lối sống: mô tả triệu chứng, thói quen lành mạnh và lời khuyên.",
        order_index=3,
        theme_key="health",
    ),
    TopicSeed(
        key="l3_entertainment_culture",
        id=uuid.UUID("b0000000-0000-4000-8000-000000000031"),
        level_order=3,
        name="Entertainment & Culture",
        description="Giải trí, Văn hóa & Truyền thông: phim ảnh, âm nhạc, tin tức và sự khác biệt văn hóa.",
        order_index=1,
        theme_key="entertainment",
    ),
    TopicSeed(
        key="l3_technology_career",
        id=uuid.UUID("b0000000-0000-4000-8000-000000000032"),
        level_order=3,
        name="Technology, Career & Future",
        description="Công nghệ, Sự nghiệp & Tương lai: công nghệ trong đời sống, định hướng nghề nghiệp và kế hoạch tương lai.",
        order_index=2,
        theme_key="technology",
    ),
    TopicSeed(
        key="l3_environment_society",
        id=uuid.UUID("b0000000-0000-4000-8000-000000000033"),
        level_order=3,
        name="Environment & Society",
        description="Môi trường & Vấn đề xã hội: bảo vệ môi trường, các vấn đề xã hội và cách bày tỏ ý kiến.",
        order_index=3,
        theme_key="environment",
    ),
)

LESSONS: tuple[LessonSeed, ...] = (
    LessonSeed(
        key="l1_self_family",
        id=uuid.UUID("c0000000-0000-4000-8000-000000000011"),
        topic_key="l1_self_family",
        title="Introducing Yourself & Your Family",
        description="Học cách giới thiệu bản thân và nói về gia đình.",
        order_index=1,
    ),
    LessonSeed(
        key="l1_daily_hobbies",
        id=uuid.UUID("c0000000-0000-4000-8000-000000000012"),
        topic_key="l1_daily_hobbies",
        title="My Day & My Hobbies",
        description="Học cách kể về một ngày và sở thích của bạn.",
        order_index=1,
    ),
    LessonSeed(
        key="l1_food_shopping",
        id=uuid.UUID("c0000000-0000-4000-8000-000000000013"),
        topic_key="l1_food_shopping",
        title="Ordering Food & Shopping",
        description="Học cách gọi món và mua sắm cơ bản.",
        order_index=1,
    ),
    LessonSeed(
        key="l2_travel_directions",
        id=uuid.UUID("c0000000-0000-4000-8000-000000000021"),
        topic_key="l2_travel_directions",
        title="Getting Around When You Travel",
        description="Học cách hỏi đường và di chuyển khi đi du lịch.",
        order_index=1,
    ),
    LessonSeed(
        key="l2_work_study_past",
        id=uuid.UUID("c0000000-0000-4000-8000-000000000022"),
        topic_key="l2_work_study_past",
        title="Talking About Work, Study & the Past",
        description="Học cách nói về công việc, việc học và những chuyện đã qua.",
        order_index=1,
    ),
    LessonSeed(
        key="l2_health_lifestyle",
        id=uuid.UUID("c0000000-0000-4000-8000-000000000023"),
        topic_key="l2_health_lifestyle",
        title="Staying Healthy",
        description="Học cách nói về sức khỏe và lối sống lành mạnh.",
        order_index=1,
    ),
    LessonSeed(
        key="l3_entertainment_culture",
        id=uuid.UUID("c0000000-0000-4000-8000-000000000031"),
        topic_key="l3_entertainment_culture",
        title="Talking About Entertainment & Culture",
        description="Học cách thảo luận về giải trí, văn hóa và truyền thông.",
        order_index=1,
    ),
    LessonSeed(
        key="l3_technology_career",
        id=uuid.UUID("c0000000-0000-4000-8000-000000000032"),
        topic_key="l3_technology_career",
        title="Technology, Career & Future Plans",
        description="Học cách nói về công nghệ, nghề nghiệp và kế hoạch tương lai.",
        order_index=1,
    ),
    LessonSeed(
        key="l3_environment_society",
        id=uuid.UUID("c0000000-0000-4000-8000-000000000033"),
        topic_key="l3_environment_society",
        title="Environment & Society Issues",
        description="Học cách bày tỏ ý kiến về môi trường và các vấn đề xã hội.",
        order_index=1,
    ),
)

SEED_DATA = SeedDataset(
    level_id_by_order=LEVEL_ID_BY_ORDER,
    topics=TOPICS,
    lessons=LESSONS,
)


def validate_seed_data(data: SeedDataset = SEED_DATA) -> None:
    """Check counts, orders, unique ids, theme keys and references; raise SeedDataError on any violation."""
    if set(data.level_id_by_order.keys()) != set(EXPECTED_LEVEL_ORDERS):
        raise SeedDataError("level_id_by_order must define exactly level orders 1, 2, and 3")

    seen_ids: set[uuid.UUID] = set()
    for lvl_id in data.level_id_by_order.values():
        if not isinstance(lvl_id, uuid.UUID):
            raise SeedDataError("level id must be a UUID")
        if lvl_id in seen_ids:
            raise SeedDataError("duplicate UUID in level_id_by_order")
        seen_ids.add(lvl_id)

    expected_topic_count = len(EXPECTED_LEVEL_ORDERS) * TOPICS_PER_LEVEL
    if len(data.topics) != expected_topic_count:
        raise SeedDataError(f"expected {expected_topic_count} topics, got {len(data.topics)}")

    topic_keys: set[str] = set()
    theme_keys: set[str] = set()
    topics_by_level: dict[int, list[TopicSeed]] = {order: [] for order in EXPECTED_LEVEL_ORDERS}

    for topic in data.topics:
        if not isinstance(topic.id, uuid.UUID):
            raise SeedDataError("topic id must be a UUID")
        if topic.id in seen_ids:
            raise SeedDataError(f"duplicate UUID detected for topic {topic.key}")
        seen_ids.add(topic.id)

        if not topic.key or topic.key in topic_keys:
            raise SeedDataError(f"invalid or duplicate topic key: {topic.key!r}")
        topic_keys.add(topic.key)

        if topic.level_order not in topics_by_level:
            raise SeedDataError(f"invalid level_order {topic.level_order} on topic {topic.key}")
        topics_by_level[topic.level_order].append(topic)

        if not topic.name or not topic.name.strip() or len(topic.name) > MAX_NAME_LENGTH:
            raise SeedDataError(f"invalid topic name length on {topic.key}")
        if not topic.description or not topic.description.strip():
            raise SeedDataError(f"empty description on topic {topic.key}")
        if not THEME_KEY_RE.fullmatch(topic.theme_key):
            raise SeedDataError(f"invalid theme_key {topic.theme_key!r} on topic {topic.key}")
        if topic.theme_key in theme_keys:
            raise SeedDataError(f"duplicate theme_key {topic.theme_key!r}")
        theme_keys.add(topic.theme_key)

    for level_order, level_topics in topics_by_level.items():
        if len(level_topics) != TOPICS_PER_LEVEL:
            raise SeedDataError(f"level {level_order} must have {TOPICS_PER_LEVEL} topics")
        orders = [t.order_index for t in level_topics]
        if sorted(orders) != list(range(1, TOPICS_PER_LEVEL + 1)):
            raise SeedDataError(f"level {level_order} topic order_index must be 1..{TOPICS_PER_LEVEL}")
        names = {t.name for t in level_topics}
        if len(names) != len(level_topics):
            raise SeedDataError(f"duplicate topic name in level {level_order}")

    expected_lesson_count = expected_topic_count * LESSONS_PER_TOPIC
    if len(data.lessons) != expected_lesson_count:
        raise SeedDataError(f"expected {expected_lesson_count} lessons, got {len(data.lessons)}")

    lesson_keys: set[str] = set()
    lesson_count_by_topic: dict[str, int] = {k: 0 for k in topic_keys}

    for lesson in data.lessons:
        if not isinstance(lesson.id, uuid.UUID):
            raise SeedDataError("lesson id must be a UUID")
        if lesson.id in seen_ids:
            raise SeedDataError(f"duplicate UUID detected for lesson {lesson.key}")
        seen_ids.add(lesson.id)

        if not lesson.key or lesson.key in lesson_keys:
            raise SeedDataError(f"invalid or duplicate lesson key: {lesson.key!r}")
        lesson_keys.add(lesson.key)

        if lesson.topic_key not in lesson_count_by_topic:
            raise SeedDataError(f"lesson {lesson.key} references unknown topic_key {lesson.topic_key!r}")
        lesson_count_by_topic[lesson.topic_key] += 1

        if lesson.order_index != 1:
            raise SeedDataError(f"lesson {lesson.key} must have order_index == 1")
        if not lesson.title or not lesson.title.strip() or len(lesson.title) > MAX_NAME_LENGTH:
            raise SeedDataError(f"invalid lesson title length on {lesson.key}")
        if not lesson.description or not lesson.description.strip():
            raise SeedDataError(f"empty description on lesson {lesson.key}")

    for t_key, count in lesson_count_by_topic.items():
        if count != LESSONS_PER_TOPIC:
            raise SeedDataError(f"topic {t_key} must have {LESSONS_PER_TOPIC} lesson(s), got {count}")

