package com.nhom21.english_learning_app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Data transfer object representing a Topic per api-contract section 6.4.
 *
 * All nullable fields default to null and list fields default to emptyList()
 * to satisfy [explicitNulls = true] in kotlinx.serialization.
 */
@Serializable
data class TopicDto(
    val id: String,
    val levelId: String? = null,
    val name: String,
    val description: String? = null,
    val orderIndex: Int? = null,
    val status: String = "locked",
    val stars: Int = 0,
    val maxStars: Int = 15,
    val roundsCompleted: Int = 0,
    val roundsTotal: Int = 5,
    val progressPercent: Double = 0.0,
    val themeKey: String = "default"
)

/**
 * Data transfer object representing a Lesson summary per api-contract section 6.5.
 */
@Serializable
data class LessonSummaryDto(
    val id: String,
    val topicId: String? = null,
    val title: String,
    val description: String? = null,
    val orderIndex: Int? = null
)

/**
 * Data transfer object representing a Round within a lesson per api-contract section 6.6.
 */
@Serializable
data class RoundDto(
    val id: String,
    val lessonId: String? = null,
    val roundNumber: Int,
    val type: String,
    val title: String? = null,
    val status: String = "locked",
    val stars: Int = 0,
    val bestScorePercent: Double? = null,
    val attempts: Int = 0,
    val totalQuestions: Int = 0
)

/**
 * Data transfer object for topic detail response per api-contract section 6.11 & 7.4.
 * Combines topic properties with associated lessons (MVP has exactly 1 lesson).
 */
@Serializable
data class TopicDetailDto(
    val id: String,
    val levelId: String? = null,
    val name: String,
    val description: String? = null,
    val orderIndex: Int? = null,
    val status: String = "locked",
    val stars: Int = 0,
    val maxStars: Int = 15,
    val roundsCompleted: Int = 0,
    val roundsTotal: Int = 5,
    val progressPercent: Double = 0.0,
    val themeKey: String = "default",
    val lessons: List<LessonSummaryDto> = emptyList()
)

/**
 * Data transfer object for lesson detail response per api-contract section 6.11 & 7.4.
 * Combines lesson properties with associated rounds (5 rounds).
 */
@Serializable
data class LessonDetailDto(
    val id: String,
    val topicId: String? = null,
    val title: String,
    val description: String? = null,
    val orderIndex: Int? = null,
    val rounds: List<RoundDto> = emptyList()
)

