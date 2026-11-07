package com.nhom21.english_learning_app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Information regarding the user's current streak state.
 */
@Serializable
data class StreakDto(
    val current: Int = 0,
    val lastLearningDate: String? = null,
    val learnedToday: Boolean = false,
)

/**
 * Information regarding the user's current level.
 */
@Serializable
data class HomeLevelDto(
    val id: String = "",
    val name: String = "Level 1",
    val stars: Int = 0,
    val maxStars: Int = 135
)

/**
 * Information regarding the current topic being learned.
 */
@Serializable
data class CurrentTopicDto(
    val id: String = "",
    val name: String = "Food and drinks",
    val topicOrder: Int = 1,
    val lessonId: String? = null,
    val round: Int = 1,
    val roundsTotal: Int = 5,
    val progressPercent: Float = 0f
)

/**
 * Payload returned by GET /api/v1/home per contract section 7.9.
 */
@Serializable
data class HomeDataDto(
    val username: String = "User",
    val level: HomeLevelDto? = null,
    val streak: StreakDto? = null,
    val totalStars: Int = 0,
    val maxStars: Int = 135,
    val currentTopic: CurrentTopicDto? = null,
    val flashcardCount: Int = 0,
    val reviewCount: Int = 0
)
