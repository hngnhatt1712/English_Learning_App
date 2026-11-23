package com.nhom21.english_learning_app.domain.model

/**
 * Domain entity representing an English learning topic.
 *
 * @property id Unique identifier (UUID).
 * @property levelId Associated level ID (UUID) or null.
 * @property name Topic title.
 * @property description Optional topic description.
 * @property themeKey Visual theme key (e.g. "food", "weather", or "default").
 * @property status Current access/progress status.
 * @property stars Stars earned on this topic.
 * @property maxStars Maximum achievable stars.
 * @property roundsTotal Total rounds in this topic.
 * @property roundsCompleted Number of completed rounds.
 * @property progressPercent Progress percentage (0.0 to 100.0).
 * @property orderIndex 1-based display ordering.
 */
data class Topic(
    val id: String,
    val levelId: String?,
    val name: String,
    val description: String?,
    val themeKey: String,
    val status: ContentStatus,
    val stars: Int,
    val maxStars: Int,
    val roundsTotal: Int,
    val roundsCompleted: Int,
    val progressPercent: Double,
    val orderIndex: Int?
)

/**
 * Domain entity summarizing a lesson inside a topic.
 *
 * @property id Unique identifier (UUID).
 * @property topicId Parent topic identifier (UUID) or null.
 * @property title Lesson title.
 * @property description Optional lesson description.
 * @property orderIndex 1-based display ordering.
 */
data class LessonSummary(
    val id: String,
    val topicId: String?,
    val title: String,
    val description: String?,
    val orderIndex: Int?
)

/**
 * Domain entity summarizing an activity / round within a lesson.
 *
 * @property id Unique identifier (UUID).
 * @property lessonId Parent lesson identifier (UUID) or null.
 * @property roundNumber Sequential round number (1 to 5).
 * @property type Educational activity type.
 * @property title Round title or null.
 * @property status Access status for this round.
 * @property stars Best stars earned on this round (0 to 3).
 * @property bestScorePercent Highest score percentage or null if unattempted.
 * @property attempts Total number of submission attempts.
 * @property totalQuestions Number of questions in this round.
 */
data class RoundSummary(
    val id: String,
    val lessonId: String?,
    val roundNumber: Int,
    val type: RoundType,
    val title: String?,
    val status: ContentStatus,
    val stars: Int,
    val bestScorePercent: Double?,
    val attempts: Int,
    val totalQuestions: Int
)

/**
 * Composite domain entity containing a [Topic] and its associated [lessons].
 */
data class TopicDetail(
    val topic: Topic,
    val lessons: List<LessonSummary>
)

/**
 * Composite domain entity containing a [LessonSummary] and its constituent [rounds].
 */
data class LessonDetail(
    val lesson: LessonSummary,
    val rounds: List<RoundSummary>
)

