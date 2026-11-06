package com.nhom21.english_learning_app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Compact level summary embedded inside [ProfileDto] and HomeDto
 * per api-contract section 6.11 and 7.3.
 *
 * This is a subset of [LevelDto]; it only contains fields relevant
 * to the profile/home context (no status, topicsCompleted, etc.).
 *
 * @property id UUID of the level.
 * @property name Human-readable level name (e.g. "Beginner (A1)").
 * @property stars Total stars earned by the user in this level.
 * @property maxStars Maximum possible stars for this level.
 * @property progressPercent Percentage of max stars earned (0.0–100.0).
 */
@Serializable
data class LevelBriefDto(
    val id: String,
    val name: String,
    val stars: Int,
    val maxStars: Int,
    val progressPercent: Double,
)

